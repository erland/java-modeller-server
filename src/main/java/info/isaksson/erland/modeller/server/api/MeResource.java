package info.isaksson.erland.modeller.server.api;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Phase 1 / Step 1 stub endpoint.
 *
 * Later steps will protect this with OIDC and return the authenticated principal.
 */
@Path("/me")
@Produces(MediaType.APPLICATION_JSON)
public class MeResource {

    @GET
    public Map<String, Object> me() {
        // NOTE: Map.of(...) does not allow null values; this endpoint intentionally returns nulls
        // until OIDC is implemented in Step 5.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("authenticated", false);
        payload.put("subject", null);
        payload.put("username", null);
        payload.put("email", null);
        return payload;
    }
}
