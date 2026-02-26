package info.isaksson.erland.modeller.server.persistence.repositories;

import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetAclEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetAclId;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
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

    public List<UUID> findDatasetIdsForUser(String userSub) {
        if (userSub == null || userSub.isBlank()) {
            return List.of();
        }
        return find("id.userSub", userSub).stream()
                .map(e -> e.id != null ? e.id.datasetId : null)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    public List<DatasetAclEntity> listEntries(UUID datasetId) {
        if (datasetId == null) return List.of();
        return list("id.datasetId", datasetId);
    }

    public long countOwners(UUID datasetId) {
        if (datasetId == null) return 0;
        return count("id.datasetId = ?1 and role = ?2", datasetId, Role.OWNER.name());
    }

    public void upsert(UUID datasetId, String userSub, Role role, java.time.OffsetDateTime createdAtIfNew) {
        DatasetAclId id = new DatasetAclId(datasetId, userSub);
        DatasetAclEntity existing = findById(id);
        if (existing == null) {
            DatasetAclEntity e = new DatasetAclEntity();
            e.id = id;
            e.role = role.name();
            e.createdAt = createdAtIfNew;
            persist(e);
        } else {
            existing.role = role.name();
            persist(existing);
        }
    }

    public boolean deleteEntry(UUID datasetId, String userSub) {
        if (datasetId == null || userSub == null || userSub.isBlank()) return false;
        return deleteById(new DatasetAclId(datasetId, userSub));
    }
}
