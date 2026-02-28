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
@Table(name = "dataset_operation")
@IdClass(DatasetOperationId.class)
public class DatasetOperationEntity {

    @Id
    @Column(name = "dataset_id", nullable = false)
    public UUID datasetId;

    @Id
    @Column(name = "revision", nullable = false)
    public long revision;

    @Column(name = "op_id", nullable = false)
    public String opId;

    @Column(name = "op_type", nullable = false)
    public String opType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    public String payloadJson;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "created_by")
    public String createdBy;
}
