package info.isaksson.erland.modeller.server.ops;

import info.isaksson.erland.modeller.server.api.dto.OperationEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-dataset SSE hub.
 *
 * <p>Phase 3 scope: single-node fan-out and ordering guarantees (per dataset, per node).
 * This component is intentionally in-memory; clustered fan-out is out of scope for Phase 3.</p>
 */
@ApplicationScoped
public class DatasetOpsSseHub {

    private final Map<UUID, Channel> channels = new ConcurrentHashMap<>();

    public Multi<OperationEvent> stream(UUID datasetId) {
        return channels.computeIfAbsent(datasetId, id -> new Channel()).processor;
    }

    /**
     * Ensures the hub knows the current committed head revision for the dataset.
     * This prevents buffering forever when the first published revision is far from 1
     * (e.g., dataset already existed with head revision 100 and the next op is 101).
     *
     * <p>Safe to call repeatedly; the baseline only moves forward.</p>
     */
    public void ensureBaseline(UUID datasetId, long currentHeadRevision) {
        channels.computeIfAbsent(datasetId, id -> new Channel()).ensureBaseline(currentHeadRevision);
    }

    public void publish(UUID datasetId, OperationEvent event) {
        channels.computeIfAbsent(datasetId, id -> new Channel()).publish(event);
    }

    static final class Channel {
        final BroadcastProcessor<OperationEvent> processor = BroadcastProcessor.create();
        final TreeMap<Long, OperationEvent> buffer = new TreeMap<>();

        long lastEmittedRevision = 0L;

        synchronized void ensureBaseline(long currentHeadRevision) {
            if (currentHeadRevision > lastEmittedRevision) {
                lastEmittedRevision = currentHeadRevision;
            }
            drain();
        }

        synchronized void publish(OperationEvent event) {
            if (event == null) return;

            long rev = event.revision();
            // Drop duplicates / old events (can happen on retries).
            if (rev <= lastEmittedRevision) {
                return;
            }
            buffer.put(rev, event);
            drain();
        }

        private void drain() {
            while (true) {
                long next = lastEmittedRevision + 1L;
                OperationEvent ev = buffer.get(next);
                if (ev == null) {
                    return;
                }
                buffer.remove(next);
                lastEmittedRevision = next;
                processor.onNext(ev);
            }
        }
    }
}
