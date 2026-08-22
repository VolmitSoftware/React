package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.integration.IntegrationMetricKeySelector;
import art.arcane.react.core.integration.ReactIntegrationService;
import art.arcane.react.core.integration.ReflectiveIntegrationProviderAdapter;
import art.arcane.react.core.integration.RemoteSamplerBridge;
import art.arcane.react.core.integration.ThirdPartyMetricRegistry;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.plugin.IController;
import art.arcane.volmlib.integration.IntegrationHandshakeResponse;
import art.arcane.volmlib.integration.IntegrationHeartbeat;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.integration.IntegrationProtocolVersion;
import art.arcane.volmlib.integration.IntegrationServiceContract;
import art.arcane.volmlib.util.format.Form;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = true)
@Getter
public class IntegrationController extends TickedObject implements IController {
  private static final long DISCOVERY_INTERVAL_MS = 5_000L;
  private static final long EXPECTED_KEY_REFRESH_MS = 5_000L;
  private static final long HANDSHAKE_RETRY_MS = 10_000L;
  private static final long STALE_HEARTBEAT_TIMEOUT_MS = 15_000L;
  private static final long MAX_FUTURE_HEARTBEAT_MS = 5_000L;
  private static final long CORRELATION_COOLDOWN_MS = 10_000L;
  private static final long TIMELINE_RETENTION_MS = 180_000L;
  private static final double ADAPT_SESSION_LOAD_THRESHOLD = 65D;
  private static final double ADAPT_ABILITY_TIMING_BUDGET_THRESHOLD = 100D;
  private static final int ADAPT_ABILITY_TIMING_SUSTAINED_SAMPLES = 3;
  private static final double ADAPT_ABILITY_TIMING_MSPT_GATE = 50D;
  private static final Set<String> PRIMARY_PLUGINS = Set.of("iris", "adapt", "wormholes");

  private final transient AtomicBoolean syncTickQueued = new AtomicBoolean(false);
  private final transient Map<String, IntegrationNodeState> nodes = new ConcurrentHashMap<>();
  private final transient Deque<TimelineEvent> timeline = new ConcurrentLinkedDeque<>();
  private final transient Map<String, Boolean> activeThresholds = new ConcurrentHashMap<>();
  private transient ReactIntegrationService localService;
  private transient RemoteSamplerBridge remoteSamplerBridge;
  private transient ThirdPartyMetricRegistry thirdPartyMetrics;
  private transient long lastDiscoveryMs;
  private transient long lastCorrelationLogMs;
  private transient double previousIrisQueue;
  private transient String lastCorrelationMessage;
  private transient int adaptAbilityTimingImpactSampleStreak;

  public IntegrationController() {
    super("react", "integration", 1000);
  }

  @Override
  public String getName() {
    return "Integration";
  }

  @Override
  public String getId() {
    return "integration";
  }

  @Override
  public void start() {
    remoteSamplerBridge = new RemoteSamplerBridge();
    thirdPartyMetrics = new ThirdPartyMetricRegistry();
    thirdPartyMetrics.install();
    localService = new ReactIntegrationService();
    localService.register();
    lastDiscoveryMs = 0L;
    lastCorrelationLogMs = 0L;
    previousIrisQueue = -1D;
    lastCorrelationMessage = "";
    adaptAbilityTimingImpactSampleStreak = 0;
    nodes.clear();
    timeline.clear();
    activeThresholds.clear();
    logLifecycle("discover", "react", "provider-registered");
  }

  @Override
  public void stop() {
    if (localService != null) {
      localService.unregister();
    }
    if (thirdPartyMetrics != null) {
      thirdPartyMetrics.uninstall();
      thirdPartyMetrics = null;
    }
    nodes.clear();
    timeline.clear();
    activeThresholds.clear();
    adaptAbilityTimingImpactSampleStreak = 0;
    if (remoteSamplerBridge != null) {
      remoteSamplerBridge.clear();
    }
  }

  @Override
  public void postStart() {

  }

  @Override
  public void onTick() {
    if (!syncTickQueued.compareAndSet(false, true)) {
      return;
    }

    J.s(() -> {
      try {
        tickSync();
      } finally {
        syncTickQueued.set(false);
      }
    });
  }

