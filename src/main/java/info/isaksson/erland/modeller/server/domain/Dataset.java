package info.isaksson.erland.modeller.server.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain view of a dataset (metadata).
 *
 * Note: status is derived from archivedAt/deletedAt. currentRevision is maintained by the server.
 */
public record Dataset(
        UUID id,
        String name,
        String description,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy,
        long currentRevision,
        OffsetDateTime archivedAt,
        OffsetDateTime deletedAt
) {}
