package info.isaksson.erland.modeller.server.api;

import info.isaksson.erland.modeller.server.security.PrincipalInfo;
import info.isaksson.erland.modeller.server.security.SecurityIdentityMapper;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Returns information about the current authenticated user.
 *
 * Phase 1: protected by OIDC (Keycloak). During tests we use quarkus-test-security.
 */
@Path("/me")
@Produces(MediaType.APPLICATION_JSON)
public class MeResource {

    @Inject
    SecurityIdentity identity;

    @GET
    @Authenticated
    public Map<String, Object> me() {
        PrincipalInfo me = SecurityIdentityMapper.toPrincipalInfo(identity);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("authenticated", identity != null && !identity.isAnonymous());
        payload.put("subject", me.subject());
        payload.put("username", me.username());
        payload.put("email", me.email());
        return payload;
    }
}
