package info.isaksson.erland.modeller.server.ops.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import info.isaksson.erland.modeller.server.api.dto.OperationEvent;
import info.isaksson.erland.modeller.server.ops.DatasetOpsSseHub;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OpsEventPublisherTest {

    private static OperationEvent op(UUID datasetId, long revision, String opId, String opType) {
        JsonNode payload = NullNode.getInstance();
        return new OperationEvent(
                datasetId,
                revision,
                opId,
                opType,
                payload,
                OffsetDateTime.of(2026, 3, 3, 12, 0, 0, 0, ZoneOffset.UTC),
                "tester"
        );
    }

    @Test
    void publishes_operations_only_after_commit() {
        TransactionSynchronizationRegistry tx = mock(TransactionSynchronizationRegistry.class);
        DatasetOpsSseHub hub = mock(DatasetOpsSseHub.class);
        OpsEventPublisher publisher = new OpsEventPublisher(tx, hub);

        UUID datasetId = UUID.randomUUID();
        when(tx.getTransactionStatus()).thenReturn(Status.STATUS_ACTIVE);

        List<OperationEvent> events = List.of(
                op(datasetId, 11L, "op1", "TypeA"),
                op(datasetId, 12L, "op2", "TypeB")
        );

        ArgumentCaptor<Synchronization> sync = ArgumentCaptor.forClass(Synchronization.class);
        publisher.publishOperationsAppliedAfterCommit(datasetId, 10L, events);
        verify(tx).registerInterposedSynchronization(sync.capture());

        // Before completion should do nothing
        sync.getValue().beforeCompletion();
        verifyNoInteractions(hub);

        // After commit: baseline first, then publish events
        sync.getValue().afterCompletion(Status.STATUS_COMMITTED);
        InOrder inOrder = inOrder(hub);
        inOrder.verify(hub).ensureBaseline(eq(datasetId), eq(10L));
        inOrder.verify(hub).publish(eq(datasetId), eq(events.get(0)));
        inOrder.verify(hub).publish(eq(datasetId), eq(events.get(1)));
    }

    @Test
    void does_not_publish_on_rollback() {
        TransactionSynchronizationRegistry tx = mock(TransactionSynchronizationRegistry.class);
        DatasetOpsSseHub hub = mock(DatasetOpsSseHub.class);
        OpsEventPublisher publisher = new OpsEventPublisher(tx, hub);

        UUID datasetId = UUID.randomUUID();
        when(tx.getTransactionStatus()).thenReturn(Status.STATUS_ACTIVE);

        List<OperationEvent> events = List.of(op(datasetId, 11L, "op1", "TypeA"));

        ArgumentCaptor<Synchronization> sync = ArgumentCaptor.forClass(Synchronization.class);
        publisher.publishOperationsAppliedAfterCommit(datasetId, 10L, events);
        verify(tx).registerInterposedSynchronization(sync.capture());

        sync.getValue().afterCompletion(Status.STATUS_ROLLEDBACK);
        verifyNoInteractions(hub);
    }

    @Test
    void swallows_exceptions_from_publish_action() {
        TransactionSynchronizationRegistry tx = mock(TransactionSynchronizationRegistry.class);
        DatasetOpsSseHub hub = mock(DatasetOpsSseHub.class);

        ArgumentCaptor<Synchronization> syncCaptor = ArgumentCaptor.forClass(Synchronization.class);
        doNothing().when(tx).registerInterposedSynchronization(syncCaptor.capture());

        doThrow(new RuntimeException("boom")).when(hub).publish(any(), any(OperationEvent.class));

        OpsEventPublisher publisher = new OpsEventPublisher(tx, hub);

        UUID datasetId = UUID.randomUUID();
        publisher.publishAcceptedEventsAfterCommit(datasetId, 11L, List.of(op(datasetId, 11L, "op1", "TypeA")));

        Synchronization sync = syncCaptor.getValue();
        assertDoesNotThrow(() -> sync.afterCompletion(Status.STATUS_COMMITTED));
    }
}
