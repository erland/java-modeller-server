package info.isaksson.erland.modeller.server.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dataset_leases")
public class DatasetLeaseEntity {

    @Id
    @Column(name = "dataset_id", nullable = false)
    public UUID datasetId;

    @Column(name = "holder_sub", nullable = false)
    public String holderSub;

    @Column(name = "lease_token", nullable = false)
    public String leaseToken;

    @Column(name = "acquired_at", nullable = false)
    public OffsetDateTime acquiredAt;

    @Column(name = "renewed_at", nullable = false)
    public OffsetDateTime renewedAt;

    @Column(name = "expires_at", nullable = false)
    public OffsetDateTime expiresAt;
}
