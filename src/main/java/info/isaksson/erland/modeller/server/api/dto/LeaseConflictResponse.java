package info.isaksson.erland.modeller.server.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Returned for 409 when a dataset is leased by another user. */
public class LeaseConflictResponse {
    public UUID datasetId;
    public String holderSub;
    public OffsetDateTime expiresAt;

    public LeaseConflictResponse() {}

    public LeaseConflictResponse(UUID datasetId, String holderSub, OffsetDateTime expiresAt) {
        this.datasetId = datasetId;
        this.holderSub = holderSub;
        this.expiresAt = expiresAt;
    }
}
