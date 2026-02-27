package info.isaksson.erland.modeller.server.validation;

/**
 * A single validation issue.
 *
 * path: JSON pointer-like path (best effort), e.g. "$.schemaVersion"
 * rule: stable identifier, e.g. "schemaVersion.required"
 */
public class ValidationIssue {
    public ValidationSeverity severity;
    public String rule;
    public String path;
    public String message;

    public ValidationIssue() {}

    public ValidationIssue(ValidationSeverity severity, String rule, String path, String message) {
        this.severity = severity;
        this.rule = rule;
        this.path = path;
        this.message = message;
    }

    public static ValidationIssue error(String rule, String path, String message) {
        return new ValidationIssue(ValidationSeverity.ERROR, rule, path, message);
    }

    public static ValidationIssue warning(String rule, String path, String message) {
        return new ValidationIssue(ValidationSeverity.WARNING, rule, path, message);
    }
}
