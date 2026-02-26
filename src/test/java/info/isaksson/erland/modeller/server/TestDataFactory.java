package info.isaksson.erland.modeller.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetAclEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetAclId;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetSnapshotLatestEntity;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAclRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetSnapshotLatestRepository;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAuditRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Test helper that performs DB setup inside real transactions.
 * Using @Transactional here ensures data is committed before REST calls.
 */
@ApplicationScoped
public class TestDataFactory {

    @Inject DatasetRepository datasetRepository;
    @Inject DatasetAclRepository aclRepository;
    @Inject DatasetSnapshotLatestRepository snapshotRepository;
    @Inject DatasetAuditRepository auditRepository;
    @Inject ObjectMapper objectMapper;

    @Transactional
    public UUID createDatasetVisibleTo(String userSub) {
        OffsetDateTime now = OffsetDateTime.now();

        DatasetEntity ds = new DatasetEntity();
        ds.id = UUID.randomUUID();
        ds.name = "Test dataset";
        ds.description = null;
        ds.createdAt = now;
        ds.updatedAt = now;
        ds.archivedAt = null;
        ds.deletedAt = null;
        datasetRepository.persist(ds);

        DatasetAclEntity acl = new DatasetAclEntity();
        acl.id = new DatasetAclId(ds.id, userSub);
        acl.role = "OWNER";
        acl.createdAt = now;
        aclRepository.persist(acl);

        return ds.id;
    }

    @Transactional
    public void grantAcl(UUID datasetId, String userSub, String role) {
        OffsetDateTime now = OffsetDateTime.now();
        DatasetAclEntity acl = new DatasetAclEntity();
        acl.id = new DatasetAclId(datasetId, userSub);
        acl.role = role;
        acl.createdAt = now;
        aclRepository.persist(acl);
    }

    @Transactional
    public void createSnapshot(UUID datasetId, long revision, int schemaVersion) throws Exception {
        DatasetSnapshotLatestEntity s = new DatasetSnapshotLatestEntity();
        s.datasetId = datasetId;
        s.revision = revision;
        s.etag = String.valueOf(revision);
        s.payloadJson = objectMapper.createObjectNode()
                .put("schemaVersion", schemaVersion)
                .set("model", objectMapper.createObjectNode())
                .toString();
        s.updatedAt = OffsetDateTime.now();
        snapshotRepository.persist(s);
    }


    @Transactional
    public long countAudit(UUID datasetId, String action) {
        return auditRepository.countForDatasetAndAction(datasetId, action);
    }

    @Transactional
    public java.util.List<String> listAuditActions(UUID datasetId) {
        return auditRepository.listForDataset(datasetId).stream().map(a -> a.action).toList();
    }

}
