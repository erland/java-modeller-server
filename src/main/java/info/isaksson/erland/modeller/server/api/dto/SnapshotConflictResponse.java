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
    /**
     * Backwards-compatible fields (Phase 1 / early Phase 2).
     * Prefer updatedAt/updatedBy going forward.
     */
    public OffsetDateTime savedAt;
    public String savedBy;

    /**
     * Phase 2: deterministic "last updated" fields for conflict UX.
     */
    public OffsetDateTime updatedAt;
    public String updatedBy;

    public SnapshotConflictResponse() {}

    public SnapshotConflictResponse(UUID datasetId,
                                  long currentRevision,
                                  String currentEtag,
                                  OffsetDateTime updatedAt,
                                  String updatedBy) {
        this.datasetId = datasetId;
        this.currentRevision = currentRevision;
        this.currentEtag = currentEtag;
        // Keep legacy names populated for existing clients
        this.savedAt = updatedAt;
        this.savedBy = updatedBy;
        // New deterministic names
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }
}
