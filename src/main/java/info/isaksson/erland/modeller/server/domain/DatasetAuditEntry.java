package info.isaksson.erland.modeller.server.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DatasetAuditEntry(
        long id,
        UUID datasetId,
        String actorSub,
        String action,
        OffsetDateTime createdAt,
        String detailsJson
) {}
