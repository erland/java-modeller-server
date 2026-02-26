package info.isaksson.erland.modeller.server.persistence.repositories;

import info.isaksson.erland.modeller.server.persistence.entities.DatasetEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class DatasetRepository implements PanacheRepositoryBase<DatasetEntity, UUID> {
}
