package info.isaksson.erland.modeller.server.domain;

import java.util.Locale;

/**
 * Dataset-level roles (Phase 1).
 *
 * Ordering (highest -> lowest): OWNER, EDITOR, VIEWER.
 */
public enum Role {
    OWNER(3),
    EDITOR(2),
    VIEWER(1);

    private final int rank;

    Role(int rank) {
        this.rank = rank;
    }

    public boolean atLeast(Role required) {
        return this.rank >= required.rank;
    }

    public static Role parse(String value) {
        if (value == null) return null;
        return Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
