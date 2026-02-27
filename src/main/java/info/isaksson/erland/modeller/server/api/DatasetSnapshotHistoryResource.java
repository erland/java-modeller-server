package info.isaksson.erland.modeller.server.api;

import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.domain.ValidationPolicy;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetLeaseEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetSnapshotHistoryEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetSnapshotLatestEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetAuditEntity;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAclRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAuditRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetLeaseRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetSnapshotHistoryRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetSnapshotLatestRepository;
import info.isaksson.erland.modeller.server.security.DatasetAuthorizationService;
import info.isaksson.erland.modeller.server.security.PrincipalInfo;
import info.isaksson.erland.modeller.server.api.dto.LeaseConflictResponse;
import info.isaksson.erland.modeller.server.api.dto.SnapshotConflictResponse;
import info.isaksson.erland.modeller.server.api.dto.SnapshotResponse;
import info.isaksson.erland.modeller.server.api.dto.ValidationErrorDto;
import info.isaksson.erland.modeller.server.api.dto.RestoreSnapshotRequest;
import info.isaksson.erland.modeller.server.api.error.ApiError;
import info.isaksson.erland.modeller.server.validation.SnapshotValidationService;
import info.isaksson.erland.modeller.server.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.logging.MDC;
import jakarta.transaction.Transactional;

/**
 * Optional Phase 1-friendly snapshot history endpoint.
 * Lists prior snapshots (metadata only) in descending revision order.
 */
