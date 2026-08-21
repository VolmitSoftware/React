package art.arcane.react.api.web.relay;

import com.google.gson.JsonElement;
import java.util.Map;

public interface RelayRequestBridge {

    RelayResponse dispatch(String method, String path, Map<String, String> headers, String body);

    record RelayResponse(int status, JsonElement body) {}
}
