package info.isaksson.erland.modeller.server.ops.policy;

import info.isaksson.erland.modeller.server.persistence.entities.DatasetEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/**
 * Centralizes revision concurrency semantics for dataset writes:
 * - lock dataset row
 * - refresh revision after acquiring lock
 * - compare expected baseRevision
 * - compute the new revision
 */
@ApplicationScoped
public class RevisionGate {

    private final EntityManager em;

    @Inject
    public RevisionGate(EntityManager em) {
        this.em = em;
    }

    public Result lockCompareAndAdvance(DatasetEntity dataset, long expectedBaseRevision, int incrementBy) {
        if (incrementBy < 0) {
            throw new IllegalArgumentException("incrementBy must be >= 0");
        }

        // Concurrency safety: lock dataset row for the duration of revision assignment
        em.lock(dataset, LockModeType.PESSIMISTIC_WRITE);

        // Refresh the revision after acquiring the lock to be sure we compare against the latest committed value.
        em.refresh(dataset);

        long currentRevision = dataset.currentRevision;
        if (expectedBaseRevision != currentRevision) {
            return new Conflict(expectedBaseRevision, currentRevision);
        }

        long newRevision = currentRevision + incrementBy;
        return new Ok(currentRevision, newRevision);
    }

    public sealed interface Result permits Ok, Conflict {}

    public record Ok(long previousRevision, long newRevision) implements Result {}

    public record Conflict(long expectedBaseRevision, long currentRevision) implements Result {}
}