@Path("/datasets/{datasetId}/snapshots")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetSnapshotHistoryResource {

    @Inject DatasetRepository datasetRepository;
    @Inject DatasetAclRepository aclRepository;
    @Inject DatasetSnapshotHistoryRepository historyRepository;
    @Inject DatasetSnapshotLatestRepository latestRepository;
    @Inject DatasetAuditRepository auditRepository;
    @Inject DatasetLeaseRepository leaseRepository;
    @Inject SnapshotValidationService validationService;
    @Inject ObjectMapper objectMapper;
    @Inject DatasetAuthorizationService authz;

    @ConfigProperty(name = "modeller.snapshot.history.keep", defaultValue = "0")
    int snapshotHistoryKeep;

    @ConfigProperty(name = "modeller.snapshot.history.maxAgeDays", defaultValue = "0")
    int snapshotHistoryMaxAgeDays;

    @Context UriInfo uriInfo;

    public static class SnapshotHistoryItem {
        public long revision;
        public String etag;
        public OffsetDateTime savedAt;
        public String savedBy;
        public Integer schemaVersion;

        public SnapshotHistoryItem() {}

        public SnapshotHistoryItem(long revision, String etag, OffsetDateTime savedAt, String savedBy, Integer schemaVersion) {
            this.revision = revision;
            this.etag = etag;
            this.savedAt = savedAt;
            this.savedBy = savedBy;
            this.schemaVersion = schemaVersion;
        }
    }

    public static class SnapshotHistoryResponse {
        public UUID datasetId;
        public List<SnapshotHistoryItem> items;

        public SnapshotHistoryResponse() {}

        public SnapshotHistoryResponse(UUID datasetId, List<SnapshotHistoryItem> items) {
            this.datasetId = datasetId;
            this.items = items;
        }
    }

    @GET
    public SnapshotHistoryResponse list(@PathParam("datasetId") UUID datasetId,
                                       @QueryParam("limit") Integer limit,
                                       @QueryParam("offset") Integer offset) {
        PrincipalInfo principal = authz.currentPrincipal();

        Role role = aclRepository.findRole(datasetId, principal.subject())
                .orElseThrow(() -> new NotFoundException("Dataset not found"));
        if (!role.atLeast(Role.VIEWER)) {
            throw new jakarta.ws.rs.ForbiddenException("Insufficient role for dataset");
        }

        DatasetEntity ds = datasetRepository.findById(datasetId);
        if (ds == null || ds.deletedAt != null) {
            throw new NotFoundException("Dataset not found");
        }

        int l = (limit == null ? 50 : limit);
        int o = (offset == null ? 0 : offset);
        List<DatasetSnapshotHistoryEntity> rows = historyRepository.listForDataset(datasetId, l, o);

        List<SnapshotHistoryItem> items = rows.stream()
                .map(r -> new SnapshotHistoryItem(r.revision, r.etag, r.savedAt, r.savedBy, r.schemaVersion))
                .toList();

        return new SnapshotHistoryResponse(datasetId, items);
    }

    /**
     * Phase 2: Restore a prior snapshot revision by copying its payload into latest.
     * This is a write operation: it requires EDITOR+ and respects leases and If-Match optimistic concurrency.
     */
    @POST
    @Path("/{revision}/restore")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response restore(@PathParam("datasetId") UUID datasetId,
                            @PathParam("revision") long revision,
                            @HeaderParam("If-Match") String ifMatch,
                            @HeaderParam("X-Lease-Token") String leaseToken,
                            @QueryParam("force") @DefaultValue("false") boolean force,
                            RestoreSnapshotRequest request) {
        PrincipalInfo principal = authz.currentPrincipal();

        Role role = aclRepository.findRole(datasetId, principal.subject())
                .orElseThrow(() -> new NotFoundException("Dataset not found"));
        if (!role.atLeast(Role.EDITOR)) {
            throw new jakarta.ws.rs.ForbiddenException("Insufficient role for dataset");
        }

        DatasetEntity ds = datasetRepository.findById(datasetId);
        if (ds == null || ds.deletedAt != null) {
            throw new NotFoundException("Dataset not found");
        }

        if (ifMatch == null || ifMatch.isBlank()) {
            return Response.status(428)
                    .entity(Map.of(
                            "error", "precondition_required",
                            "message", "Missing If-Match header"
                    ))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        String expected = normalizeIfMatch(ifMatch);

        boolean requestContextForceOverride = false;
        String requestContextForcedLeaseHolder = null;

        // Lease enforcement (same rules as snapshot writes)
        OffsetDateTime nowLeaseCheck = OffsetDateTime.now();
        java.util.Optional<DatasetLeaseEntity> activeLeaseOpt = leaseRepository.findActive(datasetId, nowLeaseCheck);
        if (activeLeaseOpt.isPresent()) {
            DatasetLeaseEntity activeLease = activeLeaseOpt.get();
            if (!principal.subject().equals(activeLease.holderSub)) {
                if (force) {
                    if (!role.atLeast(Role.OWNER)) {
                        throw new jakarta.ws.rs.ForbiddenException("Insufficient role for dataset");
                    }
                    requestContextForceOverride = true;
                    requestContextForcedLeaseHolder = activeLease.holderSub;
                } else {
                    LeaseConflictResponse conflict = new LeaseConflictResponse(datasetId, activeLease.holderSub, activeLease.expiresAt);
                    return Response.status(Response.Status.CONFLICT)
                            .type(MediaType.APPLICATION_JSON)
                            .entity(conflict)
                            .build();
                }
            }
            if (principal.subject().equals(activeLease.holderSub) && (leaseToken == null || leaseToken.isBlank() || !leaseToken.trim().equals(activeLease.leaseToken))) {
                String path = uriInfo != null && uriInfo.getPath() != null ? "/" + uriInfo.getPath() : null;
                String requestId = (String) MDC.get("requestId");
                ApiError err = new ApiError(
                        OffsetDateTime.now(),
                        428,
                        "LEASE_TOKEN_REQUIRED",
                        "Missing or invalid X-Lease-Token for active lease",
                        path,
                        requestId
                );
                return Response.status(428)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(err)
                        .build();
            }
        }

        DatasetSnapshotLatestEntity latest = latestRepository.findById(datasetId);
        String currentEtag = latest == null ? "0" : (latest.etag != null ? latest.etag : String.valueOf(latest.revision));
        long currentRevision = latest == null ? 0L : latest.revision;

        if (!expected.equals(currentEtag)) {
            String updatedBy = auditRepository.findLatestActorForDatasetAndAction(datasetId, "SNAPSHOT_SAVE").orElse(null);
            OffsetDateTime updatedAt = latest == null ? null : latest.updatedAt;

            SnapshotConflictResponse conflict = new SnapshotConflictResponse(
                    datasetId,
                    currentRevision,
                    currentEtag,
                    updatedAt,
                    updatedBy
            );

            return Response.status(Response.Status.CONFLICT)
                    .type(MediaType.APPLICATION_JSON)
                    .tag(new EntityTag(currentEtag))
                    .entity(conflict)
                    .build();
        }

        DatasetSnapshotHistoryEntity source = historyRepository.findByDatasetAndRevision(datasetId, revision)
                .orElseThrow(() -> new NotFoundException("Snapshot revision not found"));

        JsonNode payload;
        try {
            payload = objectMapper.readTree(source.payloadJson);
        } catch (Exception e) {
            throw new jakarta.ws.rs.InternalServerErrorException("Stored snapshot payload is invalid JSON", e);
        }

        // Validate restored payload according to dataset policy (same as normal writes)
        ValidationPolicy policy = ds.validationPolicy == null ? ValidationPolicy.NONE : ds.validationPolicy;
        ValidationResult validation = validationService.validate(payload, source.payloadJson, policy);
        if (validation.hasErrors()) {
            String path = uriInfo != null && uriInfo.getPath() != null ? "/" + uriInfo.getPath() : null;
            String requestId = (String) MDC.get("requestId");

            ApiError err = new ApiError(
                    OffsetDateTime.now(),
                    Response.Status.BAD_REQUEST.getStatusCode(),
                    "VALIDATION_FAILED",
                    "Snapshot validation failed",
                    path,
                    requestId
            ).withValidationErrors(
                    validation.errors().stream()
                            .map(ValidationErrorDto::fromIssue)
                            .collect(Collectors.toList())
            );

            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(err)
                    .build();
        }

        long newRevision = currentRevision + 1L;
        String newEtag = String.valueOf(newRevision);
        OffsetDateTime now = OffsetDateTime.now();

        DatasetSnapshotLatestEntity entity = latest;
        if (entity == null) {
            entity = new DatasetSnapshotLatestEntity();
            entity.datasetId = datasetId;
            latestRepository.persist(entity);
        }

        entity.revision = newRevision;
        entity.etag = newEtag;
        entity.payloadJson = source.payloadJson;
        entity.updatedAt = now;

        ds.updatedAt = now;
        ds.updatedBy = principal.subject();
        ds.currentRevision = newRevision;

        // Derive schemaVersion best-effort
        Integer schemaVersion = null;
        try {
            if (payload.has("schemaVersion") && payload.get("schemaVersion").canConvertToInt()) {
                schemaVersion = payload.get("schemaVersion").intValue();
            }
        } catch (Exception ignore) {
        }

        DatasetAuditEntity audit = new DatasetAuditEntity();
        audit.datasetId = datasetId;
        audit.actorSub = principal.subject();
        audit.action = "SNAPSHOT_RESTORE";
        audit.createdAt = now;
        com.fasterxml.jackson.databind.node.ObjectNode auditDetails = objectMapper.createObjectNode()
                .put("restoredFromRevision", revision)
                .put("previousRevision", currentRevision)
                .put("newRevision", newRevision)
                .put("etag", newEtag);
        if (request != null && request.message != null && !request.message.isBlank()) {
            auditDetails.put("message", request.message.trim());
        }
        if (requestContextForceOverride) {
            auditDetails.put("forced", true);
            if (requestContextForcedLeaseHolder != null) {
                auditDetails.put("overrodeLeaseHolderSub", requestContextForcedLeaseHolder);
            }
        }
        audit.detailsJson = auditDetails.toString();
        auditRepository.persist(audit);

        // Also record the restored state into history so the timeline remains linear.
        if (snapshotHistoryKeep > 0) {
            DatasetSnapshotHistoryEntity hist = new DatasetSnapshotHistoryEntity();
            hist.datasetId = datasetId;
            hist.revision = newRevision;
            hist.etag = newEtag;
            hist.payloadJson = source.payloadJson;
            hist.schemaVersion = schemaVersion;
            hist.savedAt = now;
            hist.savedBy = principal.subject();
            String payloadJson = source.payloadJson;
            hist.payloadBytes = payloadJson == null ? 0 : payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            hist.savedAction = "RESTORE";
            String restoreMsg = (request != null && request.message != null && !request.message.isBlank())
                    ? request.message.trim()
                    : ("Restored from revision " + revision);
            hist.savedMessage = restoreMsg;
            historyRepository.persist(hist);
            historyRepository.pruneKeepLatest(datasetId, snapshotHistoryKeep);
            historyRepository.pruneByMaxAgeDays(datasetId, snapshotHistoryMaxAgeDays);
        }

        SnapshotResponse body = new SnapshotResponse(
                datasetId,
                newRevision,
                now,
                principal.subject(),
                now,
                principal.subject(),
                schemaVersion,
                payload
        );

        return Response.ok(body)
                .tag(new EntityTag(newEtag))
                .build();
    }

    private static String normalizeIfMatch(String ifMatch) {
        String token = ifMatch.split(",")[0].trim();
        if (token.startsWith("W/")) {
            token = token.substring(2).trim();
        }
        if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) {
            token = token.substring(1, token.length() - 1);
        }
        return token.trim();
    }
}