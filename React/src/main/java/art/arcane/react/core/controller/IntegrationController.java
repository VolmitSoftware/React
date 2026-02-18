package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.integration.ReactIntegrationService;
import art.arcane.react.core.integration.ReflectiveIntegrationProviderAdapter;
import art.arcane.react.core.integration.RemoteSamplerBridge;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.scheduling.J;
import art.arcane.react.util.scheduling.TickedObject;
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
    private static final long HANDSHAKE_RETRY_MS = 10_000L;
    private static final long STALE_HEARTBEAT_TIMEOUT_MS = 15_000L;
    private static final long CORRELATION_COOLDOWN_MS = 10_000L;
    private static final long TIMELINE_RETENTION_MS = 180_000L;
    private static final double ADAPT_SESSION_LOAD_THRESHOLD = 65D;
    private static final double ADAPT_ABILITY_OPS_THRESHOLD = 240D;
    private static final int ADAPT_ABILITY_OPS_SUSTAINED_SAMPLES = 3;
    private static final double ADAPT_ABILITY_OPS_MSPT_GATE = 50D;
    private static final Set<String> PRIMARY_PLUGINS = Set.of("iris", "adapt");

    private final transient AtomicBoolean syncTickQueued = new AtomicBoolean(false);
    private final transient Map<String, IntegrationNodeState> nodes = new ConcurrentHashMap<>();
    private final transient Deque<TimelineEvent> timeline = new ConcurrentLinkedDeque<>();
    private final transient Map<String, Boolean> activeThresholds = new ConcurrentHashMap<>();
    private transient ReactIntegrationService localService;
    private transient RemoteSamplerBridge remoteSamplerBridge;
    private transient long lastDiscoveryMs;
    private transient long lastCorrelationLogMs;
    private transient double previousIrisQueue;
    private transient String lastCorrelationMessage;
    private transient int adaptAbilityOpsSampleStreak;

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
        localService = new ReactIntegrationService();
        localService.register();
        lastDiscoveryMs = 0L;
        lastCorrelationLogMs = 0L;
        previousIrisQueue = -1D;
        lastCorrelationMessage = "";
        adaptAbilityOpsSampleStreak = 0;
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
        nodes.clear();
        timeline.clear();
        activeThresholds.clear();
        adaptAbilityOpsSampleStreak = 0;
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

    private void discoverProviders(long now) {
        Map<String, IntegrationServiceContract> discovered = new HashMap<>();
        Collection<RegisteredServiceProvider<IntegrationServiceContract>> registrations = Bukkit.getServicesManager().getRegistrations(IntegrationServiceContract.class);

        for (RegisteredServiceProvider<IntegrationServiceContract> registration : registrations) {
            IntegrationServiceContract provider = registration.getProvider();
            if (provider == null) {
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
            state.provider = provider;
            state.lastProviderSeenMs = now;
        }

        for (Class<?> serviceClass : Bukkit.getServicesManager().getKnownServices()) {
            if (serviceClass == null
                    || serviceClass == IntegrationServiceContract.class
                    || !ReflectiveIntegrationProviderAdapter.supportsServiceClass(serviceClass)) {
                continue;
            }

            for (RegisteredServiceProvider<?> registration : getRegistrationsRaw(serviceClass)) {
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
                state.provider = bridge;
                state.lastProviderSeenMs = now;
            }
        }

        for (IntegrationNodeState state : nodes.values()) {
            if (!discovered.containsKey(state.pluginId) && now - state.lastProviderSeenMs > DISCOVERY_INTERVAL_MS * 2L) {
                state.provider = null;
            }
        }
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
        Set<String> expectedKeys = expectedKeys(node.pluginId);
        IntegrationServiceContract provider = node.provider;

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
        try {
            heartbeat = provider.heartbeat();
        } catch (Throwable e) {
            node.bound = false;
            setHealth(node, Health.DEGRADED, "heartbeat-error:" + e.getClass().getSimpleName());
            remoteSamplerBridge.markPluginUnavailable(node.pluginId, expectedKeys, "heartbeat-error");
            return;
        }

        if (heartbeat != null) {
            node.lastHeartbeatMs = heartbeat.lastHeartbeatMs();
            if (heartbeat.protocol() != null) {
                node.negotiatedProtocol = heartbeat.protocol();
            }
            if (!heartbeat.message().isBlank()) {
                node.message = heartbeat.message();
            }
        }

        if (node.lastHeartbeatMs <= 0L || now - node.lastHeartbeatMs > STALE_HEARTBEAT_TIMEOUT_MS) {
            setHealth(node, Health.STALE, "heartbeat-timeout");
            remoteSamplerBridge.markPluginUnavailable(node.pluginId, expectedKeys, "heartbeat-timeout");
            return;
        }

        try {
            Map<String, IntegrationMetricSample> samples = provider.sampleMetrics(expectedKeys);
            remoteSamplerBridge.updatePluginSamples(node.pluginId, expectedKeys, samples, "metric-unavailable");
            setHealth(node, Health.HEALTHY, "ok");
        } catch (Throwable e) {
            setHealth(node, Health.DEGRADED, "metric-error:" + e.getClass().getSimpleName());
            remoteSamplerBridge.markPluginUnavailable(node.pluginId, expectedKeys, "metric-error");
        }
    }

    private void attemptHandshake(IntegrationNodeState node, IntegrationServiceContract provider, long now) {
        node.lastHandshakeAttemptMs = now;
        logLifecycle("negotiate", node.pluginId, "attempt");

        IntegrationHandshakeResponse response;
        try {
            response = provider.handshake(localService.createRequest());
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

        boolean wasBound = node.bound;
        node.bound = true;
        node.negotiatedProtocol = response.negotiatedProtocol();
        node.message = response.message();
        if (!wasBound) {
            logLifecycle("bind", node.pluginId, "protocol=" + node.negotiatedProtocol.asText());
        }
    }

    private void evaluateCorrelation(long now) {
        var tickSampler = React.sampler(SamplerTickTime.ID);
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
        boolean rawAbilityOpsHigh = adaptAbilityOps >= ADAPT_ABILITY_OPS_THRESHOLD;
        if (rawAbilityOpsHigh) {
            adaptAbilityOpsSampleStreak = Math.min(Integer.MAX_VALUE, adaptAbilityOpsSampleStreak + 1);
        } else {
            adaptAbilityOpsSampleStreak = 0;
        }

        evaluateThreshold(
                "adapt.ability.ops.raw.high",
                rawAbilityOpsHigh,
                String.format(Locale.ROOT, "Adapt ability ops high [%s checks] (%.0f ops/min)", adaptAbilityOpsMode, adaptAbilityOps),
                String.format(Locale.ROOT, "Adapt ability ops raw recovered [%s checks] (%.0f ops/min)", adaptAbilityOpsMode, Math.max(0D, adaptAbilityOps)),
                now,
                false
        );

        boolean stressGate = adaptSessionLoad >= ADAPT_SESSION_LOAD_THRESHOLD
                || tickMs >= ADAPT_ABILITY_OPS_MSPT_GATE;
        boolean sustainedGate = adaptAbilityOpsSampleStreak >= ADAPT_ABILITY_OPS_SUSTAINED_SAMPLES;
        boolean gatedAbilityOpsHigh = rawAbilityOpsHigh && sustainedGate && stressGate;
        String gateMode = resolveAdaptOpsGateMode(adaptSessionLoad, tickMs);

        evaluateThreshold(
                "adapt.ability.ops.high",
                gatedAbilityOpsHigh,
                String.format(
                        Locale.ROOT,
                        "Adapt ability ops elevated [%s checks] (%.0f ops/min, streak=%d, gate=%s)",
                        adaptAbilityOpsMode,
                        adaptAbilityOps,
                        adaptAbilityOpsSampleStreak,
                        gateMode
                ),
                String.format(
                        Locale.ROOT,
                        "Adapt ability ops normalized [%s checks] (%.0f ops/min, streak=%d)",
                        adaptAbilityOpsMode,
                        Math.max(0D, adaptAbilityOps),
                        adaptAbilityOpsSampleStreak
                ),
                now
        );
    }

    private void evaluateThreshold(String key, boolean tripped, String tripMessage, String recoverMessage, long now) {
        evaluateThreshold(key, tripped, tripMessage, recoverMessage, now, true);
    }

    private void evaluateThreshold(String key, boolean tripped, String tripMessage, String recoverMessage, long now, boolean severeTrip) {
        boolean wasActive = activeThresholds.getOrDefault(key, false);
        if (tripped && !wasActive) {
            activeThresholds.put(key, true);
            addTimeline(tripMessage, now, severeTrip);
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

    private String resolveAdaptOpsGateMode(double adaptSessionLoad, double tickMs) {
        boolean loadGate = adaptSessionLoad >= ADAPT_SESSION_LOAD_THRESHOLD;
        boolean msptGate = tickMs >= ADAPT_ABILITY_OPS_MSPT_GATE;
        if (loadGate && msptGate) {
            return "session+mspt";
        }
        if (loadGate) {
            return "session";
        }
        if (msptGate) {
            return "mspt";
        }
        return "none";
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

    private Set<String> expectedKeys(String pluginId) {
        String normalized = normalize(pluginId);
        if ("iris".equals(normalized)) {
            return IntegrationMetricSchema.irisKeys();
        }
        if ("adapt".equals(normalized)) {
            return IntegrationMetricSchema.adaptKeys();
        }
        return Set.of();
    }

    private String normalize(String pluginId) {
        return pluginId == null ? "" : pluginId.trim().toLowerCase(Locale.ROOT);
    }

    public record IntegrationStatus(
            String pluginId,
            Health health,
            String protocol,
            long lastHeartbeatMs,
            String message
    ) {
    }

    public enum Health {
        HEALTHY,
        DEGRADED,
        STALE,
        MISSING
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
        }
    }

    private record TimelineEvent(long atMs, String message) {
    }
}
