package info.isaksson.erland.modeller.server.ops.policy;

import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.ops.append.OpsConflict;
import info.isaksson.erland.modeller.server.persistence.entities.DatasetLeaseEntity;
import info.isaksson.erland.modeller.server.security.PrincipalInfo;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LeasePolicyEnforcerTest {

    private static DatasetLeaseEntity leaseHeldBy(String holderSub, String token) {
        DatasetLeaseEntity e = new DatasetLeaseEntity();
        e.datasetId = UUID.randomUUID();
        e.holderSub = holderSub;
        e.leaseToken = token;
        e.acquiredAt = OffsetDateTime.now().minusMinutes(1);
        e.renewedAt = OffsetDateTime.now().minusSeconds(10);
        e.expiresAt = OffsetDateTime.now().plusMinutes(5);
        return e;
    }

    private static PrincipalInfo principal(String sub) {
        return new PrincipalInfo(sub, sub, null);
    }

    @Test
    public void allows_when_no_active_lease() {
        LeasePolicyEnforcer enforcer = new LeasePolicyEnforcer();
        LeasePolicyEnforcer.Result r = enforcer.check(principal("alice"), Role.EDITOR, Optional.empty(), null, false);

        assertEquals(LeasePolicyEnforcer.Outcome.ALLOW, r.outcome());
        assertFalse(r.forcedOverride());
        assertNull(r.conflict());
    }

    @Test
    public void conflicts_when_lease_held_by_other_without_force() {
        LeasePolicyEnforcer enforcer = new LeasePolicyEnforcer();
        DatasetLeaseEntity lease = leaseHeldBy("bob", "token-bob");

        LeasePolicyEnforcer.Result r = enforcer.check(principal("alice"), Role.OWNER, Optional.of(lease), null, false);

        assertEquals(LeasePolicyEnforcer.Outcome.CONFLICT, r.outcome());
        assertTrue(r.conflict() instanceof OpsConflict.LeaseConflict);
        OpsConflict.LeaseConflict c = (OpsConflict.LeaseConflict) r.conflict();
        assertEquals("bob", c.holderSub());
        assertEquals(lease.expiresAt, c.expiresAt());
    }

    @Test
    public void forbids_force_override_when_role_not_owner() {
        LeasePolicyEnforcer enforcer = new LeasePolicyEnforcer();
        DatasetLeaseEntity lease = leaseHeldBy("bob", "token-bob");

        LeasePolicyEnforcer.Result r = enforcer.check(principal("alice"), Role.EDITOR, Optional.of(lease), null, true);

        assertEquals(LeasePolicyEnforcer.Outcome.FORBIDDEN, r.outcome());
        assertNull(r.conflict());
    }

    @Test
    public void allows_force_override_when_role_owner_or_higher() {
        LeasePolicyEnforcer enforcer = new LeasePolicyEnforcer();
        DatasetLeaseEntity lease = leaseHeldBy("bob", "token-bob");

        LeasePolicyEnforcer.Result r = enforcer.check(principal("alice"), Role.OWNER, Optional.of(lease), null, true);

        assertEquals(LeasePolicyEnforcer.Outcome.ALLOW, r.outcome());
        assertTrue(r.forcedOverride());
        assertEquals("bob", r.forcedLeaseHolderSub());
    }

    @Test
    public void requires_token_when_caller_is_lease_holder() {
        LeasePolicyEnforcer enforcer = new LeasePolicyEnforcer();
        DatasetLeaseEntity lease = leaseHeldBy("alice", "token-alice");

        // Missing token
        LeasePolicyEnforcer.Result missing = enforcer.check(principal("alice"), Role.EDITOR, Optional.of(lease), null, false);
        assertEquals(LeasePolicyEnforcer.Outcome.CONFLICT, missing.outcome());
        assertTrue(missing.conflict() instanceof OpsConflict.LeaseTokenRequired);

        // Wrong token
        LeasePolicyEnforcer.Result wrong = enforcer.check(principal("alice"), Role.EDITOR, Optional.of(lease), "wrong", false);
        assertEquals(LeasePolicyEnforcer.Outcome.CONFLICT, wrong.outcome());
        assertTrue(wrong.conflict() instanceof OpsConflict.LeaseTokenRequired);

        // Correct token (with whitespace)
        LeasePolicyEnforcer.Result ok = enforcer.check(principal("alice"), Role.EDITOR, Optional.of(lease), "  token-alice  ", false);
        assertEquals(LeasePolicyEnforcer.Outcome.ALLOW, ok.outcome());
        assertNull(ok.conflict());
    }
}
