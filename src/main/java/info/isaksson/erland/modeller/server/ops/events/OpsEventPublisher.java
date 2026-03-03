package info.isaksson.erland.modeller.server.ops.events;

import info.isaksson.erland.modeller.server.ops.DatasetOpsSseHub;
import info.isaksson.erland.modeller.server.api.dto.OperationEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

import java.util.List;
import java.util.UUID;

/**
 * Publishes dataset ops-related SSE events strictly after transaction commit.
 * This ensures clients never observe events for DB changes that later roll back.
 */
@ApplicationScoped
public class OpsEventPublisher {

    private final TransactionSynchronizationRegistry txSync;
    private final DatasetOpsSseHub sseHub;

    @Inject
    public OpsEventPublisher(TransactionSynchronizationRegistry txSync, DatasetOpsSseHub sseHub) {
        this.txSync = txSync;
        this.sseHub = sseHub;
    }

    public void publishOperationsAppliedAfterCommit(UUID datasetId,
                                                    long baseRevision,
                                                    List<OperationEvent> events) {
        // DatasetOpsSseHub intentionally exposes only "ensureBaseline + publish" primitives.
        // Keep this helper for call-sites that conceptually "apply ops" as a batch.
        registerAfterCommit(() -> {
            sseHub.ensureBaseline(datasetId, baseRevision);
            events.forEach(ev -> sseHub.publish(datasetId, ev));
        });
    }

    public void publishAcceptedEventsAfterCommit(UUID datasetId,
                                               long previousRevision,
                                               List<OperationEvent> accepted) {
        registerAfterCommit(() -> {
            sseHub.ensureBaseline(datasetId, previousRevision);
            accepted.forEach(ev -> sseHub.publish(datasetId, ev));
        });
    }

    private void registerAfterCommit(Runnable action) {
        txSync.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
                // no-op
            }

            @Override
            public void afterCompletion(int status) {
                if (status == jakarta.transaction.Status.STATUS_COMMITTED) {
                    try {
                        action.run();
                    } catch (Exception ignored) {
                        // Best-effort: SSE is auxiliary. Avoid interfering with txn completion.
                    }
                }
            }
        });
    }
}
