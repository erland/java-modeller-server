package info.isaksson.erland.modeller.server.ops.append;

import info.isaksson.erland.modeller.server.api.dto.OperationEvent;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Internal result type for append operations use-case.
 * <p>
 * Refactor seam: keep domain/policy decisions separate from HTTP response shaping.
 */
public sealed interface AppendOpsResult permits AppendOpsResult.Success, AppendOpsResult.Conflict {

    record Success(
            long previousRevision,
            long newRevision,
            OffsetDateTime committedAt,
            String committedBy,
            List<OperationEvent> accepted,
            boolean forcedLeaseOverride,
            String forcedLeaseHolderSub
    ) implements AppendOpsResult { }

    record Conflict(OpsConflict conflict) implements AppendOpsResult { }
}
