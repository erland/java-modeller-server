package info.isaksson.erland.modeller.server.domain;

import jakarta.ws.rs.BadRequestException;

/**
 * Deterministic metadata validation rules for Phase 1.
 *
 * Keep conservative to avoid rejecting legitimate real-world names.
 */
public final class DatasetMetadataPolicy {

    public static final int MAX_NAME_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    private DatasetMetadataPolicy() {}

    public static String normalizeAndValidateName(String name) {
        if (name == null) throw new BadRequestException("Dataset name is required");
        String n = name.trim();
        if (n.isEmpty()) throw new BadRequestException("Dataset name is required");
        if (n.length() > MAX_NAME_LENGTH) throw new BadRequestException("Dataset name is too long");
        return n;
    }

    public static String normalizeAndValidateDescription(String description) {
        if (description == null) return null;
        String d = description.trim();
        if (d.isEmpty()) return null;
        if (d.length() > MAX_DESCRIPTION_LENGTH) throw new BadRequestException("Dataset description is too long");
        return d;
    }
}
