package info.isaksson.erland.modeller.server.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Server-accepted operation event.
 *
 * Used both for REST responses (append ops, ops since) and for streaming (SSE) payloads.
 * Phase 3.
 */
public record OperationEvent(
        UUID datasetId,
        long revision,
        String opId,
        String type,
        JsonNode payload,
        OffsetDateTime createdAt,
        String createdBy
) {
}
