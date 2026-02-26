package info.isaksson.erland.modeller.server.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain view of a dataset (metadata only).
 * Snapshot payload is modeled separately (Phase 1).
 */
public record Dataset(
        UUID id,
        String name,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime archivedAt,
        OffsetDateTime deletedAt
) {}
