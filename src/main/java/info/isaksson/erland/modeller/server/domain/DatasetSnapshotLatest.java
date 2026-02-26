package info.isaksson.erland.modeller.server.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Phase 1: latest snapshot stored in DB (full payload JSON).
 */
public record DatasetSnapshotLatest(
        UUID datasetId,
        long revision,
        String etag,
        String payloadJson,
        OffsetDateTime updatedAt
) {}
