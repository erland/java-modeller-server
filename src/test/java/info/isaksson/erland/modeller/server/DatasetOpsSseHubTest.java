package info.isaksson.erland.modeller.server;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import info.isaksson.erland.modeller.server.api.dto.OperationEvent;
import info.isaksson.erland.modeller.server.ops.DatasetOpsSseHub;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DatasetOpsSseHubTest {

    @Test
    void broadcastsEventsPerDataset() throws Exception {
        DatasetOpsSseHub hub = new DatasetOpsSseHub();
        UUID datasetId = UUID.randomUUID();

        var uni = hub.stream(datasetId)
                .select().first(2)
                .collect().asList();

        var future = uni.subscribeAsCompletionStage().toCompletableFuture();

        hub.publish(datasetId, new OperationEvent(
                datasetId,
                1L,
                "op-1",
                "SNAPSHOT_REPLACE",
                JsonNodeFactory.instance.objectNode().put("k", "v"),
                OffsetDateTime.now(),
                "user-a"
        ));
        hub.publish(datasetId, new OperationEvent(
                datasetId,
                2L,
                "op-2",
                "JSON_PATCH",
                JsonNodeFactory.instance.arrayNode(),
                OffsetDateTime.now(),
                "user-a"
        ));

        List<OperationEvent> received = future.get(2, TimeUnit.SECONDS);
        assertEquals(2, received.size());
        assertEquals("op-1", received.get(0).opId());
        assertEquals("op-2", received.get(1).opId());
    }

    @Test
    void enforcesRevisionOrdering() throws Exception {
        DatasetOpsSseHub hub = new DatasetOpsSseHub();
        UUID datasetId = UUID.randomUUID();

        var uni = hub.stream(datasetId)
                .select().first(3)
                .collect().asList();
        var future = uni.subscribeAsCompletionStage().toCompletableFuture();

        // Publish out of order
        hub.publish(datasetId, new OperationEvent(datasetId, 2L, "op-2", "JSON_PATCH", JsonNodeFactory.instance.arrayNode(), OffsetDateTime.now(), "u"));
        hub.publish(datasetId, new OperationEvent(datasetId, 1L, "op-1", "JSON_PATCH", JsonNodeFactory.instance.arrayNode(), OffsetDateTime.now(), "u"));
        hub.publish(datasetId, new OperationEvent(datasetId, 3L, "op-3", "JSON_PATCH", JsonNodeFactory.instance.arrayNode(), OffsetDateTime.now(), "u"));

        List<OperationEvent> received = future.get(2, TimeUnit.SECONDS);
        assertEquals(List.of(1L, 2L, 3L), received.stream().map(OperationEvent::revision).toList());
    }
}
