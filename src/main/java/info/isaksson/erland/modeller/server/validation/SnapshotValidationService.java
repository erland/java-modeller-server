package info.isaksson.erland.modeller.server.validation;

import com.fasterxml.jackson.databind.JsonNode;
import info.isaksson.erland.modeller.server.domain.ValidationPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;

/**
 * Phase 2 validation pipeline: structural checks -> policy-driven checks.
 *
 * NOTE: This is intentionally conservative. "basic" and "strict" rules should be
 * expanded as the dataset schema becomes more formally specified.
 */
@ApplicationScoped
public class SnapshotValidationService {

    /**
     * Maximum snapshot payload size in bytes (UTF-8 JSON string).
     * Default is 10 MiB.
     */
    @ConfigProperty(name = "modeller.snapshot.max-bytes", defaultValue = "10485760")
    long maxBytes;

    public ValidationResult validate(JsonNode payload, String payloadJson, ValidationPolicy policy) {
        ValidationResult result = new ValidationResult();

        // ---- Structural checks (all policies) ----
        if (payload == null) {
            result.add(ValidationIssue.error("payload.required", "$", "Snapshot payload is required"));
            return result;
        }

        if (!payload.isObject()) {
            result.add(ValidationIssue.error("payload.type", "$", "Snapshot payload must be a JSON object"));
            return result;
        }

        if (payloadJson != null) {
            long bytes = payloadJson.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > maxBytes) {
                result.add(ValidationIssue.error(
                        "payload.size",
                        "$",
                        "Snapshot payload exceeds maximum size (" + maxBytes + " bytes)"
                ));
            }
        }

        // If schemaVersion is present, it must be an integer
        if (payload.has("schemaVersion") && !payload.get("schemaVersion").canConvertToInt()) {
            result.add(ValidationIssue.error(
                    "schemaVersion.type",
                    "$.schemaVersion",
                    "schemaVersion must be an integer"
            ));
        }

        // ---- Policy-driven checks ----
        ValidationPolicy effective = policy == null ? ValidationPolicy.NONE : policy;

        if (effective == ValidationPolicy.BASIC || effective == ValidationPolicy.STRICT) {
            if (!payload.has("schemaVersion")) {
                result.add(ValidationIssue.error(
                        "schemaVersion.required",
                        "$.schemaVersion",
                        "schemaVersion is required for this dataset's validation policy"
                ));
            } else if (!payload.get("schemaVersion").canConvertToInt()) {
                // already added above, but keep deterministic behavior (no-op)
            } else {
                int v = payload.get("schemaVersion").intValue();
                if (v <= 0) {
                    result.add(ValidationIssue.error(
                            "schemaVersion.range",
                            "$.schemaVersion",
                            "schemaVersion must be a positive integer"
                    ));
                }
            }
        }

        if (effective == ValidationPolicy.STRICT) {
            // Strict: minimal shape checks for known optional fields (best effort).
            if (payload.has("model") && !payload.get("model").isObject()) {
                result.add(ValidationIssue.error(
                        "model.type",
                        "$.model",
                        "model must be a JSON object when provided"
                ));
            }

            // Common keys used by many dataset snapshots; only validate type if present.
            checkArrayIfPresent(result, payload, "elements");
            checkArrayIfPresent(result, payload, "relationships");
            checkArrayIfPresent(result, payload, "views");
            checkArrayIfPresent(result, payload, "diagrams");
        }

        return result;
    }

    private static void checkArrayIfPresent(ValidationResult result, JsonNode payload, String field) {
        if (payload.has(field) && !payload.get(field).isArray()) {
            result.add(ValidationIssue.error(
                    field + ".type",
                    "$." + field,
                    field + " must be an array when provided"
            ));
        }
    }
}
