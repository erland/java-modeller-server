package info.isaksson.erland.modeller.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import info.isaksson.erland.modeller.server.api.dto.AppendOperationsRequest;
import info.isaksson.erland.modeller.server.api.dto.AppendOperationsResponse;
import info.isaksson.erland.modeller.server.api.dto.OperationEvent;
import info.isaksson.erland.modeller.server.api.dto.OperationRequest;
import info.isaksson.erland.modeller.server.api.dto.OpsSinceResponse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class Phase3DtosTest {

    @Inject ObjectMapper objectMapper;

    @Test
    void operationDtos_areJsonSerializable() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"k\":\"v\",\"n\":123}");

        AppendOperationsRequest req = new AppendOperationsRequest();
        req.baseRevision = 7L;
        req.operations = List.of(new OperationRequest("op-1", "TEST", payload));

        String json = objectMapper.writeValueAsString(req);
        assertTrue(json.contains("\"baseRevision\":7"));
        assertTrue(json.contains("\"opId\":\"op-1\""));

        AppendOperationsRequest roundTrip = objectMapper.readValue(json, AppendOperationsRequest.class);
        assertEquals(7L, roundTrip.baseRevision);
        assertNotNull(roundTrip.operations);
        assertEquals(1, roundTrip.operations.size());
        assertEquals("op-1", roundTrip.operations.get(0).opId);
        assertEquals("TEST", roundTrip.operations.get(0).type);
        assertEquals(payload, roundTrip.operations.get(0).payload);

        UUID datasetId = UUID.randomUUID();
        OperationEvent ev = new OperationEvent(datasetId, 8L, "op-1", "TEST", payload, OffsetDateTime.now(), "user-1");
        AppendOperationsResponse resp = new AppendOperationsResponse(8L, List.of(ev));
        String respJson = objectMapper.writeValueAsString(resp);
        assertTrue(respJson.contains("\"newRevision\":8"));
        assertTrue(respJson.contains(datasetId.toString()));

        OpsSinceResponse opsSince = new OpsSinceResponse(7L, 8L, List.of(ev));
        String sinceJson = objectMapper.writeValueAsString(opsSince);
        assertTrue(sinceJson.contains("\"fromRevision\":7"));
        assertTrue(sinceJson.contains("\"toRevision\":8"));
    }
}
