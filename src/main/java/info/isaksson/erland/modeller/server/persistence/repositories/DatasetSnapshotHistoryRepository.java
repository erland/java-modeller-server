package info.isaksson.erland.modeller.server.persistence.repositories;

import info.isaksson.erland.modeller.server.persistence.entities.DatasetSnapshotHistoryEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetSnapshotHistoryId;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DatasetSnapshotHistoryRepository implements PanacheRepositoryBase<DatasetSnapshotHistoryEntity, DatasetSnapshotHistoryId> {

    public Optional<DatasetSnapshotHistoryEntity> findByDatasetAndRevision(UUID datasetId, long revision) {
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
        // Single-statement prune to avoid pulling all revisions into memory.
        // NOTE: Some drivers/providers don't support binding LIMIT values, so we inline the validated integer.
        int safeKeep = Math.max(1, Math.min(keep, 10_000));
        String sql =
                "DELETE FROM dataset_snapshot_history h " +
                "WHERE h.dataset_id = :id AND h.revision NOT IN (" +
                "  SELECT revision FROM dataset_snapshot_history WHERE dataset_id = :id ORDER BY revision DESC LIMIT " + safeKeep +
                ")";
        getEntityManager().createNativeQuery(sql)
                .setParameter("id", datasetId)
                .executeUpdate();
    }

    public void pruneByMaxAgeDays(UUID datasetId, int maxAgeDays) {
        if (maxAgeDays <= 0) {
            return;
        }
        getEntityManager().createNativeQuery(
                        "DELETE FROM dataset_snapshot_history " +
                        "WHERE dataset_id = :id AND saved_at < (NOW() - (:days || ' days')::interval)"
                )
                .setParameter("id", datasetId)
                .setParameter("days", maxAgeDays)
                .executeUpdate();
    }
}
