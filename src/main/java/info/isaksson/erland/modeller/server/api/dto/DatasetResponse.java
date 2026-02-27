package info.isaksson.erland.modeller.server.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dataset metadata returned to clients (Phase 1).
 *
 * This aligns with the functional specification model where possible:
 * - status is derived from archivedAt/deletedAt (ACTIVE/ARCHIVED/DELETED)
 * - currentRevision is mirrored from server-maintained metadata (0 if none)
 */
public class DatasetResponse {
    public UUID id;
    public String name;
    public String description;

    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
    public OffsetDateTime archivedAt;
    public OffsetDateTime deletedAt;

    /** Best-effort identity of creator/updater (OIDC subject). */
    public String createdBy;
    public String updatedBy;

    /** Latest known snapshot revision (0 if none). */
    public long currentRevision;

    /** Phase 2: none | basic | strict */
    public String validationPolicy;

    /** Derived: ACTIVE | ARCHIVED | DELETED */
    public String status;

    /** Current user's role for this dataset (OWNER/EDITOR/VIEWER). */
    public String role;
}
