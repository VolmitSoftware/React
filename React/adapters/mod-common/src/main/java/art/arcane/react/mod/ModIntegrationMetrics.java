package art.arcane.react.mod;

import art.arcane.react.platform.IntegrationMetricsHost;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.integration.IntegrationMetricType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Integration metrics on a mod server, read straight from the installed mods once a second. Bukkit gets the same
 * metrics from each plugin's integration service; on mods each {@link Source} reads that mod's own public telemetry.
 */
final class ModIntegrationMetrics implements IntegrationMetricsHost {
    private static final int REFRESH_TICKS = 20;
    private final MinecraftServer server;
    private final List<Source> sources;
    private volatile Map<String, Published> published = Map.of();

    /** One mod's metrics. {@link #read} runs on the server thread. */
    interface Source {
        String id();

        /** Every key the dashboards may ask for; the ones {@link #read} leaves out show as unavailable. */
        Set<String> keys();

        Reading read(MinecraftServer server, long nowMs) throws ReflectiveOperationException;
    }

    record Reading(Map<String, Double> values, List<WorldGroup> worlds) {
    }

    record WorldGroup(Set<String> scopes, String label, Map<String, Double> values) {
    }

    private record Published(Status status, Map<String, IntegrationMetricSample> samples, Map<String, IntegrationMetricGroup> worlds,
                             List<IntegrationMetricGroup> worldList) {
    }

    ModIntegrationMetrics(MinecraftServer server, List<Source> sources) {
        this.server = server;
        this.sources = List.copyOf(sources);
    }

    boolean any() {
        return !sources.isEmpty();
    }

    void schedule(PlatformScheduler scheduler) {
        scheduler.global(() -> {
            refresh();
            schedule(scheduler);
        }, REFRESH_TICKS);
    }

    void refresh() {
        long now = System.currentTimeMillis();
        Map<String, Published> next = new HashMap<>();
        for (Source source : sources) {
            try {
                Reading reading = source.read(server, now);
                Map<String, IntegrationMetricGroup> worlds = new HashMap<>();
                List<IntegrationMetricGroup> worldList = new ArrayList<>();
                for (WorldGroup world : reading.worlds()) {
                    Map<String, IntegrationMetricSample> worldSamples = samples(world.values().keySet(), world.values(), now, "world-metric-not-published");
                    for (String scope : world.scopes()) {
                        worlds.put(scope, new IntegrationMetricGroup("world", scope, world.label(), Map.of(), worldSamples));
                    }
                    worldList.add(worlds.get(world.scopes().iterator().next()));
                }
                worldList.sort(Comparator.comparing(IntegrationMetricGroup::scopeId));
                Set<String> keys = new LinkedHashSet<>(source.keys());
                keys.addAll(reading.values().keySet());
                next.put(source.id(), new Published(new Status("HEALTHY", ""),
                        samples(keys, reading.values(), now, "not-published-on-mods"), Map.copyOf(worlds), List.copyOf(worldList)));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                String reason = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                next.put(source.id(), new Published(new Status("DEGRADED", reason),
                        samples(source.keys(), Map.of(), now, "read-failed"), Map.of(), List.of()));
            }
        }
        published = Map.copyOf(next);
    }

    private static Map<String, IntegrationMetricSample> samples(Set<String> keys, Map<String, Double> values, long now, String missingReason) {
        Map<String, IntegrationMetricSample> samples = new LinkedHashMap<>();
        for (String key : keys) {
            IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(key);
            Double value = values.get(key);
            if (value == null || !Double.isFinite(value)) {
                samples.put(key, IntegrationMetricSample.unavailable(descriptor, missingReason, now));
                continue;
            }
            boolean integral = descriptor.type() == IntegrationMetricType.INTEGER || descriptor.type() == IntegrationMetricType.LONG;
            samples.put(key, IntegrationMetricSample.available(descriptor, integral ? Math.rint(value) : value, now));
        }
        return samples;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public Status status(String integrationId) {
        Published current = published.get(integrationId);
        return current == null ? null : current.status();
    }

    @Override
    public IntegrationMetricSample sample(String integrationId, String key) {
        Published current = published.get(integrationId);
        return current == null ? null : current.samples().get(key);
    }

    @Override
    public Map<String, IntegrationMetricSample> samples(String integrationId) {
        Published current = published.get(integrationId);
        return current == null ? Map.of() : current.samples();
    }

    @Override
    public List<IntegrationMetricGroup> groups(String integrationId, String scopeKind) {
        Published current = published.get(integrationId);
        return current == null || !"world".equals(scopeKind) ? List.of() : current.worldList();
    }

    @Override
    public IntegrationMetricGroup group(String integrationId, String scopeKind, String scopeId) {
        Published current = published.get(integrationId);
        return current == null || !"world".equals(scopeKind) ? null : current.worlds().get(scopeId);
    }

    @Override
    public String worldScope(MapRenderTarget target) {
        if (target == null) {
            return null;
        }
        UUID worldId = target.worldId();
        if (worldId == null && target.viewerId() != null) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(target.viewerId());
            return viewer == null ? null : ModReactWorld.nameOf(viewer.level());
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (ModReactWorld.idOf(level).equals(worldId)) {
                return ModReactWorld.nameOf(level);
            }
        }
        return null;
    }
}
