package info.isaksson.erland.modeller.server.api;

import info.isaksson.erland.modeller.server.api.dto.DatasetHeadResponse;
import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetLeaseEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetSnapshotLatestEntity;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetSnapshotLatestRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetLeaseRepository;
import info.isaksson.erland.modeller.server.security.DatasetAuthorizationService;

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
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 2: Lightweight dataset head endpoint for polling (revision/etag/updatedBy/updatedAt + lease status).
 */
@Path("/datasets/{datasetId}/head")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetHeadResource {

    private final DatasetRepository datasetRepository;
    private final DatasetSnapshotLatestRepository snapshotRepository;
    private final DatasetLeaseRepository leaseRepository;
    private final DatasetAuthorizationService authz;

    @Inject
    public DatasetHeadResource(DatasetRepository datasetRepository,
                              DatasetSnapshotLatestRepository snapshotRepository,
                              DatasetLeaseRepository leaseRepository,
                              DatasetAuthorizationService authz) {
        this.datasetRepository = datasetRepository;
        this.snapshotRepository = snapshotRepository;
        this.leaseRepository = leaseRepository;
        this.authz = authz;
    }

    @GET
    public Response getHead(@PathParam("datasetId") UUID datasetId) {
        authz.requireAtLeast(datasetId, Role.VIEWER);

        DatasetEntity ds = datasetRepository.findById(datasetId);
        if (ds == null || ds.deletedAt != null) {
            throw new NotFoundException();
        }

        DatasetSnapshotLatestEntity latest = snapshotRepository.findById(datasetId);

        long revision = 0;
        String etag = "0";
        if (latest != null) {
            revision = latest.revision;
            etag = latest.etag == null ? "0" : latest.etag;
        }

        OffsetDateTime now = OffsetDateTime.now();
        Optional<DatasetLeaseEntity> activeLease = leaseRepository.findActive(datasetId, now);

        DatasetHeadResponse res = new DatasetHeadResponse();
        res.datasetId = datasetId;
        res.currentRevision = revision;
        res.currentEtag = etag;
        res.updatedAt = ds.updatedAt;
        res.updatedBy = ds.updatedBy;
        res.validationPolicy = ds.validationPolicy == null ? null : ds.validationPolicy.wireValue();
        res.archivedAt = ds.archivedAt;
        res.deletedAt = ds.deletedAt;

        res.leaseActive = activeLease.isPresent();
        if (activeLease.isPresent()) {
            res.leaseHolderSub = activeLease.get().holderSub;
            res.leaseExpiresAt = activeLease.get().expiresAt;
        } else {
            res.leaseHolderSub = null;
            res.leaseExpiresAt = null;
        }

        return Response.ok(res)
                .tag(new EntityTag(etag))
                .build();
    }
}
