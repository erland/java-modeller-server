package info.isaksson.erland.modeller.server.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidationResult {
    private final List<ValidationIssue> issues = new ArrayList<>();

    public void add(ValidationIssue issue) {
        if (issue != null) issues.add(issue);
    }

    public void addAll(List<ValidationIssue> more) {
        if (more != null) issues.addAll(more);
    }

    public List<ValidationIssue> issues() {
        return Collections.unmodifiableList(issues);
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i != null && i.severity == ValidationSeverity.ERROR);
    }

    public List<ValidationIssue> errors() {
        return issues.stream().filter(i -> i != null && i.severity == ValidationSeverity.ERROR).toList();
    }

    public List<ValidationIssue> warnings() {
        return issues.stream().filter(i -> i != null && i.severity == ValidationSeverity.WARNING).toList();
    }
}
