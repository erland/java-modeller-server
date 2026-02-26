package info.isaksson.erland.modeller.server.security;

import io.quarkus.security.identity.SecurityIdentity;

import java.util.Objects;

/**
 * Maps Quarkus SecurityIdentity to our minimal PrincipalInfo.
 *
 * NOTE: Claim availability depends on Keycloak client / token mapper configuration.
 */
public final class SecurityIdentityMapper {

    private SecurityIdentityMapper() {}

    public static PrincipalInfo toPrincipalInfo(SecurityIdentity identity) {
        if (identity == null || identity.isAnonymous()) {
            return new PrincipalInfo(null, null, null);
        }

        // By default, Quarkus sets principal name to the token principal claim (often 'sub').
        String subject = safeString(identity.getPrincipal() != null ? identity.getPrincipal().getName() : null);

        // Common Keycloak claims - may be absent depending on realm/client configuration.
        String preferredUsername = safeString(identity.getAttribute("preferred_username"));
        String email = safeString(identity.getAttribute("email"));

        // Fall back to other typical claim names if needed.
        if (preferredUsername == null) {
            preferredUsername = safeString(identity.getAttribute("username"));
        }

        return new PrincipalInfo(subject, preferredUsername, email);
    }

    private static String safeString(Object value) {
        if (value == null) return null;
        String s = Objects.toString(value, null);
        return (s == null || s.isBlank()) ? null : s;
    }
}
