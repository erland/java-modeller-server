package info.isaksson.erland.modeller.server.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response for GET latest snapshot (Phase 1).
 *
 * Mirrors the API examples: returns revision + payload and includes ETag header.
 */
public record SnapshotResponse(
        UUID datasetId,
        long revision,
        OffsetDateTime savedAt,
        String savedBy,
        // For convenience/compatibility with step plan wording:
        OffsetDateTime updatedAt,
        String updatedBy,
        Integer schemaVersion,
        JsonNode payload
) {
}
