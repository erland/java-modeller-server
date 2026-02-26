package info.isaksson.erland.modeller.server.api.error;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

import java.time.OffsetDateTime;

@Provider
public class UnhandledExceptionMapper implements ExceptionMapper<Throwable> {

    @jakarta.ws.rs.core.Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable ex) {
        String path = uriInfo != null && uriInfo.getPath() != null ? "/" + uriInfo.getPath() : null;
        String requestId = (String) MDC.get("requestId");

        ApiError err = new ApiError(
                OffsetDateTime.now(),
                500,
                "INTERNAL_ERROR",
                "Internal server error",
                path,
                requestId
        );

        return Response.status(500)
                .type(MediaType.APPLICATION_JSON)
                .entity(err)
                .build();
    }
}
