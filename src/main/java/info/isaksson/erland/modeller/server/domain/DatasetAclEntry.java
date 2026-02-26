package info.isaksson.erland.modeller.server.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DatasetAclEntry(
        UUID datasetId,
        String userSub,
        Role role,
        OffsetDateTime createdAt
) {}
