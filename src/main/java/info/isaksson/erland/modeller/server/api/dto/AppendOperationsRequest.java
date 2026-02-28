package info.isaksson.erland.modeller.server.api.dto;

import java.util.List;

/**
 * Request body for POST /datasets/{datasetId}/ops.
 *
 * Phase 3.
 */
public class AppendOperationsRequest {
    /** Base revision the client built the operations against. */
    public Long baseRevision;

    /** Operations to append, applied sequentially in the order provided. */
    public List<OperationRequest> operations;
}
