package info.isaksson.erland.modeller.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.isaksson.erland.modeller.server.api.dto.SnapshotResponse;
import info.isaksson.erland.modeller.server.api.dto.SnapshotConflictResponse;
import info.isaksson.erland.modeller.server.api.dto.ValidationErrorDto;
import info.isaksson.erland.modeller.server.api.error.ApiError;
import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetAuditEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetSnapshotLatestEntity;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAclRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAuditRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetSnapshotLatestRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetLeaseRepository;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetLeaseEntity;
import info.isaksson.erland.modeller.server.api.dto.LeaseConflictResponse;
import info.isaksson.erland.modeller.server.security.DatasetAuthorizationService;
import info.isaksson.erland.modeller.server.security.PrincipalInfo;
import info.isaksson.erland.modeller.server.domain.ValidationPolicy;
import info.isaksson.erland.modeller.server.validation.SnapshotValidationService;
import info.isaksson.erland.modeller.server.validation.ValidationResult;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetSnapshotHistoryEntity;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetSnapshotHistoryRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.MDC;

/**
 * Snapshot read endpoint (Phase 1): returns the latest snapshot for a dataset.
 */
@Path("/datasets/{datasetId}/snapshot")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetSnapshotResource {

    @jakarta.ws.rs.core.Context
    UriInfo uriInfo;

    private final DatasetRepository datasetRepository;
    private final DatasetAclRepository aclRepository;
    private final DatasetSnapshotLatestRepository snapshotRepository;
    private final DatasetLeaseRepository leaseRepository;

    @jakarta.inject.Inject
    DatasetSnapshotHistoryRepository historyRepository;

    @ConfigProperty(name = "modeller.snapshot.history.keep", defaultValue = "20")
    int snapshotHistoryKeep;

    @ConfigProperty(name = "modeller.snapshot.history.maxAgeDays", defaultValue = "0")
    int snapshotHistoryMaxAgeDays;
    private final DatasetAuditRepository auditRepository;
    private final DatasetAuthorizationService authz;
    private final ObjectMapper objectMapper;

    @Inject
    SnapshotValidationService validationService;

    @Inject
    public DatasetSnapshotResource(DatasetRepository datasetRepository,
                                  DatasetAclRepository aclRepository,
                                  DatasetSnapshotLatestRepository snapshotRepository,
                                  DatasetLeaseRepository leaseRepository,
                                  DatasetAuditRepository auditRepository,
                                  DatasetAuthorizationService authz,
                                  ObjectMapper objectMapper) {
        this.datasetRepository = datasetRepository;
        this.aclRepository = aclRepository;
        this.snapshotRepository = snapshotRepository;
        this.leaseRepository = leaseRepository;
        this.auditRepository = auditRepository;
        this.authz = authz;
        this.objectMapper = objectMapper;
    }

    @GET
    public Response getLatest(@PathParam("datasetId") UUID datasetId) {
        PrincipalInfo principal = authz.currentPrincipal();

        // Visibility check (avoid leaking dataset existence)
        Role role = aclRepository.findRole(datasetId, principal.subject())
                .orElseThrow(() -> new NotFoundException("Dataset not found"));
        if (!role.atLeast(Role.VIEWER)) {
            throw new jakarta.ws.rs.ForbiddenException("Insufficient role for dataset");
        }

        DatasetEntity ds = datasetRepository.findById(datasetId);
        if (ds == null || ds.deletedAt != null) {
            throw new NotFoundException("Dataset not found");
        }

        DatasetSnapshotLatestEntity latest = snapshotRepository.findById(datasetId);

        if (latest == null) {
            // Phase 1 policy: return an empty snapshot with ETag "0".
            ObjectNode payload = objectMapper.createObjectNode();
            payload.set("model", objectMapper.createObjectNode());

            SnapshotResponse body = new SnapshotResponse(
                    datasetId,
                    0L,
                    null,
                    null,
                    null,
                    null,
                    null,
                    payload
            );

            return Response.ok(body)
                    .tag(new EntityTag("0"))
                    .build();
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(latest.payloadJson);
        } catch (Exception e) {
            // If stored payload is invalid JSON, treat as server error.
            throw new jakarta.ws.rs.InternalServerErrorException("Stored snapshot payload is invalid JSON", e);
        }

        Integer schemaVersion = null;
        if (payload != null && payload.has("schemaVersion") && payload.get("schemaVersion").canConvertToInt()) {
            schemaVersion = payload.get("schemaVersion").intValue();
        }

        // Best-effort: identify who last saved (requires Step 9 to start writing audit entries).
        String savedBy = auditRepository.findLatestActorForDatasetAndAction(datasetId, "SNAPSHOT_SAVE")
                .orElse(null);

        OffsetDateTime savedAt = latest.updatedAt;

        SnapshotResponse body = new SnapshotResponse(
                datasetId,
                latest.revision,
                savedAt,
                savedBy,
                savedAt,
                savedBy,
                schemaVersion,
                payload
        );

        String etagValue = latest.etag != null ? latest.etag : String.valueOf(latest.revision);

        return Response.ok(body)
                .tag(new EntityTag(etagValue))
                .build();
    }


    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response putLatest(@PathParam("datasetId") UUID datasetId,
                          @HeaderParam("If-Match") String ifMatch,
                          @HeaderParam("X-Lease-Token") String leaseToken,
                          JsonNode payload) {
    PrincipalInfo principal = authz.currentPrincipal();

    // Require at least EDITOR to write snapshots
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
        // Step 9: requires If-Match (client must send "0" for first write)
        return Response.status(428)
                .entity(Map.of(
                        "error", "precondition_required",
                        "message", "Missing If-Match header"
                ))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    String expected = normalizeIfMatch(ifMatch);

// Phase 2: enforce dataset leases (soft locks). Leases complement revision checks.
// - If an active lease exists and is held by someone else -> 409 LeaseConflictResponse
// - If an active lease exists and is held by caller -> require matching X-Lease-Token
OffsetDateTime nowLeaseCheck = OffsetDateTime.now();
java.util.Optional<DatasetLeaseEntity> activeLeaseOpt = leaseRepository.findActive(datasetId, nowLeaseCheck);
if (activeLeaseOpt.isPresent()) {
    DatasetLeaseEntity activeLease = activeLeaseOpt.get();
    if (!principal.subject().equals(activeLease.holderSub)) {
        LeaseConflictResponse conflict = new LeaseConflictResponse(datasetId, activeLease.holderSub, activeLease.expiresAt);
        return Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(conflict)
                .build();
    }
    if (leaseToken == null || leaseToken.isBlank() || !leaseToken.trim().equals(activeLease.leaseToken)) {
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


    DatasetSnapshotLatestEntity latest = snapshotRepository.findById(datasetId);
    String currentEtag = latest == null ? "0" : (latest.etag != null ? latest.etag : String.valueOf(latest.revision));
    long currentRevision = latest == null ? 0L : latest.revision;

    if (!expected.equals(currentEtag)) {
        // Phase 2: enriched conflict response for clients.
        // Include deterministic current revision + ETag + who/when last updated.
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

    if (payload == null) {
        throw new jakarta.ws.rs.BadRequestException("Snapshot payload is required");
    }

    // Normalize payload: always store as JSON object
    String payloadJson;
    try {
        payloadJson = objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
        throw new jakarta.ws.rs.BadRequestException("Invalid JSON payload", e);
    }



    // Phase 2: validate snapshot payload according to dataset validation policy
    ValidationPolicy policy = ds.validationPolicy == null ? ValidationPolicy.NONE : ds.validationPolicy;
    ValidationResult validation = validationService.validate(payload, payloadJson, policy);
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
    }
    entity.revision = newRevision;
    entity.etag = newEtag;
    entity.payloadJson = payloadJson;
    entity.updatedAt = now;

    if (latest == null) {
        snapshotRepository.persist(entity);
    }

    // Update dataset metadata (Phase 1)
    ds.updatedAt = now;
    ds.updatedBy = principal.subject();
    ds.currentRevision = newRevision;

    // Derive schemaVersion from payload (best-effort)
    Integer schemaVersion = null;
    try {
        JsonNode stored = objectMapper.readTree(payloadJson);
        if (stored.has("schemaVersion") && stored.get("schemaVersion").canConvertToInt()) {
            schemaVersion = stored.get("schemaVersion").intValue();
        }
    } catch (Exception ignore) {
        // ignore: payload is still stored as JSON
    }

// Write audit entry
    DatasetAuditEntity audit = new DatasetAuditEntity();
    audit.datasetId = datasetId;
    audit.actorSub = principal.subject();
    audit.action = "SNAPSHOT_SAVE";
    audit.createdAt = now;
    audit.detailsJson = objectMapper.createObjectNode()
            .put("previousRevision", currentRevision)
            .put("newRevision", newRevision)
            .put("etag", newEtag)
            .toString();
    auditRepository.persist(audit);

    // Optional history: store prior snapshots and prune to keep latest N
    if (snapshotHistoryKeep > 0) {
        DatasetSnapshotHistoryEntity hist = new DatasetSnapshotHistoryEntity();
        hist.datasetId = datasetId;
        hist.revision = newRevision;
        hist.etag = newEtag;
        hist.payloadJson = payloadJson;
        hist.schemaVersion = schemaVersion;
        hist.savedAt = now;
        hist.savedBy = principal.subject();
        hist.payloadBytes = payloadJson == null ? 0 : payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        hist.savedAction = "WRITE";
        hist.savedMessage = null;
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