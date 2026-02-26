package info.isaksson.erland.modeller.server.api.error;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

import java.time.OffsetDateTime;

@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @jakarta.ws.rs.core.Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(WebApplicationException ex) {
        Response original = ex.getResponse();
        int status = original != null ? original.getStatus() : 500;

        // If a resource already returned a JSON entity (like our snapshot conflict response),
        // do not wrap it again.
        Object entity = original != null ? original.getEntity() : null;
        if (entity != null) {
            return original;
        }

        String path = uriInfo != null && uriInfo.getPath() != null ? "/" + uriInfo.getPath() : null;
        String requestId = (String) MDC.get("requestId");

        ApiError err = new ApiError(
                OffsetDateTime.now(),
                status,
                codeForStatus(status),
                safeMessage(ex, status),
                path,
                requestId
        );

        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(err)
                .build();
    }

    static String codeForStatus(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 409 -> "CONFLICT";
            case 412 -> "PRECONDITION_FAILED";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 422 -> "UNPROCESSABLE_ENTITY";
            case 428 -> "PRECONDITION_REQUIRED";
            default -> (status >= 500 ? "INTERNAL_ERROR" : "ERROR");
        };
    }

    static String safeMessage(Throwable ex, int status) {
        String msg = ex != null ? ex.getMessage() : null;
        if (msg == null || msg.isBlank()) {
            return status >= 500 ? "Internal server error" : "Request failed";
        }
        return msg;
    }
}
