package info.isaksson.erland.modeller.server.persistence.repositories;

import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetAclEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetAclId;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DatasetAclRepository implements PanacheRepositoryBase<DatasetAclEntity, DatasetAclId> {

    public Optional<Role> findRole(UUID datasetId, String userSub) {
        if (datasetId == null || userSub == null || userSub.isBlank()) {
            return Optional.empty();
        }
        DatasetAclId id = new DatasetAclId(datasetId, userSub);
        DatasetAclEntity entity = findById(id);
        if (entity == null || entity.role == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Role.parse(entity.role));
        } catch (IllegalArgumentException e) {
            // Unknown role in DB - treat as no access in Phase 1
            return Optional.empty();
        }
    }
}
