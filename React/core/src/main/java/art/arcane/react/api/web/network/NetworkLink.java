package art.arcane.react.api.web.network;

import art.arcane.react.api.web.WebLog;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A backend's membership in a proxy's network: registers with the proxy at once and every
 * {@link NetworkProtocol#REGISTER_INTERVAL_MS}, so the proxy keeps this server's address, port and token current.
 * Runs on its own daemon thread and never touches game state.
 */
public final class NetworkLink {
  private static final Gson GSON = new Gson();

  public enum State { CONNECTING, REGISTERED, REFUSED, UNREACHABLE }

  /** {@code member} is this server's name on the proxy once registered; {@code detail} says why it is not. */
  public record Status(String proxyUrl, State state, String member, String detail, long registeredAtMs) {
  }

  private final String proxyUrl;
  private final String secret;
  private final Supplier<NetworkRegistration> registration;
  private final HttpClient http;
  private volatile ScheduledExecutorService scheduler;
  private volatile Status status;

  public NetworkLink(String proxyUrl, String secret, Supplier<NetworkRegistration> registration) {
    this.proxyUrl = trimSlashes(Objects.requireNonNull(proxyUrl, "proxyUrl"));
    this.secret = Objects.requireNonNull(secret, "secret");
    this.registration = Objects.requireNonNull(registration, "registration");
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    this.status = new Status(this.proxyUrl, State.CONNECTING, "", "", 0L);
  }

  public synchronized void start() {
    if (scheduler != null) {
      return;
    }
    ScheduledExecutorService created = Executors.newSingleThreadScheduledExecutor((Runnable task) -> {
      Thread thread = new Thread(task, "react-network-link");
      thread.setDaemon(true);
      return thread;
    });
    scheduler = created;
    created.scheduleWithFixedDelay(this::registerSafely, 0L, NetworkProtocol.REGISTER_INTERVAL_MS, TimeUnit.MILLISECONDS);
  }

  public synchronized void stop() {
    ScheduledExecutorService current = scheduler;
    scheduler = null;
    if (current != null) {
      current.shutdownNow();
    }
  }

  public Status status() {
    return status;
  }

  private void registerSafely() {
    try {
      update(register());
    } catch (Throwable failure) {
      update(new Status(proxyUrl, State.UNREACHABLE, "", String.valueOf(failure.getMessage()), status.registeredAtMs()));
    }
  }

  Status register() throws GeneralSecurityException, InterruptedException {
    NetworkRegistration current = registration.get();
    JsonObject body = new JsonObject();
    body.addProperty("sealed", NetworkSeal.seal(secret, GSON.toJson(current)));
    HttpRequest request = HttpRequest.newBuilder(URI.create(proxyUrl + NetworkProtocol.REGISTER_PATH))
        .timeout(Duration.ofSeconds(10))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
        .build();
    HttpResponse<String> response;
    try {
      response = http.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (java.io.IOException unreachable) {
      String reason = unreachable.getMessage() == null ? unreachable.getClass().getSimpleName() : unreachable.getMessage();
      return new Status(proxyUrl, State.UNREACHABLE, "", reason, status.registeredAtMs());
    }
    if (response.statusCode() == 200) {
      return new Status(proxyUrl, State.REGISTERED, member(response.body()), "", System.currentTimeMillis());
    }
    return new Status(proxyUrl, State.REFUSED, "", refusal(response), status.registeredAtMs());
  }

  private void update(Status next) {
    Status previous = status;
    status = next;
    if (previous.state() == next.state() && previous.member().equals(next.member()) && previous.detail().equals(next.detail())) {
      return;
    }
    switch (next.state()) {
      case REGISTERED -> WebLog.info("Joined the network at " + proxyUrl + " as " + next.member());
      case REFUSED -> WebLog.warn("The network proxy at " + proxyUrl + " refused this server: " + next.detail());
      case UNREACHABLE -> WebLog.warn("Could not reach the network proxy at " + proxyUrl + ": " + next.detail());
      case CONNECTING -> {
      }
    }
  }

  private static String member(String body) {
    try {
      JsonElement data = JsonParser.parseString(body).getAsJsonObject().get("data");
      return data != null && data.isJsonObject() && data.getAsJsonObject().has("member")
          ? data.getAsJsonObject().get("member").getAsString() : "";
    } catch (RuntimeException malformed) {
      return "";
    }
  }

  private static String refusal(HttpResponse<String> response) {
    try {
      JsonObject error = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("error");
      if (error != null && error.has("message")) {
        return error.get("message").getAsString();
      }
    } catch (RuntimeException ignored) {
    }
    return "HTTP " + response.statusCode();
  }

  private static String trimSlashes(String url) {
    String trimmed = url.strip();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
