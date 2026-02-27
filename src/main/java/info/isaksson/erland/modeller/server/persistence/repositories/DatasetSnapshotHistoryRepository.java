package info.isaksson.erland.modeller.server.persistence.repositories;

import info.isaksson.erland.modeller.server.persistence.entities.DatasetSnapshotHistoryEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetSnapshotHistoryId;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DatasetSnapshotHistoryRepository implements PanacheRepositoryBase<DatasetSnapshotHistoryEntity, DatasetSnapshotHistoryId> {

    public java.util.Optional<DatasetSnapshotHistoryEntity> findByDatasetAndRevision(UUID datasetId, long revision) {
        return find("datasetId = ?1 AND revision = ?2", datasetId, revision).firstResultOptional();
    }

    public List<DatasetSnapshotHistoryEntity> listForDataset(UUID datasetId, int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int safeOffset = Math.max(0, offset);
        int pageIndex = safeOffset / safeLimit;

        return find("datasetId = ?1 ORDER BY revision DESC", datasetId)
                .page(pageIndex, safeLimit)
                .list();
    }

    public void pruneKeepLatest(UUID datasetId, int keep) {
        if (keep <= 0) {
            return;
        }
        List<Long> keepRevs = getEntityManager()
                .createQuery("select h.revision from DatasetSnapshotHistoryEntity h where h.datasetId = :id order by h.revision desc", Long.class)
                .setParameter("id", datasetId)
                .setMaxResults(keep)
                .getResultList();

        if (keepRevs == null || keepRevs.isEmpty()) {
            return;
        }

        delete("datasetId = ?1 AND revision NOT IN ?2", datasetId, keepRevs);
    }
}
