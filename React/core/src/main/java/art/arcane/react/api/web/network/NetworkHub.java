package art.arcane.react.api.web.network;

import art.arcane.react.api.web.WebError;
import art.arcane.react.api.web.WebLog;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * React's view of a proxy's network: every server the proxy lists, pinged every few seconds, and the backends running
 * React, which register themselves and whose live metrics this hub reads every two seconds.
 */
public final class NetworkHub {
  private static final Gson GSON = new Gson();
  private static final long METRICS_INTERVAL_MS = 2_000L;
  private static final long PING_INTERVAL_MS = 5_000L;
  private static final Duration BACKEND_TIMEOUT = Duration.ofSeconds(4);

  private final NetworkView view;
  private final LongSupplier clock;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final Map<String, Member> members = new ConcurrentHashMap<>();
  private final Map<String, Ping> pings = new ConcurrentHashMap<>();
  private volatile ScheduledExecutorService scheduler;

  public NetworkHub(NetworkView view) {
    this(view, System::currentTimeMillis);
  }

  NetworkHub(NetworkView view, LongSupplier clock) {
    this.view = Objects.requireNonNull(view, "view");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public NetworkView view() {
    return view;
  }

  public synchronized void start() {
    if (scheduler != null) {
      return;
    }
    ScheduledExecutorService created = Executors.newScheduledThreadPool(2, (Runnable task) -> {
      Thread thread = new Thread(task, "react-network-hub");
      thread.setDaemon(true);
      return thread;
    });
    scheduler = created;
    created.scheduleWithFixedDelay(this::pingAll, 0L, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    created.scheduleWithFixedDelay(this::readMetrics, METRICS_INTERVAL_MS, METRICS_INTERVAL_MS, TimeUnit.MILLISECONDS);
  }

  public synchronized void stop() {
    ScheduledExecutorService current = scheduler;
    scheduler = null;
    if (current != null) {
      current.shutdownNow();
    }
  }

  /** Runs {@code task} every {@code periodMs} on the hub's threads; empty when the hub is stopped. */
  public Optional<ScheduledFuture<?>> every(long periodMs, Runnable task) {
    ScheduledExecutorService current = scheduler;
    if (current == null) {
      return Optional.empty();
    }
    return Optional.of(current.scheduleWithFixedDelay(task, periodMs, periodMs, TimeUnit.MILLISECONDS));
  }

  /**
   * Opens a backend's sealed registration from {@code remoteHost} and records it; the backend's name in the proxy's
   * list. Refuses with 401 for a wrong secret or a stale clock, and 404 when no listed server matches.
   */
  public String register(String secret, String sealed, String remoteHost) {
    NetworkRegistration registration;
    try {
      registration = GSON.fromJson(NetworkSeal.open(secret, sealed), NetworkRegistration.class);
    } catch (GeneralSecurityException | JsonParseException refused) {
      throw new WebError(401, "unauthorized", "The registration was not sealed with this proxy's networkSecret");
    }
    if (registration == null || registration.bearer() == null || registration.bearer().isBlank() || registration.webPort() < 1
        || registration.webPort() > 65535) {
      throw WebError.badRequest("Incomplete registration");
    }
    long now = clock.getAsLong();
    if (Math.abs(now - registration.sentAtMs()) > NetworkProtocol.MAXIMUM_CLOCK_SKEW_MS) {
      throw new WebError(401, "unauthorized", "The backend's clock is more than two minutes off the proxy's");
    }
    String name = match(registration, remoteHost)
        .orElseThrow(() -> new WebError(404, "not_found", registration.serverName() == null || registration.serverName().isBlank()
            ? "No server in the proxy's list uses port " + registration.gamePort() + "; set networkServerName in the backend's web.toml"
            : "The proxy lists no server named " + registration.serverName()));
    Member previous = members.get(name);
    URI base = URI.create("http://" + (remoteHost.indexOf(':') >= 0 ? "[" + remoteHost + "]" : remoteHost) + ":" + registration.webPort());
    Member member = previous != null && previous.base.equals(base) && previous.registration.bearer().equals(registration.bearer())
        ? previous.renewed(registration, now)
        : new Member(registration, base, now, previous == null ? null : previous.stats);
    members.put(name, member);
    if (previous == null || !previous.base.equals(base)) {
      WebLog.info("Network backend " + name + " registered from " + base);
    }
    return name;
  }

  Optional<String> match(NetworkRegistration registration, String remoteHost) {
    List<NetworkView.Server> servers = view.servers();
    String wanted = registration.serverName() == null ? "" : registration.serverName().strip();
    if (!wanted.isEmpty()) {
      for (NetworkView.Server server : servers) {
        if (server.name().equalsIgnoreCase(wanted)) {
          return Optional.of(server.name());
        }
      }
      return Optional.empty();
    }
    List<NetworkView.Server> onPort = new ArrayList<>();
    for (NetworkView.Server server : servers) {
      if (server.port() == registration.gamePort()) {
        onPort.add(server);
      }
    }
    if (onPort.size() == 1) {
      return Optional.of(onPort.get(0).name());
    }
    for (NetworkView.Server server : onPort) {
      if (sameHost(server.host(), remoteHost)) {
        return Optional.of(server.name());
      }
    }
    return Optional.empty();
  }

  /** A backend running React that registered within {@link NetworkProtocol#MEMBER_TIMEOUT_MS}. */
  public Optional<Member> member(String name) {
    Member member = members.get(name);
    if (member == null || clock.getAsLong() - member.seenAtMs > NetworkProtocol.MEMBER_TIMEOUT_MS) {
      return Optional.empty();
    }
    return Optional.of(member);
  }

  public NetworkDto snapshot(String proxyName) {
    List<ServerDto> servers = new ArrayList<>();
    int online = 0;
    int react = 0;
    for (NetworkView.Server server : view.servers()) {
      Ping ping = pings.get(server.name());
      Member member = member(server.name()).orElse(null);
      Stats stats = member == null ? null : member.stats;
      boolean up = member != null || (ping != null && ping.latencyMs >= 0);
      String status = up ? "online" : ping == null ? "unknown" : "offline";
      if (up) {
        online++;
      }
      if (member != null) {
        react++;
      }
      NetworkRegistration registration = member == null ? null : member.registration;
      servers.add(new ServerDto(server.name(), server.address(), status, server.players(),
          ping == null || ping.latencyMs < 0 ? null : ping.latencyMs, member != null,
          stats == null ? null : stats.tps, stats == null ? null : stats.mspt,
          stats == null ? null : stats.memoryUsedBytes, stats == null ? null : stats.memoryMaxBytes,
          registration == null ? null : registration.platform(), registration == null ? null : registration.loader(),
          registration == null ? null : registration.version(), member == null ? null : member.seenAtMs,
          NetworkProtocol.SERVERS_PATH + "/" + URLEncoder.encode(server.name(), StandardCharsets.UTF_8).replace("+", "%20")));
    }
    ProxyDto proxy = new ProxyDto(proxyName, view.kind(), view.version(), view.onlinePlayers(), servers.size(), online, react);
    return new NetworkDto(proxy, servers);
  }

  private void pingAll() {
    try {
      for (NetworkView.Server server : view.servers()) {
        view.ping(server.name()).orTimeout(BACKEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            .whenComplete((OptionalLong latency, Throwable failure) -> pings.put(server.name(),
                new Ping(failure == null && latency.isPresent() ? latency.getAsLong() : -1L, clock.getAsLong())));
      }
      pings.keySet().removeIf((String name) -> view.servers().stream().noneMatch((NetworkView.Server server) -> server.name().equals(name)));
    } catch (RuntimeException failure) {
      WebLog.verbose("Network ping failed", failure);
    }
  }

  private void readMetrics() {
    for (Map.Entry<String, Member> entry : members.entrySet()) {
      Member member = entry.getValue();
      if (clock.getAsLong() - member.seenAtMs > NetworkProtocol.MEMBER_TIMEOUT_MS) {
        continue;
      }
      HttpRequest request = HttpRequest.newBuilder(member.base.resolve("/api/v1/metrics?compact=1"))
          .timeout(BACKEND_TIMEOUT)
          .header("Authorization", "Bearer " + member.registration.bearer())
          .GET()
          .build();
      http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((HttpResponse<String> response, Throwable failure) -> {
        if (failure == null && response.statusCode() == 200) {
          member.stats = stats(response.body(), clock.getAsLong());
        }
      });
    }
  }

  static Stats stats(String body, long now) {
    Double tps = null;
    Double mspt = null;
    Long used = null;
    Long free = null;
    try {
      JsonObject data = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("data");
      JsonArray samplers = data == null ? null : data.getAsJsonArray("samplers");
      if (samplers != null) {
        for (JsonElement element : samplers) {
          JsonObject sample = element.getAsJsonObject();
          if (!sample.has("id") || !sample.has("value")) {
            continue;
          }
          double value = sample.get("value").getAsDouble();
          switch (sample.get("id").getAsString()) {
            case "ticks-per-second" -> tps = value;
            case "tick-time" -> mspt = value;
            case "memory-used" -> used = Math.round(value);
            case "memory-free" -> free = Math.round(value);
            default -> {
            }
          }
        }
      }
    } catch (RuntimeException malformed) {
      return new Stats(null, null, null, null, now);
    }
    return new Stats(tps, mspt, used, used == null || free == null ? null : used + free, now);
  }

  static boolean sameHost(String listed, String remote) {
    try {
      InetAddress listedAddress = InetAddress.getByName(listed);
      InetAddress remoteAddress = InetAddress.getByName(remote);
      return listedAddress.equals(remoteAddress) || listedAddress.isLoopbackAddress() && remoteAddress.isLoopbackAddress();
    } catch (UnknownHostException unknown) {
      return false;
    }
  }

  /** A registered backend: where to reach it, the token to use, and its latest metrics. */
  public static final class Member {
    private final NetworkRegistration registration;
    private final URI base;
    private final long seenAtMs;
    private final AtomicLong counter;
    private volatile Stats stats;

    private Member(NetworkRegistration registration, URI base, long seenAtMs, Stats stats) {
      this(registration, base, seenAtMs, stats, new AtomicLong(seenAtMs * 1000L));
    }

    private Member(NetworkRegistration registration, URI base, long seenAtMs, Stats stats, AtomicLong counter) {
      this.registration = registration;
      this.base = base;
      this.seenAtMs = seenAtMs;
      this.stats = stats;
      this.counter = counter;
    }

    private Member renewed(NetworkRegistration next, long now) {
      return new Member(next, base, now, stats, counter);
    }

    public URI base() {
      return base;
    }

    public String bearer() {
      return registration.bearer();
    }

    public NetworkRegistration registration() {
      return registration;
    }

    /** The next {@code X-React-Counter} for a request the proxy forwards to this backend. */
    public long nextCounter() {
      return counter.incrementAndGet();
    }
  }

  record Stats(Double tps, Double mspt, Long memoryUsedBytes, Long memoryMaxBytes, long sampledAtMs) {
  }

  private record Ping(long latencyMs, long atMs) {
  }

  public record NetworkDto(ProxyDto proxy, List<ServerDto> servers) {
  }

  public record ProxyDto(String name, String kind, String version, int players, int servers, int online, int react) {
  }

  public record ServerDto(String name, String address, String status, int players, Long pingMs, boolean react, Double tps,
                          Double mspt, Long memoryUsedBytes, Long memoryMaxBytes, String platform, String loader,
                          String version, Long lastSeenMs, String basePath) {
  }
}
