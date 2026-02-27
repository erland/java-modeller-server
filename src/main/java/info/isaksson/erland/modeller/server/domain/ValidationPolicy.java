package info.isaksson.erland.modeller.server.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Dataset-level server-side validation policy.
 *
 * Phase 2 introduces this as a per-dataset configuration.
 */
public enum ValidationPolicy {
    NONE("none"),
    BASIC("basic"),
    STRICT("strict");

    private final String wire;

    ValidationPolicy(String wire) {
        this.wire = wire;
    }

    /** Lowercase value used in API and persisted in DB. */
    public String wireValue() {
        return wire;
    }

    public static Optional<ValidationPolicy> tryParse(String raw) {
        if (raw == null) return Optional.empty();
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) return Optional.empty();
        for (ValidationPolicy p : values()) {
            if (p.wire.equals(v)) return Optional.of(p);
        }
        return Optional.empty();
    }
}
