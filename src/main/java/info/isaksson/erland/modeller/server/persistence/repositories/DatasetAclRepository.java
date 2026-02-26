package info.isaksson.erland.modeller.server.persistence.repositories;

import info.isaksson.erland.modeller.server.persistence.entities.DatasetAclEntity;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetAclId;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DatasetAclRepository implements PanacheRepositoryBase<DatasetAclEntity, DatasetAclId> {
}
