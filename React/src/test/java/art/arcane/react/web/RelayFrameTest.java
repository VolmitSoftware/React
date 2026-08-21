package art.arcane.react.web;

import art.arcane.react.api.web.relay.RelayFrame;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RelayFrameTest {

    @Test
    void roundTripsAllFields() {
        JsonObject headers = new JsonObject();
        headers.addProperty("Authorization", "Bearer x");

        JsonObject payload = new JsonObject();
        payload.addProperty("method", "GET");
        payload.add("headers", headers);

        RelayFrame original = new RelayFrame("route", "fp", "r1", payload);
        String json = original.toJson();
        RelayFrame parsed = RelayFrame.parse(json);

        assertEquals("route", parsed.type());
        assertEquals("fp", parsed.serverId());
        assertEquals("r1", parsed.requestId());
        assertNotNull(parsed.payload());
        assertEquals("GET", parsed.payload().get("method").getAsString());
        assertEquals("Bearer x", parsed.payload().getAsJsonObject("headers").get("Authorization").getAsString());
    }

    @Test
    void parseIgnoresUnknownTopLevelKeys() {
        String json = "{\"type\":\"data\",\"serverId\":\"s1\",\"requestId\":\"req9\",\"payload\":{\"key\":\"val\"},\"foo\":1}";
        RelayFrame parsed = RelayFrame.parse(json);
        assertEquals("data", parsed.type());
        assertEquals("s1", parsed.serverId());
        assertEquals("req9", parsed.requestId());
        assertNotNull(parsed.payload());
        assertEquals("val", parsed.payload().get("key").getAsString());
    }

    @Test
    void parseHandlesMissingPayload() {
        String json = "{\"type\":\"register\",\"serverId\":\"s2\",\"requestId\":\"req0\"}";
        RelayFrame parsed = RelayFrame.parse(json);
        assertEquals("register", parsed.type());
        assertEquals("s2", parsed.serverId());
        assertEquals("req0", parsed.requestId());
        assertNull(parsed.payload());
    }
}
