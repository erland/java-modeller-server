package info.isaksson.erland.modeller.server.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body for snapshot write conflicts (stale If-Match).
 */
public class SnapshotConflictResponse {
    public UUID datasetId;
    public long currentRevision;
    public String currentEtag;
    public OffsetDateTime savedAt;
    public String savedBy;

    public SnapshotConflictResponse() {}

    public SnapshotConflictResponse(UUID datasetId, long currentRevision, String currentEtag, OffsetDateTime savedAt, String savedBy) {
        this.datasetId = datasetId;
        this.currentRevision = currentRevision;
        this.currentEtag = currentEtag;
        this.savedAt = savedAt;
        this.savedBy = savedBy;
    }
}
