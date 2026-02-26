package info.isaksson.erland.modeller.server.api.error;

import java.time.OffsetDateTime;

/**
 * Stable JSON error envelope for Phase 1.
 *
 * Keep this intentionally small and forwards-compatible.
 */
public class ApiError {
    public OffsetDateTime timestamp;
    public int status;
    public String code;
    public String message;
    public String path;
    public String requestId;

    public ApiError() {}

    public ApiError(OffsetDateTime timestamp, int status, String code, String message, String path, String requestId) {
        this.timestamp = timestamp;
        this.status = status;
        this.code = code;
        this.message = message;
        this.path = path;
        this.requestId = requestId;
    }
}
