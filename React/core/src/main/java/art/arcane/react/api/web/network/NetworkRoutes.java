package art.arcane.react.api.web.network;

import art.arcane.react.api.web.WebAuth;
import art.arcane.react.api.web.WebError;
import art.arcane.react.api.web.WebLog;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.pairing.WebToken;
import art.arcane.react.api.web.pairing.WebTokens;
import art.arcane.react.api.web.ws.WsAuthenticationGate;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import io.javalin.websocket.WsContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The proxy's network routes: backend registration, the network list, and every backend's own API and sockets under
 * {@code /network/<server>/}, so one pairing with the proxy reaches the whole network. The browser's token is checked
 * here; the backend sees the proxy's backend token narrowed to the browser's scopes.
 */
public final class NetworkRoutes {
  private static final Set<HandlerType> FORWARDED = Set.of(HandlerType.GET, HandlerType.POST, HandlerType.PUT,
      HandlerType.PATCH, HandlerType.DELETE);
  private static final Set<String> LOCAL_ONLY = Set.of("pair");
  private static final Duration FORWARD_TIMEOUT = Duration.ofSeconds(15);
  private static final long SOCKET_PING_MS = 15_000L;

  private final NetworkHub hub;
  private final WebTokens tokens;
  private final WebAuth auth;
  private final Supplier<String> secret;
  private final Supplier<String> proxyName;
  private final boolean requireTokenForReads;
  private final Predicate<String> originAllowed;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final Map<String, Bridge> bridges = new ConcurrentHashMap<>();

  public NetworkRoutes(NetworkHub hub, WebTokens tokens, WebAuth auth, Supplier<String> secret, Supplier<String> proxyName,
                       boolean requireTokenForReads, Predicate<String> originAllowed) {
    this.hub = hub;
    this.tokens = tokens;
    this.auth = auth;
    this.secret = secret;
    this.proxyName = proxyName;
    this.requireTokenForReads = requireTokenForReads;
    this.originAllowed = originAllowed;
  }

  public void install(Javalin javalin) {
    javalin.post(NetworkProtocol.REGISTER_PATH, this::register);
    if (requireTokenForReads) {
      javalin.before("/api/v1/network", auth);
    }
    javalin.get("/api/v1/network", (Context ctx) -> {
      WebAuth.requireScope(ctx, "read");
      ctx.json(new Envelope<>(hub.snapshot(proxyName.get())));
    });
    for (HandlerType method : FORWARDED) {
      javalin.addHttpHandler(method, "/network/{server}/api/v1/<rest>", this::forward);
    }
    socket(javalin, "metrics", "read");
    socket(javalin, "logs", "console:read");
    hub.every(SOCKET_PING_MS, () -> bridges.values().forEach(Bridge::ping));
  }

  /** Closes the bridged sockets of a token that was revoked or expired. */
  public void closeToken(String tokenId, String reason) {
    for (Bridge bridge : List.copyOf(bridges.values())) {
      if (tokenId.equals(bridge.tokenId)) {
        bridge.close(WsAuthenticationGate.closeCode(reason), reason);
      }
    }
  }

  public void close() {
    for (Bridge bridge : List.copyOf(bridges.values())) {
      bridge.close(1001, "going_away");
    }
  }

  private void register(Context ctx) {
    String sealed;
    try {
      JsonObject body = JsonParser.parseString(ctx.body()).getAsJsonObject();
      sealed = body.get("sealed").getAsString();
    } catch (RuntimeException malformed) {
      throw WebError.badRequest("Expected {\"sealed\": ...}");
    }
    String member = hub.register(secret.get(), sealed, ctx.ip());
    ctx.json(new Envelope<>(Map.of("member", member)));
  }

