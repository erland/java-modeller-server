package info.isaksson.erland.modeller.server.persistence.repositories;

import info.isaksson.erland.modeller.server.persistence.entities.DatasetAuditEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DatasetAuditRepository implements PanacheRepositoryBase<DatasetAuditEntity, Long> {

    /**
     * Best-effort helper for Step 8: who last performed a given action for a dataset.
     * Step 9 will start writing these audit entries.
     */
    public Optional<String> findLatestActorForDatasetAndAction(UUID datasetId, String action) {
        DatasetAuditEntity e = find("datasetId = ?1 and action = ?2 order by createdAt desc", datasetId, action)
                .firstResult();
        return e == null ? Optional.empty() : Optional.ofNullable(e.actorSub);
    }


    public long countForDatasetAndAction(UUID datasetId, String action) {
        return count("datasetId = ?1 and action = ?2", datasetId, action);
    }

    public java.util.List<DatasetAuditEntity> listForDataset(UUID datasetId) {
        return list("datasetId = ?1 order by createdAt asc", datasetId);
    }

}
