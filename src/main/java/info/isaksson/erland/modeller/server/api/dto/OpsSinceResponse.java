package info.isaksson.erland.modeller.server.api.dto;

import java.util.List;

/**
 * Response body for GET /datasets/{datasetId}/ops?fromRevision=<n>&limit=<m>.
 *
 * Phase 3.
 */
public class OpsSinceResponse {
    public long fromRevision;
    public long toRevision;
    public List<OperationEvent> operations;

    public OpsSinceResponse() {}

    public OpsSinceResponse(long fromRevision, long toRevision, List<OperationEvent> operations) {
        this.fromRevision = fromRevision;
        this.toRevision = toRevision;
        this.operations = operations;
    }
}