  private void forward(Context ctx) throws InterruptedException {
    String server = ctx.pathParam("server");
    String rest = ctx.pathParam("rest");
    if (LOCAL_ONLY.contains(rest)) {
      throw new WebError(404, "not_found", "Pair with the proxy, not through it");
    }
    List<String> scopes = browserScopes(ctx, server);
    NetworkHub.Member member = hub.member(server)
        .orElseThrow(() -> new WebError(404, "not_found", "No React backend named " + server + " is registered with this proxy"));
    String query = ctx.queryString();
    URI target = member.base().resolve("/api/v1/" + rest + (query == null || query.isEmpty() ? "" : "?" + query));
    HttpRequest.Builder request = HttpRequest.newBuilder(target)
        .timeout(FORWARD_TIMEOUT)
        .header("Authorization", "Bearer " + member.bearer())
        .header(NetworkProtocol.SCOPES_HEADER, String.join(",", scopes));
    String contentType = ctx.header("Content-Type");
    if (contentType != null) {
      request.header("Content-Type", contentType);
    }
    if (ctx.method() == HandlerType.GET) {
      request.GET();
    } else {
      request.header("X-React-Counter", Long.toString(member.nextCounter()));
      request.method(ctx.method().name(), HttpRequest.BodyPublishers.ofByteArray(ctx.bodyAsBytes()));
    }
    HttpResponse<byte[]> response;
    try {
      response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (java.io.IOException unreachable) {
      throw new WebError(502, "backend_unreachable", server + " did not answer: " + unreachable.getMessage());
    }
    ctx.status(response.statusCode());
    response.headers().firstValue("Content-Type").ifPresent(ctx::contentType);
    ctx.result(response.body());
  }

  /** The scopes the browser holds here; mutations are counted per backend, since each is its own server to the browser. */
  private List<String> browserScopes(Context ctx, String server) {
    if (ctx.header("Authorization") == null && !requireTokenForReads && ctx.method() == HandlerType.GET) {
      return List.of("read");
    }
    return auth.authenticate(ctx, "@" + server).scopes();
  }

  private void socket(Javalin javalin, String name, String scope) {
    javalin.ws("/network/{server}/ws/" + name, ws -> {
      ws.onConnect(ctx -> {
        if (!originAllowed.test(ctx.header("Origin"))) {
          ctx.closeSession(WsAuthenticationGate.CLOSE_ORIGIN_NOT_ALLOWED, "origin_not_allowed");
          return;
        }
        String query = ctx.queryString();
        bridges.put(ctx.sessionId(), new Bridge(ctx, ctx.pathParam("server"), name, scope, query == null ? "" : query));
        if (!requireTokenForReads && scope.equals("read")) {
          bridges.get(ctx.sessionId()).admit("");
        }
      });
      ws.onMessage(ctx -> {
        Bridge bridge = bridges.get(ctx.sessionId());
        if (bridge != null) {
          bridge.downstream(ctx.message());
        }
      });
      ws.onClose(ctx -> {
        Bridge bridge = bridges.remove(ctx.sessionId());
        if (bridge != null) {
          bridge.closeUpstream();
        }
      });
      ws.onError(ctx -> {
        Bridge bridge = bridges.remove(ctx.sessionId());
        if (bridge != null) {
          bridge.closeUpstream();
        }
      });
    });
  }

  /** One browser socket to a backend through the proxy: the browser authenticates here, then frames pass both ways. */
  private final class Bridge implements WebSocket.Listener {
    private final WsContext downstream;
    private final String server;
    private final String socket;
    private final String scope;
    private final String query;
    private final List<String> queued = new ArrayList<>();
    private final StringBuilder partial = new StringBuilder();
    private volatile String tokenId;
    private volatile boolean admitted;
    private volatile WebSocket upstream;

    private Bridge(WsContext downstream, String server, String socket, String scope, String query) {
      this.downstream = downstream;
      this.server = server;
      this.socket = socket;
      this.scope = scope;
      this.query = query;
    }

    private void downstream(String message) {
      if (!admitted) {
        String bearer = bearerOf(message);
        if (bearer == null) {
          if (!requireTokenForReads && scope.equals("read")) {
            return;
          }
          close(WsAuthenticationGate.CLOSE_POLICY, "Invalid authentication frame");
          return;
        }
        WebToken token;
        try {
          token = tokens.authenticate(bearer);
        } catch (WebError refused) {
          close(WsAuthenticationGate.closeCode(refused.code()), refused.code());
          return;
        }
        if (!token.hasScope(scope)) {
          close(WsAuthenticationGate.CLOSE_POLICY, "scope_missing");
          return;
        }
        admit(token.id());
        return;
      }
      WebSocket current = upstream;
      if (current == null) {
        synchronized (queued) {
          queued.add(message);
        }
        return;
      }
      current.sendText(message, true);
    }

    private void admit(String tokenId) {
      if (admitted) {
        return;
      }
      NetworkHub.Member member = hub.member(server).orElse(null);
      if (member == null) {
        close(WsAuthenticationGate.CLOSE_POLICY, "backend_unavailable");
        return;
      }
      this.tokenId = tokenId;
      admitted = true;
      URI base = member.base();
      URI target = URI.create("ws://" + base.getRawAuthority() + "/ws/" + socket + (query.isEmpty() ? "" : "?" + query));
      http.newWebSocketBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .buildAsync(target, this)
          .whenComplete((WebSocket opened, Throwable failure) -> {
            if (failure != null) {
              WebLog.verbose("Network socket to " + server + " failed", failure);
              close(1011, "backend_unreachable");
              return;
            }
            opened.sendText("{\"type\":\"auth\",\"token\":\"" + member.bearer() + "\"}", true);
            List<String> pending;
            synchronized (queued) {
              upstream = opened;
              pending = List.copyOf(queued);
              queued.clear();
            }
            pending.forEach((String text) -> opened.sendText(text, true));
          });
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      partial.append(data);
      if (last) {
        String text = partial.toString();
        partial.setLength(0);
        try {
          downstream.send(text);
        } catch (RuntimeException closed) {
          webSocket.abort();
        }
      }
      webSocket.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
      webSocket.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      close(statusCode, reason);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      close(1011, "backend_closed");
    }

    private void ping() {
      try {
        downstream.sendPing();
      } catch (RuntimeException ignored) {
      }
    }

    private void close(int code, String reason) {
      bridges.remove(downstream.sessionId());
      closeUpstream();
      try {
        downstream.closeSession(code, reason == null ? "" : reason);
      } catch (RuntimeException ignored) {
      }
    }

    private void closeUpstream() {
      WebSocket current = upstream;
      upstream = null;
      if (current != null && !current.isOutputClosed()) {
        current.sendClose(WebSocket.NORMAL_CLOSURE, "");
      }
    }
  }

  private static String bearerOf(String frame) {
    try {
      JsonObject object = JsonParser.parseString(frame).getAsJsonObject();
      if (!"auth".equals(object.get("type").getAsString())) {
        return null;
      }
      String token = object.get("token").getAsString();
      return token.isBlank() ? null : token;
    } catch (RuntimeException malformed) {
      return null;
    }
  }
}