  public List<IntegrationStatus> statuses() {
    List<IntegrationStatus> out = new ArrayList<>();
    for (String plugin : PRIMARY_PLUGINS) {
      out.add(statusFor(plugin));
    }

    List<String> extras = nodes.keySet().stream()
        .filter(plugin -> !PRIMARY_PLUGINS.contains(plugin))
        .sorted()
        .toList();
    for (String plugin : extras) {
      out.add(statusFor(plugin));
    }

    return out;
  }

  public IntegrationStatus statusFor(String pluginId) {
    String normalized = normalize(pluginId);
    IntegrationNodeState state = nodes.get(normalized);
    if (state == null) {
      return new IntegrationStatus(
          normalized,
          Health.MISSING,
          "-",
          0L,
          "not-discovered"
      );
    }

    String protocol = state.negotiatedProtocol == null ? "-" : state.negotiatedProtocol.asText();
    return new IntegrationStatus(
        normalized,
        state.health,
        protocol,
        state.lastHeartbeatMs,
        state.message
    );
  }

  public List<String> recentTimeline(int maxEntries) {
    int max = Math.max(1, maxEntries);
    List<TimelineEvent> copy = new ArrayList<>(timeline);
    copy.sort(Comparator.comparingLong(TimelineEvent::atMs).reversed());
    List<String> out = new ArrayList<>();
    long now = System.currentTimeMillis();
    for (int i = 0; i < copy.size() && i < max; i++) {
      TimelineEvent event = copy.get(i);
      out.add(Form.duration(Math.max(0L, now - event.atMs), 1) + " ago - " + event.message);
    }
    return out;
  }

  private void tickSync() {
    long now = System.currentTimeMillis();
    if (now - lastDiscoveryMs >= DISCOVERY_INTERVAL_MS) {
      discoverProviders(now);
      discoverMetricSources(now);
      lastDiscoveryMs = now;
    }

    for (String pluginId : PRIMARY_PLUGINS) {
      nodes.computeIfAbsent(pluginId, IntegrationNodeState::new);
    }

    for (IntegrationNodeState node : nodes.values()) {
      updateNode(node, now);
    }

    evaluateCorrelation(now);
    evaluateThresholds(now);
    trimTimeline(now);
  }

  private void discoverMetricSources(long now) {
    ThirdPartyMetricRegistry registry = thirdPartyMetrics;

    if (registry == null) {
      return;
    }

    try {
      registry.reconcile(now);
    } catch (Throwable e) {
      React.warn("[metric] third-party metric reconcile failed: " + e.getClass().getSimpleName()
          + (e.getMessage() == null ? "" : " - " + e.getMessage()));
    }
  }

  private void discoverProviders(long now) {
    Map<String, IntegrationServiceContract> discovered = new HashMap<>();
    Collection<RegisteredServiceProvider<IntegrationServiceContract>> registrations = Bukkit.getServicesManager().getRegistrations(IntegrationServiceContract.class);

    for (RegisteredServiceProvider<IntegrationServiceContract> registration : registrations) {
      IntegrationServiceContract provider = registration.getProvider();
      if (provider == null || registration.getPlugin() == null || !registration.getPlugin().isEnabled()) {
        continue;
      }

      String pluginId = normalize(provider.pluginId());
      if (pluginId.isBlank() || "react".equals(pluginId)) {
        continue;
      }

      discovered.put(pluginId, provider);
      IntegrationNodeState state = nodes.computeIfAbsent(pluginId, IntegrationNodeState::new);
      if (state.provider == null) {
        logLifecycle("discover", pluginId, "provider=" + registration.getPlugin().getName());
      }
      bindDiscoveredProvider(state, provider, now);
      state.lastProviderSeenMs = now;
    }

    for (Class<?> serviceClass : Bukkit.getServicesManager().getKnownServices()) {
      if (serviceClass == null
          || serviceClass == IntegrationServiceContract.class
          || !ReflectiveIntegrationProviderAdapter.supportsServiceClass(serviceClass)) {
        continue;
      }

      for (RegisteredServiceProvider<?> registration : getRegistrationsRaw(serviceClass)) {
        if (registration.getPlugin() == null || !registration.getPlugin().isEnabled()) {
          continue;
        }
        Object providerObject = registration.getProvider();
        ReflectiveIntegrationProviderAdapter bridge = ReflectiveIntegrationProviderAdapter.tryCreate(providerObject, serviceClass);
        if (bridge == null) {
          continue;
        }

        String pluginId = normalize(bridge.pluginId());
        if (pluginId.isBlank() || "react".equals(pluginId) || discovered.containsKey(pluginId)) {
          continue;
        }

        discovered.put(pluginId, bridge);
        IntegrationNodeState state = nodes.computeIfAbsent(pluginId, IntegrationNodeState::new);
        if (state.provider == null) {
          logLifecycle(
              "discover",
              pluginId,
              "provider=" + registration.getPlugin().getName()
                  + " service=" + bridge.sourceServiceClassName()
                  + " reflective=true"
          );
        }
        bindDiscoveredProvider(state, bridge, now);
        state.lastProviderSeenMs = now;
      }
    }

    for (IntegrationNodeState state : nodes.values()) {
      if (!discovered.containsKey(state.pluginId) && now - state.lastProviderSeenMs > DISCOVERY_INTERVAL_MS * 2L) {
        resetBinding(state, null);
      }
    }
  }

