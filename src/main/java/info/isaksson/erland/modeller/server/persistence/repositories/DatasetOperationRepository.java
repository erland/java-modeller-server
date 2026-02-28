package info.isaksson.erland.modeller.server.persistence.repositories;

import info.isaksson.erland.modeller.server.persistence.entities.DatasetOperationEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetOperationId;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DatasetOperationRepository implements PanacheRepositoryBase<DatasetOperationEntity, DatasetOperationId> {

    /**
     * Returns operations with revision strictly greater than {@code fromRevision}, ordered by revision ascending.
     */
    public List<DatasetOperationEntity> listAfterRevision(UUID datasetId, long fromRevision, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        return find("datasetId = ?1 and revision > ?2 order by revision asc", datasetId, fromRevision)
                .page(Page.ofSize(safeLimit))
                .list();
    }

    /**
     * Returns the operation with the given opId for the dataset, if present.
     */
    public java.util.Optional<DatasetOperationEntity> findByOpId(UUID datasetId, String opId) {
        if (opId == null) {
            return java.util.Optional.empty();
        }
        return find("datasetId = ?1 and opId = ?2", datasetId, opId).firstResultOptional();
    }
}