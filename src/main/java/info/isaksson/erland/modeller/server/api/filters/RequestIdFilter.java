package info.isaksson.erland.modeller.server.api.filters;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds request correlation headers and makes requestId available to exception mappers.
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_API_VERSION = "X-API-Version";
    public static final String CTX_REQUEST_ID = "requestId";

    // Phase 1: hardcoded stable API version.
    private static final String API_VERSION = "1";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String rid = requestContext.getHeaderString(HEADER_REQUEST_ID);
        if (rid == null || rid.isBlank()) {
            rid = UUID.randomUUID().toString();
        }
        requestContext.setProperty(CTX_REQUEST_ID, rid);
        MDC.put("requestId", rid);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        Object rid = requestContext.getProperty(CTX_REQUEST_ID);
        if (rid instanceof String s && !s.isBlank()) {
            responseContext.getHeaders().putSingle(HEADER_REQUEST_ID, s);
        }
        responseContext.getHeaders().putSingle(HEADER_API_VERSION, API_VERSION);

        // Ensure we don't leak MDC between requests (esp. in tests).
        MDC.remove("requestId");
    }
}
