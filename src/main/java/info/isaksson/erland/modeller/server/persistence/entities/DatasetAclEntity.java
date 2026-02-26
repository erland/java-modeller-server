package info.isaksson.erland.modeller.server.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "dataset_acl")
public class DatasetAclEntity {

    @EmbeddedId
    public DatasetAclId id;

    @Column(name = "role", nullable = false)
    public String role;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;
}
