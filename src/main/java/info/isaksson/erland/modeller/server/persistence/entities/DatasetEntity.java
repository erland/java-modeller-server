package info.isaksson.erland.modeller.server.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "datasets")
public class DatasetEntity {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "description")
    public String description;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;


    @Column(name = "created_by")
    public String createdBy;

    @Column(name = "updated_by")
    public String updatedBy;

    @Column(name = "current_revision", nullable = false)
    public long currentRevision;


    @Column(name = "archived_at")
    public OffsetDateTime archivedAt;

    @Column(name = "deleted_at")
    public OffsetDateTime deletedAt;
}
