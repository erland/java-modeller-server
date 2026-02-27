package info.isaksson.erland.modeller.server.api.dto;

import info.isaksson.erland.modeller.server.validation.ValidationIssue;

/**
 * Stable validation error item for API responses.
 */
public class ValidationErrorDto {
    public String severity;
    public String rule;
    public String path;
    public String message;

    public ValidationErrorDto() {}

    public ValidationErrorDto(String severity, String rule, String path, String message) {
        this.severity = severity;
        this.rule = rule;
        this.path = path;
        this.message = message;
    }

    public static ValidationErrorDto fromIssue(ValidationIssue issue) {
        if (issue == null) return null;
        String sev = issue.severity != null ? issue.severity.name() : null;
        return new ValidationErrorDto(sev, issue.rule, issue.path, issue.message);
    }
}
