package info.isaksson.erland.modeller.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.isaksson.erland.modeller.server.api.dto.SnapshotResponse;
import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetEntity;
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
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.OffsetDateTime;
import java.util.UUID;

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
}
