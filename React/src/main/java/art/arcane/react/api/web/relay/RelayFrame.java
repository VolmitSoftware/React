package art.arcane.react.api.web.relay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public record RelayFrame(String type, String serverId, String requestId, JsonObject payload) {

    public static RelayFrame parse(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        String type = obj.has("type") && !obj.get("type").isJsonNull() ? obj.get("type").getAsString() : null;
        String serverId = obj.has("serverId") && !obj.get("serverId").isJsonNull() ? obj.get("serverId").getAsString() : null;
        String requestId = obj.has("requestId") && !obj.get("requestId").isJsonNull() ? obj.get("requestId").getAsString() : null;
        JsonObject payload = obj.has("payload") && obj.get("payload").isJsonObject() ? obj.getAsJsonObject("payload") : null;
        return new RelayFrame(type, serverId, requestId, payload);
    }

    public String toJson() {
        JsonObject obj = new JsonObject();
        if (type != null) {
            obj.addProperty("type", type);
        }
        if (serverId != null) {
            obj.addProperty("serverId", serverId);
        }
        if (requestId != null) {
            obj.addProperty("requestId", requestId);
        }
        if (payload != null) {
            obj.add("payload", payload);
        }
        return obj.toString();
    }
}
