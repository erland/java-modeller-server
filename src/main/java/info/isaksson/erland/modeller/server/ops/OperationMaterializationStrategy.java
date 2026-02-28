package info.isaksson.erland.modeller.server.ops;

/**
 * Phase 3 (operations): server-side state materialization strategy.
 *
 * <p>We choose Strategy A from docs/phase3-step-by-step-plan-A.md:
 * keep storing full snapshots as today. When operations are appended,
 * the server applies them sequentially to the latest snapshot to produce
 * a new snapshot state, then persists:
 * <ul>
 *   <li>operation rows (append-only) with assigned revisions</li>
 *   <li>snapshot_latest updated to the new revision</li>
 *   <li>snapshot_history row for the new revision (and prunes old rows)</li>
 * </ul>
 */
public enum OperationMaterializationStrategy {
    FULL_SNAPSHOT_ON_APPEND
}
