package info.isaksson.erland.modeller.server.ops.policy;

import jakarta.enterprise.context.ApplicationScoped;
import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.ops.append.OpsConflict;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetLeaseEntity;
import info.isaksson.erland.modeller.server.security.PrincipalInfo;

import java.util.Optional;

/**
 * Centralized lease enforcement rules for write operations (ops append / snapshot writes).
 *
 * This is intentionally free of HTTP/REST types; callers map outcomes to API responses.
 */
@ApplicationScoped
public class LeasePolicyEnforcer {

    public enum Outcome {
        ALLOW,
        CONFLICT,
        FORBIDDEN
    }

    public record Result(
            Outcome outcome,
            boolean forcedOverride,
            String forcedLeaseHolderSub,
            OpsConflict conflict,
            String forbiddenMessage
    ) {

        public static Result allow() {
            return new Result(Outcome.ALLOW, false, null, null, null);
        }

        public static Result allowForcedOverride(String forcedLeaseHolderSub) {
            return new Result(Outcome.ALLOW, true, forcedLeaseHolderSub, null, null);
        }

        public static Result conflict(OpsConflict conflict) {
            return new Result(Outcome.CONFLICT, false, null, conflict, null);
        }

        public static Result forbidden(String message) {
            return new Result(Outcome.FORBIDDEN, false, null, null, message);
        }
    }

    /**
     * Enforce the lease rules:
     * - If there is no active lease -> allow.
     * - If active lease held by someone else:
     *     - if force && role >= OWNER -> allow (forcedOverride=true)
     *     - else -> conflict (LeaseConflict)
     * - If active lease held by caller:
     *     - require a matching token -> otherwise conflict (LeaseTokenRequired)
     */
    public Result check(PrincipalInfo principal,
                        Role role,
                        Optional<DatasetLeaseEntity> activeLeaseOpt,
                        String presentedLeaseToken,
                        boolean force) {

        if (activeLeaseOpt == null || activeLeaseOpt.isEmpty()) {
            return Result.allow();
        }

        DatasetLeaseEntity activeLease = activeLeaseOpt.get();

        // Lease held by someone else
        if (!principal.subject().equals(activeLease.holderSub)) {
            if (force) {
                if (!role.atLeast(Role.OWNER)) {
                    return Result.forbidden("Insufficient role for forced lease override");
                }
                return Result.allowForcedOverride(activeLease.holderSub);
            }
            return Result.conflict(new OpsConflict.LeaseConflict(activeLease.holderSub, activeLease.expiresAt));
        }

        // Lease held by caller: token required
        String normalizedPresented = presentedLeaseToken == null ? null : presentedLeaseToken.trim();
        if (normalizedPresented == null || normalizedPresented.isEmpty() || !normalizedPresented.equals(activeLease.leaseToken)) {
            return Result.conflict(new OpsConflict.LeaseTokenRequired());
        }

        return Result.allow();
    }
}