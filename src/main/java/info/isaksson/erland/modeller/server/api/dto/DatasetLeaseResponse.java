package info.isaksson.erland.modeller.server.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lease response DTO used for both status and acquire/refresh.
 * When active=false, holderSub/expiresAt/leaseToken may be null.
 * When active=true:
 *  - status endpoint omits leaseToken (null)
 *  - acquire/refresh returns leaseToken
 */
public class DatasetLeaseResponse {
    public UUID datasetId;
    public boolean active;
    public String holderSub;
    public OffsetDateTime acquiredAt;
    public OffsetDateTime renewedAt;
    public OffsetDateTime expiresAt;
    public String leaseToken;

    public DatasetLeaseResponse() {}

    public static DatasetLeaseResponse inactive(UUID datasetId) {
        DatasetLeaseResponse r = new DatasetLeaseResponse();
        r.datasetId = datasetId;
        r.active = false;
        return r;
    }
}
