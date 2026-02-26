package info.isaksson.erland.modeller.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.isaksson.erland.modeller.server.api.dto.SnapshotResponse;
import info.isaksson.erland.modeller.server.api.dto.SnapshotConflictResponse;
import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetAuditEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetSnapshotLatestEntity;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAclRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAuditRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetSnapshotLatestRepository;
import info.isaksson.erland.modeller.server.security.DatasetAuthorizationService;
import info.isaksson.erland.modeller.server.security.PrincipalInfo;
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
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Map;

/**
 * Snapshot read endpoint (Phase 1): returns the latest snapshot for a dataset.
 */
@Path("/datasets/{datasetId}/snapshot")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetSnapshotResource {

    private final DatasetRepository datasetRepository;
    private final DatasetAclRepository aclRepository;
    private final DatasetSnapshotLatestRepository snapshotRepository;
    private final DatasetAuditRepository auditRepository;
    private final DatasetAuthorizationService authz;
    private final ObjectMapper objectMapper;

    @Inject
    public DatasetSnapshotResource(DatasetRepository datasetRepository,
                                  DatasetAclRepository aclRepository,
                                  DatasetSnapshotLatestRepository snapshotRepository,
                                  DatasetAuditRepository auditRepository,
                                  DatasetAuthorizationService authz,
                                  ObjectMapper objectMapper) {
        this.datasetRepository = datasetRepository;
        this.aclRepository = aclRepository;
        this.snapshotRepository = snapshotRepository;
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

    DatasetSnapshotLatestEntity latest = snapshotRepository.findById(datasetId);
    String currentEtag = latest == null ? "0" : (latest.etag != null ? latest.etag : String.valueOf(latest.revision));
    long currentRevision = latest == null ? 0L : latest.revision;

    if (!expected.equals(currentEtag)) {
        // Conflict: include current revision and best-effort who/when
        String savedBy = auditRepository.findLatestActorForDatasetAndAction(datasetId, "SNAPSHOT_SAVE").orElse(null);
        OffsetDateTime savedAt = latest == null ? null : latest.updatedAt;

        SnapshotConflictResponse conflict = new SnapshotConflictResponse(
                datasetId,
                currentRevision,
                currentEtag,
                savedAt,
                savedBy
        );

        return Response.status(Response.Status.CONFLICT)
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

    // Update dataset metadata (Phase 1: updatedAt only)
    ds.updatedAt = now;

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

    // Response mirrors GET semantics
    Integer schemaVersion = null;
    try {
        JsonNode stored = objectMapper.readTree(payloadJson);
        if (stored.has("schemaVersion") && stored.get("schemaVersion").canConvertToInt()) {
            schemaVersion = stored.get("schemaVersion").intValue();
        }
    } catch (Exception ignore) {
        // ignore: we already stored JSON
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
