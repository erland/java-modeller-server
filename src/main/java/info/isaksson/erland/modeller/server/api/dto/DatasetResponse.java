package info.isaksson.erland.modeller.server.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dataset metadata returned to clients (Phase 1).
 */
public class DatasetResponse {
    public UUID id;
    public String name;
    public String description;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
    public OffsetDateTime archivedAt;
    public OffsetDateTime deletedAt;

    /** Current user's role for this dataset (OWNER/EDITOR/VIEWER). */
    public String role;
}
