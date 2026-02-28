package info.isaksson.erland.modeller.server.api.dto;

import java.util.List;

/**
 * Response body for POST /datasets/{datasetId}/ops.
 *
 * Phase 3.
 */
public class AppendOperationsResponse {
    /** New dataset revision after applying all accepted operations. */
    public long newRevision;

    /** Operations that were accepted and appended (in the same order). */
    public List<OperationEvent> acceptedOperations;

    public AppendOperationsResponse() {}

    public AppendOperationsResponse(long newRevision, List<OperationEvent> acceptedOperations) {
        this.newRevision = newRevision;
        this.acceptedOperations = acceptedOperations;
    }
}
