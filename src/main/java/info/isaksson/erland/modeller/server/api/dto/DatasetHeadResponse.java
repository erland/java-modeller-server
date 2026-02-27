package info.isaksson.erland.modeller.server.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lightweight dataset "head" information for polling.
 * Does not include the full snapshot payload.
 */
public class DatasetHeadResponse {
    public UUID datasetId;

    public long currentRevision;
    public String currentEtag;

    public OffsetDateTime updatedAt;
    public String updatedBy;

    public String validationPolicy;

    public OffsetDateTime archivedAt;
    public OffsetDateTime deletedAt;

    public boolean leaseActive;
    public String leaseHolderSub;
    public OffsetDateTime leaseExpiresAt;
}
