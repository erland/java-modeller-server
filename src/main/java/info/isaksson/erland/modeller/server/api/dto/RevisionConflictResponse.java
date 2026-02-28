package info.isaksson.erland.modeller.server.api.dto;

import java.util.UUID;

/**
 * Returned for 409 when the client's baseRevision does not match current dataset revision.
 *
 * Phase 3.
 */
public class RevisionConflictResponse {
    public UUID datasetId;
    public long baseRevision;
    public long currentRevision;

    public RevisionConflictResponse() {}

    public RevisionConflictResponse(UUID datasetId, long baseRevision, long currentRevision) {
        this.datasetId = datasetId;
        this.baseRevision = baseRevision;
        this.currentRevision = currentRevision;
    }
}