  private void bindDiscoveredProvider(IntegrationNodeState state, IntegrationServiceContract provider, long now) {
    if (sameProvider(state.provider, provider)) {
      state.provider = provider;
      return;
    }

    boolean replacing = state.provider != null;
    resetBinding(state, provider);
    if (replacing) {
      logLifecycle("replace", state.pluginId, "provider-replaced");
      remoteSamplerBridge.markPluginUnavailable(
          state.pluginId,
          fixedKeys(state),
          "provider-replaced"
      );
    }
    state.lastProviderSeenMs = now;
  }

  private boolean sameProvider(IntegrationServiceContract current, IntegrationServiceContract candidate) {
    if (current == candidate) {
      return true;
    }
    if (current instanceof ReflectiveIntegrationProviderAdapter currentReflective
        && candidate instanceof ReflectiveIntegrationProviderAdapter candidateReflective) {
      return currentReflective.wrapsSameProvider(candidateReflective);
    }
    return false;
  }

  private void resetBinding(IntegrationNodeState state, IntegrationServiceContract provider) {
    state.provider = provider;
    state.bound = false;
    state.lastHandshakeAttemptMs = 0L;
    state.lastHeartbeatMs = 0L;
    state.negotiatedProtocol = null;
    state.expectedKeys = null;
    state.expectedKeysProvider = null;
    state.expectedKeysAtMs = 0L;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Collection<RegisteredServiceProvider<?>> getRegistrationsRaw(Class<?> serviceClass) {
    Collection registrations = Bukkit.getServicesManager().getRegistrations((Class) serviceClass);
    if (registrations == null || registrations.isEmpty()) {
      return List.of();
    }
    return (Collection<RegisteredServiceProvider<?>>) registrations;
  }

  private void updateNode(IntegrationNodeState node, long now) {
    IntegrationServiceContract provider = node.provider;
    Set<String> expectedKeys = fixedKeys(node);

    if (provider == null) {
      node.bound = false;
      setHealth(node, Health.MISSING, "provider-missing");
      remoteSamplerBridge.markPluginUnavailable(node.pluginId, expectedKeys, "provider-missing");
      return;
    }

    if (!node.bound || now - node.lastHandshakeAttemptMs >= HANDSHAKE_RETRY_MS) {
      attemptHandshake(node, provider, now);
    }

    if (!node.bound) {
      remoteSamplerBridge.markPluginUnavailable(node.pluginId, expectedKeys, "handshake-not-bound");
      return;
    }

    IntegrationHeartbeat heartbeat;
    Set<IntegrationProtocolVersion> providerProtocols;
    try {
      heartbeat = provider.heartbeat();
      providerProtocols = provider.supportedProtocols();
    } catch (Throwable e) {
      node.bound = false;
      setHealth(node, Health.DEGRADED, "heartbeat-error:" + e.getClass().getSimpleName());
      remoteSamplerBridge.markPluginUnavailable(node.pluginId, expectedKeys, "heartbeat-error");
      return;
    }

    if (heartbeat == null) {
      setHealth(node, Health.DEGRADED, "heartbeat-missing");
      remoteSamplerBridge.markPluginUnavailable(node.pluginId, expectedKeys, "heartbeat-missing");
      return;
    }
    if (!heartbeat.healthy()) {
      setHealth(node, Health.DEGRADED, heartbeat.message().isBlank() ? "heartbeat-unhealthy" : heartbeat.message());
      remoteSamplerBridge.markPluginUnavailable(node.pluginId, expectedKeys, "heartbeat-unhealthy");
      return;
    }
    if (providerProtocols == null
        || heartbeat.protocol() == null
        || !providerProtocols.contains(heartbeat.protocol())) {
      setHealth(node, Health.DEGRADED, "heartbeat-protocol-invalid");
      remoteSamplerBridge.markPluginUnavailable(node.pluginId, expectedKeys, "heartbeat-protocol-invalid");
      return;
    }
    if (heartbeat.lastHeartbeatMs() > now + MAX_FUTURE_HEARTBEAT_MS) {
      setHealth(node, Health.DEGRADED, "heartbeat-future-timestamp");
      remoteSamplerBridge.markPluginUnavailable(node.pluginId, expectedKeys, "heartbeat-future-timestamp");
      return;
    }

    node.lastHeartbeatMs = heartbeat.lastHeartbeatMs();
    if (!heartbeat.message().isBlank()) {
      node.message = heartbeat.message();
    }

    if (node.lastHeartbeatMs <= 0L || now - node.lastHeartbeatMs > STALE_HEARTBEAT_TIMEOUT_MS) {
      setHealth(node, Health.STALE, "heartbeat-timeout");
      remoteSamplerBridge.markPluginUnavailable(node.pluginId, expectedKeys, "heartbeat-timeout");
      return;
    }

    try {
      expectedKeys = providerKeys(node, provider, now);
      Map<String, IntegrationMetricSample> samples = provider.sampleMetrics(expectedKeys);
      int availableSamples = remoteSamplerBridge.updatePluginSamples(node.pluginId, expectedKeys, samples, "metric-unavailable");
      remoteSamplerBridge.updatePluginGroups(node.pluginId, provider.metricGroups());
      if (!expectedKeys.isEmpty() && availableSamples <= 0) {
        setHealth(node, Health.DEGRADED, "metric-data-unavailable");
        return;
      }
      setHealth(node, Health.HEALTHY, "ok");
    } catch (Throwable e) {
      setHealth(node, Health.DEGRADED, "metric-error:" + e.getClass().getSimpleName());
      remoteSamplerBridge.markPluginUnavailable(node.pluginId, expectedKeys, "metric-error");
    }
  }

  private Set<String> fixedKeys(IntegrationNodeState node) {
    if (node.fixedKeys == null) {
      node.fixedKeys = IntegrationMetricKeySelector.expectedKeys(node.pluginId, null);
    }
    return node.fixedKeys;
  }

  private Set<String> providerKeys(IntegrationNodeState node, IntegrationServiceContract provider, long now) {
    if (node.expectedKeys == null
        || node.expectedKeysProvider != provider
        || now - node.expectedKeysAtMs >= EXPECTED_KEY_REFRESH_MS) {
      node.expectedKeys = IntegrationMetricKeySelector.expectedKeys(node.pluginId, provider);
      node.expectedKeysProvider = provider;
      node.expectedKeysAtMs = now;
    }
    return node.expectedKeys;
  }

  private void attemptHandshake(IntegrationNodeState node, IntegrationServiceContract provider, long now) {
    node.lastHandshakeAttemptMs = now;
    logLifecycle("negotiate", node.pluginId, "attempt");

    IntegrationHandshakeResponse response;
    Set<IntegrationProtocolVersion> providerProtocols;
    try {
      response = provider.handshake(localService.createRequest());
      providerProtocols = provider.supportedProtocols();
    } catch (Throwable e) {
      node.bound = false;
      setHealth(node, Health.DEGRADED, "handshake-error:" + e.getClass().getSimpleName());
      return;
    }

    if (response == null || !response.accepted() || response.negotiatedProtocol() == null) {
      node.bound = false;
      String reason = response == null ? "empty-response" : response.message();
      setHealth(node, Health.DEGRADED, "handshake-rejected:" + reason);
      return;
    }

    IntegrationProtocolVersion negotiated = response.negotiatedProtocol();
    boolean validResponder = node.pluginId.equals(normalize(response.responderPluginId()));
    boolean localSupports = localService.supportedProtocols().contains(negotiated);
    boolean remoteSupports = response.supportedProtocols().contains(negotiated)
        && providerProtocols != null
        && providerProtocols.contains(negotiated);
    boolean metricsCapable = response.capabilities().contains("metrics");
    if (!validResponder || !localSupports || !remoteSupports || !metricsCapable) {
      node.bound = false;
      setHealth(node, Health.DEGRADED, "handshake-contract-invalid");
      return;
    }

    boolean wasBound = node.bound;
    node.bound = true;
    node.negotiatedProtocol = negotiated;
    node.message = response.message();
    if (!wasBound) {
      logLifecycle("bind", node.pluginId, "protocol=" + node.negotiatedProtocol.asText());
    }
  }

  private void evaluateCorrelation(long now) {
    Sampler tickSampler = React.sampler(SamplerTickTime.ID);
    if (tickSampler == null) {
      return;
    }

    double tickMs = tickSampler.sample();
    double irisQueue = remoteSamplerBridge.valueOr("iris", IntegrationMetricSchema.IRIS_PREGEN_QUEUE, -1D);

    if (ReactConfiguration.get().isVerbose()
        && tickMs >= 50D
        && irisQueue >= 0D
        && previousIrisQueue >= 0D
        && (irisQueue - previousIrisQueue) >= 32D
        && now - lastCorrelationLogMs >= CORRELATION_COOLDOWN_MS) {
      lastCorrelationLogMs = now;
      lastCorrelationMessage = String.format(Locale.ROOT,
          "MSPT spike %.1f -> Iris pregenerator queue rise +%.0f (%.0f)",
          tickMs,
          irisQueue - previousIrisQueue,
          irisQueue
      );
      React.verbose("[integration] correlation " + lastCorrelationMessage);
    }

    if (irisQueue >= 0D) {
      previousIrisQueue = irisQueue;
    }
  }

  private void evaluateThresholds(long now) {
    double irisQueue = remoteSamplerBridge.valueOr("iris", IntegrationMetricSchema.IRIS_PREGEN_QUEUE, -1D);
    double adaptSessionLoad = remoteSamplerBridge.valueOr("adapt", IntegrationMetricSchema.ADAPT_SESSION_LOAD, -1D);
    double adaptAbilityOps = remoteSamplerBridge.valueOr("adapt", ReactConfiguration.adaptAbilityOpsMetricKey(), -1D);
    double adaptAbilityTimingBudget = remoteSamplerBridge.valueOr(
        "adapt",
        IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET,
        -1D
    );
    String adaptAbilityOpsMode = ReactConfiguration.adaptAbilityOpsMetricLabel();
    double tickMs = sampleTickMs();

    evaluateThreshold(
        "iris.queue.high",
        irisQueue >= 512D,
        String.format(Locale.ROOT, "Iris pregenerator queue exceeded threshold (%.0f chunks)", irisQueue),
        String.format(Locale.ROOT, "Iris pregenerator queue recovered (%.0f chunks)", Math.max(0D, irisQueue)),
        now
    );
    evaluateThreshold(
        "adapt.session.load.high",
        adaptSessionLoad >= ADAPT_SESSION_LOAD_THRESHOLD,
        String.format(Locale.ROOT, "Adapt session load high (%.1f%%)", adaptSessionLoad),
        String.format(Locale.ROOT, "Adapt session load recovered (%.1f%%)", Math.max(0D, adaptSessionLoad)),
        now
    );
    adaptAbilityTimingImpactSampleStreak = nextAdaptAbilityTimingImpactStreak(
        adaptAbilityTimingImpactSampleStreak,
        adaptAbilityTimingBudget,
        tickMs
    );
    boolean adaptAbilityTimingHigh = shouldReportAdaptAbilityTiming(
        adaptAbilityTimingBudget,
        adaptAbilityTimingImpactSampleStreak,
        tickMs
    );

    evaluateThreshold(
        "adapt.ability.guard-timing.high",
        adaptAbilityTimingHigh,
        String.format(
            Locale.ROOT,
            "Adapt ability guard-check timing pressure elevated [%s checks] (budget=%.1f%%, %.0f ops/min, streak=%d, MSPT=%.1fms, session=%.1f%%)",
            adaptAbilityOpsMode,
            adaptAbilityTimingBudget,
            adaptAbilityOps,
            adaptAbilityTimingImpactSampleStreak,
            tickMs,
            adaptSessionLoad
        ),
        String.format(
            Locale.ROOT,
            "Adapt ability guard-check timing-pressure alert cleared [%s checks] (budget=%.1f%%, %.0f ops/min, MSPT=%.1fms)",
            adaptAbilityOpsMode,
            Math.max(0D, adaptAbilityTimingBudget),
            Math.max(0D, adaptAbilityOps),
            Math.max(0D, tickMs)
        ),
        now
    );
  }

  static int nextAdaptAbilityTimingImpactStreak(int currentStreak, double timingBudgetPercent, double tickMs) {
    if (!hasAdaptAbilityTimingImpact(timingBudgetPercent, tickMs)) {
      return 0;
    }
    return Math.min(Integer.MAX_VALUE, Math.max(0, currentStreak) + 1);
  }

  static boolean shouldReportAdaptAbilityTiming(double timingBudgetPercent, int impactSampleStreak, double tickMs) {
    return hasAdaptAbilityTimingImpact(timingBudgetPercent, tickMs)
        && impactSampleStreak >= ADAPT_ABILITY_TIMING_SUSTAINED_SAMPLES;
  }

  private static boolean hasAdaptAbilityTimingImpact(double timingBudgetPercent, double tickMs) {
    return Double.isFinite(timingBudgetPercent)
        && timingBudgetPercent >= ADAPT_ABILITY_TIMING_BUDGET_THRESHOLD
        && Double.isFinite(tickMs)
        && tickMs >= ADAPT_ABILITY_TIMING_MSPT_GATE;
  }

  private void evaluateThreshold(String key, boolean tripped, String tripMessage, String recoverMessage, long now) {
    boolean wasActive = activeThresholds.getOrDefault(key, false);
    if (tripped && !wasActive) {
      activeThresholds.put(key, true);
      addTimeline(tripMessage, now, true);
      return;
    }

    if (!tripped && wasActive) {
      activeThresholds.put(key, false);
      addTimeline(recoverMessage, now, false);
    }
  }

  private double sampleTickMs() {
    Sampler tickSampler = React.sampler(SamplerTickTime.ID);
    if (tickSampler == null) {
      return -1D;
    }
    return tickSampler.sample();
  }

  private void trimTimeline(long now) {
    while (!timeline.isEmpty()) {
      TimelineEvent oldest = timeline.peekFirst();
      if (oldest == null || now - oldest.atMs <= TIMELINE_RETENTION_MS) {
        break;
      }
      timeline.removeFirst();
    }
  }

  private void addTimeline(String message, long now, boolean severe) {
    timeline.addLast(new TimelineEvent(now, message));
    if (severe) {
      React.warn("[integration-timeline] " + message);
    } else {
      React.info("[integration-timeline] " + message);
    }
  }

  private void setHealth(IntegrationNodeState node, Health newHealth, String message) {
    Health previous = node.health;
    node.health = newHealth;
    node.message = message == null ? "" : message;

    if (previous == newHealth) {
      return;
    }

    if (newHealth == Health.HEALTHY) {
      logLifecycle("recover", node.pluginId, node.message);
      return;
    }

    logLifecycle("degrade", node.pluginId, node.message);
  }

  private void logLifecycle(String event, String pluginId, String detail) {
    React.verbose("[integration] event=" + event + " plugin=" + pluginId + " detail=" + detail);
  }

  private String normalize(String pluginId) {
    return pluginId == null ? "" : pluginId.trim().toLowerCase(Locale.ROOT);
  }

  public enum Health {
    HEALTHY,
    DEGRADED,
    STALE,
    MISSING
  }

  public record IntegrationStatus(
      String pluginId,
      Health health,
      String protocol,
      long lastHeartbeatMs,
      String message
  ) {
  }

  private static final class IntegrationNodeState {
    private final String pluginId;
    private IntegrationServiceContract provider;
    private boolean bound;
    private long lastHandshakeAttemptMs;
    private long lastHeartbeatMs;
    private long lastProviderSeenMs;
    private IntegrationProtocolVersion negotiatedProtocol;
    private Health health;
    private String message;
    private Set<String> fixedKeys;
    private Set<String> expectedKeys;
    private IntegrationServiceContract expectedKeysProvider;
    private long expectedKeysAtMs;

    private IntegrationNodeState(String pluginId) {
      this.pluginId = pluginId;
      this.provider = null;
      this.bound = false;
      this.lastHandshakeAttemptMs = 0L;
      this.lastHeartbeatMs = 0L;
      this.lastProviderSeenMs = 0L;
      this.negotiatedProtocol = null;
      this.health = Health.MISSING;
      this.message = "not-discovered";
      this.fixedKeys = null;
      this.expectedKeys = null;
      this.expectedKeysProvider = null;
      this.expectedKeysAtMs = 0L;
    }
  }

  private record TimelineEvent(long atMs, String message) {
  }
}
