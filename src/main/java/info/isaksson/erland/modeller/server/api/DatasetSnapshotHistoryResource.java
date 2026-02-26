package info.isaksson.erland.modeller.server.api;

import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetSnapshotHistoryEntity;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAclRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetSnapshotHistoryRepository;
import info.isaksson.erland.modeller.server.security.DatasetAuthorizationService;
import info.isaksson.erland.modeller.server.security.PrincipalInfo;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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
    @Inject DatasetAuthorizationService authz;

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
}
