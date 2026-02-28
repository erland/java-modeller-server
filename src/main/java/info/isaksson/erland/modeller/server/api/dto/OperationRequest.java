package info.isaksson.erland.modeller.server.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Single client-supplied operation (command) in an append-ops request.
 *
 * Phase 3.
 */
public class OperationRequest {
    /** Client-generated stable identifier (idempotency + client-side tracking). */
    public String opId;

    /** Operation type discriminator, e.g. "ADD_ELEMENT", "UPDATE_PROPERTY". */
    public String type;

    /** Operation payload. Schema/validation is driven by ValidationPolicy and server rules. */
    public JsonNode payload;

    public OperationRequest() {}

    public OperationRequest(String opId, String type, JsonNode payload) {
        this.opId = opId;
        this.type = type;
        this.payload = payload;
    }
}
