package info.isaksson.erland.modeller.server.security;

/**
 * Minimal identity information we expose via /me.
 *
 * - subject: the stable user identifier (OIDC 'sub')
 * - username: preferred username if present
 * - email: email claim if present
 */
public record PrincipalInfo(
        String subject,
        String username,
        String email
) { }
