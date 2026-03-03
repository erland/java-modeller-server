package info.isaksson.erland.modeller.server.ops.materialize;

import com.fasterxml.jackson.databind.JsonNode;
import info.isaksson.erland.modeller.server.api.dto.OperationRequest;
import info.isaksson.erland.modeller.server.domain.ValidationPolicy;
import info.isaksson.erland.modeller.server.ops.OperationsApplier;
import info.isaksson.erland.modeller.server.validation.SnapshotValidationService;
import info.isaksson.erland.modeller.server.validation.ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Materializes the next snapshot from a base snapshot and a list of operations,
 * applying the dataset's validation policy.
 *
 * This is intentionally REST- and persistence-free: callers supply the base snapshot and ops,
 * and receive either a materialized payload or validation errors.
 */
@ApplicationScoped
public class SnapshotMaterializer {

    public sealed interface Result permits Ok, ValidationFailed {
    }

    public record Ok(JsonNode snapshot, String payloadJson) implements Result {
    }

    public record ValidationFailed(ValidationResult validationResult) implements Result {
    }

    private final OperationsApplier applier;
    private final SnapshotValidationService validationService;

    @Inject
    public SnapshotMaterializer(OperationsApplier applier,
                                SnapshotValidationService validationService) {
        this.applier = applier;
        this.validationService = validationService;
    }

    public Result materialize(JsonNode baseSnapshot,
                              List<OperationRequest> ops,
                              ValidationPolicy policy) {
        JsonNode nextSnapshot = applier.applyAll(baseSnapshot, ops);
        String payloadJson = nextSnapshot.toString();

        ValidationPolicy effective = policy == null ? ValidationPolicy.NONE : policy;
        ValidationResult vr = validationService.validate(nextSnapshot, payloadJson, effective);
        if (vr != null && vr.hasErrors()) {
            return new ValidationFailed(vr);
        }
        return new Ok(nextSnapshot, payloadJson);
    }
}
