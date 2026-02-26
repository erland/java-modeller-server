package info.isaksson.erland.modeller.server.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dataset_snapshot_history")
@IdClass(DatasetSnapshotHistoryId.class)
public class DatasetSnapshotHistoryEntity {

    @Id
    @Column(name = "dataset_id", nullable = false)
    public UUID datasetId;

    @Id
    @Column(name = "revision", nullable = false)
    public long revision;

    @Column(name = "etag", nullable = false)
    public String etag;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    public String payloadJson;

    @Column(name = "schema_version")
    public Integer schemaVersion;

    @Column(name = "saved_at", nullable = false)
    public OffsetDateTime savedAt;

    @Column(name = "saved_by")
    public String savedBy;
}
