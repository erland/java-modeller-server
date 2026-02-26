package info.isaksson.erland.modeller.server.security;

import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAclRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;

import java.util.Optional;
import java.util.UUID;

/**
 * Dataset-level authorization helper for Phase 1.
 *
 * Enforcement is intentionally explicit (called from resources/services)
 * and will be wired into endpoints in Step 7+.
 */
@ApplicationScoped
public class DatasetAuthorizationService {

    private final DatasetAclRepository aclRepository;
    private final SecurityIdentity identity;

    @Inject
    public DatasetAuthorizationService(DatasetAclRepository aclRepository, SecurityIdentity identity) {
        this.aclRepository = aclRepository;
        this.identity = identity;
    }

    public PrincipalInfo currentPrincipal() {
        if (identity == null || identity.isAnonymous()) {
            throw new NotAuthorizedException("Missing authentication");
        }
        PrincipalInfo p = SecurityIdentityMapper.toPrincipalInfo(identity);
        if (p.subject() == null || p.subject().isBlank()) {
            throw new NotAuthorizedException("Missing subject (sub) in token");
        }
        return p;
    }

    public Optional<Role> roleFor(UUID datasetId) {
        PrincipalInfo principal = currentPrincipal();
        return aclRepository.findRole(datasetId, principal.subject());
    }

    public void requireAtLeast(UUID datasetId, Role required) {
        Role effective = roleFor(datasetId).orElse(null);
        if (effective == null || !effective.atLeast(required)) {
            throw new ForbiddenException("Insufficient role for dataset");
        }
    }

    public void requireViewer(UUID datasetId) { requireAtLeast(datasetId, Role.VIEWER); }
    public void requireEditor(UUID datasetId) { requireAtLeast(datasetId, Role.EDITOR); }
    public void requireOwner(UUID datasetId) { requireAtLeast(datasetId, Role.OWNER); }
}
