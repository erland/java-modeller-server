package info.isaksson.erland.modeller.server.persistence.repositories;

import info.isaksson.erland.modeller.server.persistence.entities.DatasetAuditEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DatasetAuditRepository implements PanacheRepositoryBase<DatasetAuditEntity, Long> {
}
