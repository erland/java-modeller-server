package info.isaksson.erland.modeller.server.ops.append;

import info.isaksson.erland.modeller.server.api.dto.ValidationErrorDto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Internal conflict types for ops append.
 */
public sealed interface OpsConflict permits
        OpsConflict.RevisionConflict,
        OpsConflict.DuplicateOpId,
        OpsConflict.LeaseConflict,
        OpsConflict.LeaseTokenRequired,
        OpsConflict.ValidationFailed {

    record RevisionConflict(long requestedBaseRevision, long currentRevision) implements OpsConflict { }

    record DuplicateOpId(String opId, long existingRevision) implements OpsConflict { }

    record LeaseConflict(String holderSub, OffsetDateTime expiresAt) implements OpsConflict { }

    /**
     * Caller is the lease holder, but did not present a matching token.
     */
    record LeaseTokenRequired() implements OpsConflict { }

    record ValidationFailed(List<ValidationErrorDto> errors) implements OpsConflict { }
}
