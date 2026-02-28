package info.isaksson.erland.modeller.server.api.dto;

import java.util.UUID;

/**
 * Returned for 409 when a client tries to append an operation with an opId
 * that already exists for the dataset.
 */
public class DuplicateOpIdResponse {
    public UUID datasetId;
    public String opId;
    public long existingRevision;

    public DuplicateOpIdResponse() {
    }

    public DuplicateOpIdResponse(UUID datasetId, String opId, long existingRevision) {
        this.datasetId = datasetId;
        this.opId = opId;
        this.existingRevision = existingRevision;
    }
}
