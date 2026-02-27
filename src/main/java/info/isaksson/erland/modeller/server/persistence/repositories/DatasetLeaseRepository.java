package info.isaksson.erland.modeller.server.persistence.repositories;

import info.isaksson.erland.modeller.server.persistence.entities.DatasetLeaseEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DatasetLeaseRepository implements PanacheRepositoryBase<DatasetLeaseEntity, UUID> {

    public Optional<DatasetLeaseEntity> findActive(UUID datasetId, OffsetDateTime now) {
        DatasetLeaseEntity lease = findById(datasetId);
        if (lease == null) return Optional.empty();
        if (lease.expiresAt != null && lease.expiresAt.isAfter(now)) {
            return Optional.of(lease);
        }
        return Optional.empty();
    }
}
