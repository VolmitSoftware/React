package art.arcane.react.api.web.relay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class RelayLoopbackBridge implements RelayRequestBridge {

    private final String baseUrl;
    private final HttpClient client;

    public RelayLoopbackBridge(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = HttpClient.newHttpClient();
    }

    @Override
    public RelayResponse dispatch(String method, String path, Map<String, String> headers, String body) {
        try {
            String upperMethod = method.toUpperCase();
            boolean hasBodyContent = body != null && !body.isEmpty();
            boolean isBodylessMethod = upperMethod.equals("GET") || upperMethod.equals("DELETE");
            HttpRequest.BodyPublisher publisher = (hasBodyContent && !isBodylessMethod)
                ? HttpRequest.BodyPublishers.ofString(body)
                : HttpRequest.BodyPublishers.noBody();

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .method(upperMethod, publisher);

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            JsonElement parsedBody = parseBody(response.body());
            return new RelayResponse(status, parsedBody);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new RelayResponse(502, buildError(msg));
        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new RelayResponse(502, buildError(msg));
        }
    }

    private JsonElement parseBody(String raw) {
        if (raw == null || raw.isEmpty()) {
            return buildError("non-json response");
        }
        try {
            return JsonParser.parseString(raw);
        } catch (com.google.gson.JsonParseException e) {
            return buildError("non-json response");
        }
    }

    private JsonObject buildError(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("message", message);
        JsonObject wrapper = new JsonObject();
        wrapper.add("error", error);
        return wrapper;
    }
}
