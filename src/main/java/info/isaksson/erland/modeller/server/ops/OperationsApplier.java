package info.isaksson.erland.modeller.server.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.isaksson.erland.modeller.server.api.dto.OperationRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.util.List;

/**
 * Applies Phase 3 operations deterministically to the current snapshot payload.
 *
 * <p>This repository doesn't yet define a rich domain command language.
 * To enable server-side materialization now (Strategy A), we support a small
 * set of generic, JSON-based operations:
 *
 * <ul>
 *   <li><b>SNAPSHOT_REPLACE</b>: payload is the full snapshot JSON (same shape as PUT /snapshot body)</li>
 *   <li><b>JSON_PATCH</b>: payload is a JSON Patch array (subset: add/remove/replace) applied to the snapshot JSON</li>
 * </ul>
 */
@ApplicationScoped
public class OperationsApplier {

    public static final String OP_SNAPSHOT_REPLACE = "SNAPSHOT_REPLACE";
    public static final String OP_JSON_PATCH = "JSON_PATCH";

    private final ObjectMapper mapper;
    private final JsonPatchApplier jsonPatch;

    @Inject
    public OperationsApplier(ObjectMapper mapper) {
        this.mapper = mapper;
        this.jsonPatch = new JsonPatchApplier(mapper);
    }

    public JsonNode applyAll(JsonNode baseSnapshot, List<OperationRequest> operations) {
        JsonNode current = ensureBase(baseSnapshot);
        for (OperationRequest op : operations) {
            if (op == null) {
                throw new BadRequestException("Operation must not be null");
            }
            if (op.type == null || op.type.isBlank()) {
                throw new BadRequestException("Operation.type is required");
            }
            if (op.opId == null || op.opId.isBlank()) {
                throw new BadRequestException("Operation.opId is required");
            }

            String type = op.type.trim();
            switch (type) {
                case OP_SNAPSHOT_REPLACE -> {
                    if (op.payload == null || op.payload.isNull()) {
                        throw new BadRequestException("SNAPSHOT_REPLACE requires payload");
                    }
                    current = op.payload.deepCopy();
                }
                case OP_JSON_PATCH -> {
                    current = jsonPatch.apply(current, op.payload);
                }
                default -> throw new BadRequestException("Unsupported operation type: " + type);
            }
        }
        return current;
    }

    private JsonNode ensureBase(JsonNode base) {
        if (base == null || base.isNull()) {
            ObjectNode payload = mapper.createObjectNode();
            payload.set("model", mapper.createObjectNode());
            return payload;
        }
        return base;
    }
}
