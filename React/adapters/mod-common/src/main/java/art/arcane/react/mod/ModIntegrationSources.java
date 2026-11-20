package art.arcane.react.mod;

import art.arcane.react.api.rendering.RendererAdaptMetrics;
import art.arcane.react.api.rendering.RendererIrisMetrics;
import art.arcane.react.api.rendering.RendererIrisWorldMetrics;
import art.arcane.react.api.rendering.RendererWormholesMetrics;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The installed mods React can read integration metrics from. A mod counts as installed when its telemetry class
 * loads, so the dashboards for a missing mod are never registered, as on Bukkit.
 */
final class ModIntegrationSources {
    static final String IRIS_ENGINES = "art.arcane.iris.modded.ModdedWorldEngines";
    static final String IRIS_TELEMETRY = "art.arcane.iris.generation.runtime.EngineTelemetrySnapshot";
    static final String IRIS_HEAP = "art.arcane.iris.world.pregen.MantleHeapPressure";
    static final String WORMHOLES_RUNTIME = "art.arcane.wormholes.modded.WormholesModRuntime";
    static final String WORMHOLES_TELEMETRY = "art.arcane.wormholes.service.WormholesTelemetry";
    static final String ADAPT_CORE = "art.arcane.adapt.AdaptCore";
    static final String ADAPT_CHECKS = "art.arcane.adapt.api.telemetry.AbilityCheckTelemetry";

    private ModIntegrationSources() {
    }

    static List<ModIntegrationMetrics.Source> installed() {
        List<ModIntegrationMetrics.Source> sources = new ArrayList<>();
        if (ModReflection.find(IRIS_ENGINES) != null && ModReflection.find(IRIS_TELEMETRY) != null) {
            sources.add(new Iris());
        }
        if (ModReflection.find(WORMHOLES_RUNTIME) != null && ModReflection.find(WORMHOLES_TELEMETRY) != null) {
            sources.add(new Wormholes());
        }
        if (ModReflection.find(ADAPT_CORE) != null && ModReflection.find(ADAPT_CHECKS) != null) {
            sources.add(new Adapt());
        }
        return sources;
    }

    /**
     * Iris engine telemetry per world and in total. Cache sizes, maintenance workers, pending registrations and the
     * pregenerator are only published by the Bukkit plugin, so those rows stay unavailable on mods.
     */
    static final class Iris implements ModIntegrationMetrics.Source {
        private static final Map<String, String> TIMING_KEYS = timingKeys();
        private final Map<String, long[]> generatedHistory = new HashMap<>();

        @Override
        public String id() {
            return "iris";
        }

        @Override
        public Set<String> keys() {
            Set<String> keys = new LinkedHashSet<>(RendererIrisMetrics.dashboardMetricKeys());
            keys.addAll(RendererIrisWorldMetrics.dashboardMetricKeys());
            return keys;
        }

        @Override
        public ModIntegrationMetrics.Reading read(MinecraftServer server, long nowMs) throws ReflectiveOperationException {
            Class<?> telemetry = ModReflection.require(IRIS_TELEMETRY);
            Collection<?> engines = (Collection<?>) ModReflection.callStatic(ModReflection.require(IRIS_ENGINES), "activeEngines");
            List<Object> snapshots = new ArrayList<>();
            List<ModIntegrationMetrics.WorldGroup> worlds = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Object engine : engines) {
                Object world = ModReflection.call(engine, "getWorld");
                String identity = String.valueOf(ModReflection.call(world, "identity"));
                seen.add(identity);
                double chunksPerSecond = chunksPerSecond(identity, (long) ModReflection.number(engine, "getGenerated"), nowMs);
                Object snapshot = ModReflection.callStatic(telemetry, "capture", engine, chunksPerSecond, true, nowMs);
                snapshots.add(snapshot);
                Map<String, Double> values = worldValues(snapshot);
                Set<String> scopes = new LinkedHashSet<>(List.of(identity, String.valueOf(ModReflection.call(snapshot, "worldName"))));
                worlds.add(new ModIntegrationMetrics.WorldGroup(scopes, String.valueOf(ModReflection.call(snapshot, "worldName")), values));
            }
            generatedHistory.keySet().retainAll(seen);
            Object aggregate = ModReflection.callStatic(telemetry, "aggregate", snapshots);
            return new ModIntegrationMetrics.Reading(globalValues(aggregate), worlds);
        }

        private double chunksPerSecond(String identity, long generated, long nowMs) {
            long[] previous = generatedHistory.put(identity, new long[]{generated, nowMs});
            if (previous == null || nowMs <= previous[1]) {
                return 0D;
            }
            return Math.max(0L, generated - previous[0]) * 1000D / (nowMs - previous[1]);
        }

        private static Map<String, Double> globalValues(Object aggregate) throws ReflectiveOperationException {
            Map<String, Double> values = new LinkedHashMap<>();
            values.put(IntegrationMetricSchema.IRIS_WORLD_COUNT, ModReflection.number(aggregate, "worldCount"));
            values.put(IntegrationMetricSchema.IRIS_ENGINE_ACTIVE, ModReflection.number(aggregate, "active"));
            values.put(IntegrationMetricSchema.IRIS_ENGINE_CLOSING, ModReflection.number(aggregate, "closing"));
            values.put(IntegrationMetricSchema.IRIS_ENGINE_FAILED, ModReflection.number(aggregate, "failed"));
            values.put(IntegrationMetricSchema.IRIS_ENGINE_STUDIO, ModReflection.number(aggregate, "studio"));
            values.put(IntegrationMetricSchema.IRIS_LOADED_CHUNKS, ModReflection.number(aggregate, "loadedChunks"));
            values.put(IntegrationMetricSchema.IRIS_LOADED_ENTITIES, ModReflection.number(aggregate, "loadedEntities"));
            values.put(IntegrationMetricSchema.IRIS_ENTITY_SATURATION, ModReflection.number(aggregate, "entitySaturationMax"));
            values.put(IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_SESSION, ModReflection.number(aggregate, "generatedSession"));
            values.put(IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_TOTAL, ModReflection.number(aggregate, "generatedTotal"));
            values.put(IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, ModReflection.number(aggregate, "chunksPerSecond"));
            values.put(IntegrationMetricSchema.IRIS_BLOCK_UPDATES_PER_SECOND, ModReflection.number(aggregate, "blockUpdatesPerSecond"));
            values.put(IntegrationMetricSchema.IRIS_ENGINE_PARALLELISM, ModReflection.number(aggregate, "parallelism"));
            values.put(IntegrationMetricSchema.IRIS_GENERATION_ACTIVE_LEASES, ModReflection.number(aggregate, "activeGenerationLeases"));
            values.put(IntegrationMetricSchema.IRIS_HOTLOADS_TOTAL, ModReflection.number(aggregate, "hotloadsTotal"));
            values.put(IntegrationMetricSchema.IRIS_MANTLE_RESIDENT_PLATES, ModReflection.number(aggregate, "mantleResidentPlates"));
            values.put(IntegrationMetricSchema.IRIS_MANTLE_QUEUED_PLATES, ModReflection.number(aggregate, "mantleQueuedPlates"));
            values.put(IntegrationMetricSchema.IRIS_MANTLE_IDLE_AVERAGE_MS, ModReflection.number(aggregate, "mantleIdleAverageMs"));
            values.put(IntegrationMetricSchema.IRIS_MANTLE_IDLE_MAX_MS, ModReflection.number(aggregate, "mantleIdleMaxMs"));
            values.put(IntegrationMetricSchema.IRIS_MANTLE_IDLE_MIN_MS, ModReflection.number(aggregate, "mantleIdleMinMs"));
            putTimings(values, ModReflection.call(aggregate, "generationTimingMaximaMs"));
            Class<?> heap = ModReflection.find(IRIS_HEAP);
            if (heap != null) {
                double used = ((Number) ModReflection.callStatic(heap, "usedFraction")).doubleValue();
                values.put(IntegrationMetricSchema.IRIS_MANTLE_HEAP_USAGE, used);
                values.put(IntegrationMetricSchema.IRIS_MANTLE_RECLAIM_URGENCY, ((Number) ModReflection.callStatic(heap, "reclaimUrgency", used)).doubleValue());
            }
            return values;
        }

        private static Map<String, Double> worldValues(Object snapshot) throws ReflectiveOperationException {
            Map<String, Double> values = new LinkedHashMap<>();
            values.put(IntegrationMetricSchema.IRIS_ENGINE_ACTIVE, ModReflection.number(snapshot, "active"));
            values.put(IntegrationMetricSchema.IRIS_ENGINE_CLOSING, ModReflection.number(snapshot, "closing"));
            values.put(IntegrationMetricSchema.IRIS_ENGINE_FAILED, ModReflection.number(snapshot, "failed"));
            values.put(IntegrationMetricSchema.IRIS_ENGINE_STUDIO, ModReflection.number(snapshot, "studio"));
            values.put(IntegrationMetricSchema.IRIS_LOADED_CHUNKS, ModReflection.number(snapshot, "loadedChunks"));
            values.put(IntegrationMetricSchema.IRIS_LOADED_ENTITIES, ModReflection.number(snapshot, "loadedEntities"));
            values.put(IntegrationMetricSchema.IRIS_ENTITY_SATURATION, ModReflection.number(snapshot, "entitySaturation"));
            values.put(IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_SESSION, ModReflection.number(snapshot, "generatedSession"));
            values.put(IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_TOTAL, ModReflection.number(snapshot, "generatedTotal"));
            values.put(IntegrationMetricSchema.IRIS_CHUNKS_PER_SECOND, ModReflection.number(snapshot, "chunksPerSecond"));
            values.put(IntegrationMetricSchema.IRIS_BLOCK_UPDATES_PER_SECOND, ModReflection.number(snapshot, "blockUpdatesPerSecond"));
            values.put(IntegrationMetricSchema.IRIS_ENGINE_PARALLELISM, ModReflection.number(snapshot, "parallelism"));
            values.put(IntegrationMetricSchema.IRIS_GENERATION_ACTIVE_LEASES, ModReflection.number(snapshot, "activeGenerationLeases"));
            values.put(IntegrationMetricSchema.IRIS_HOTLOADS_TOTAL, ModReflection.number(snapshot, "hotloadsTotal"));
            values.put(IntegrationMetricSchema.IRIS_MANTLE_RESIDENT_PLATES, ModReflection.number(snapshot, "mantleResidentPlates"));
            values.put(IntegrationMetricSchema.IRIS_MANTLE_QUEUED_PLATES, ModReflection.number(snapshot, "mantleQueuedPlates"));
            values.put(IntegrationMetricSchema.IRIS_MANTLE_IDLE_AVERAGE_MS, ModReflection.number(snapshot, "mantleIdleMs"));
            putTimings(values, ModReflection.call(snapshot, "generationTimingsMs"));
            return values;
        }

        private static void putTimings(Map<String, Double> values, Object timings) {
            if (!(timings instanceof Map<?, ?> map)) {
                return;
            }
            for (Map.Entry<String, String> entry : TIMING_KEYS.entrySet()) {
                if (map.get(entry.getKey()) instanceof Number number) {
                    values.put(entry.getValue(), number.doubleValue());
                }
            }
        }

        private static Map<String, String> timingKeys() {
            Map<String, String> keys = new LinkedHashMap<>();
            keys.put("total", IntegrationMetricSchema.IRIS_GENERATION_TOTAL_MS);
            keys.put("updates", IntegrationMetricSchema.IRIS_GENERATION_UPDATES_MS);
            keys.put("terrain", IntegrationMetricSchema.IRIS_GENERATION_TERRAIN_MS);
            keys.put("biome", IntegrationMetricSchema.IRIS_GENERATION_BIOME_MS);
            keys.put("post", IntegrationMetricSchema.IRIS_GENERATION_POST_MS);
            keys.put("perfection", IntegrationMetricSchema.IRIS_GENERATION_PERFECTION_MS);
            keys.put("decoration", IntegrationMetricSchema.IRIS_GENERATION_DECORATION_MS);
            keys.put("cave", IntegrationMetricSchema.IRIS_GENERATION_CAVE_MS);
            keys.put("deposit", IntegrationMetricSchema.IRIS_GENERATION_DEPOSIT_MS);
            keys.put("carve.resolve", IntegrationMetricSchema.IRIS_GENERATION_CARVE_RESOLVE_MS);
            keys.put("carve.apply", IntegrationMetricSchema.IRIS_GENERATION_CARVE_APPLY_MS);
            keys.put("context.prefill", IntegrationMetricSchema.IRIS_GENERATION_CONTEXT_PREFILL_MS);
            keys.put("pregen.wait.permit", IntegrationMetricSchema.IRIS_PREGEN_WAIT_PERMIT_MS);
            keys.put("pregen.wait.adaptive", IntegrationMetricSchema.IRIS_PREGEN_WAIT_ADAPTIVE_MS);
            return Map.copyOf(keys);
        }
    }

    /**
     * Portal and projection counts and packet rate. The mod build never feeds Wormholes' render time, spoofed
     * entity or traversal counters, so those rows stay unavailable instead of showing a false zero.
     */
    static final class Wormholes implements ModIntegrationMetrics.Source {
        @Override
        public String id() {
            return "wormholes";
        }

        @Override
        public Set<String> keys() {
            return Set.of(IntegrationMetricSchema.WORMHOLES_PORTALS, IntegrationMetricSchema.WORMHOLES_PROJECTIONS_ACTIVE,
                    IntegrationMetricSchema.WORMHOLES_PROJECTION_OBSERVERS, IntegrationMetricSchema.WORMHOLES_PROJECTION_RENDER_MS,
                    IntegrationMetricSchema.WORMHOLES_PACKETS_PER_SECOND, IntegrationMetricSchema.WORMHOLES_SPOOFED_ENTITIES,
                    IntegrationMetricSchema.WORMHOLES_TRAVERSALS_PER_MINUTE);
        }

        @Override
        public ModIntegrationMetrics.Reading read(MinecraftServer server, long nowMs) throws ReflectiveOperationException {
            Object runtime = ModReflection.callStatic(ModReflection.require(WORMHOLES_RUNTIME), "forServer", server);
            if (runtime == null) {
                throw new IllegalStateException("Wormholes has not started on this server");
            }
            Object projections = ModReflection.call(runtime, "projections");
            Object portals = ModReflection.call(ModReflection.call(runtime, "portals"), "snapshot");
            Map<String, Double> values = new LinkedHashMap<>();
            values.put(IntegrationMetricSchema.WORMHOLES_PORTALS, (double) ((Collection<?>) portals).size());
            values.put(IntegrationMetricSchema.WORMHOLES_PROJECTIONS_ACTIVE, ModReflection.number(projections, "projectorCount"));
            values.put(IntegrationMetricSchema.WORMHOLES_PROJECTION_OBSERVERS, ModReflection.number(projections, "observerCount"));
            values.put(IntegrationMetricSchema.WORMHOLES_PACKETS_PER_SECOND,
                    ((Number) ModReflection.callStatic(ModReflection.require(WORMHOLES_TELEMETRY), "packetsPerSecond", nowMs)).doubleValue());
            return new ModIntegrationMetrics.Reading(values, List.of());
        }
    }

    /**
     * Adapt's session load, ability check rates and per-ability cost. World policy latency is never recorded by the mod build, so it
     * stays unavailable.
     */
    static final class Adapt implements ModIntegrationMetrics.Source {
        @Override
        public String id() {
            return "adapt";
        }

        @Override
        public Set<String> keys() {
            return Set.of(IntegrationMetricSchema.ADAPT_SESSION_LOAD, IntegrationMetricSchema.ADAPT_ABILITY_OPS,
                    IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS, IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS_TICK,
                    IntegrationMetricSchema.ADAPT_WORLD_POLICY_LATENCY, IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET);
        }

        @Override
        public ModIntegrationMetrics.Reading read(MinecraftServer server, long nowMs) throws ReflectiveOperationException {
            Class<?> checks = ModReflection.require(ADAPT_CHECKS);
            Map<String, Double> values = new LinkedHashMap<>();
            values.put(IntegrationMetricSchema.ADAPT_ABILITY_OPS, ((Number) ModReflection.callStatic(checks, "successfulChecksPerMinute", nowMs)).doubleValue());
            values.put(IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS, ((Number) ModReflection.callStatic(checks, "checksPerMinute", nowMs)).doubleValue());
            values.put(IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS_TICK, ((Number) ModReflection.callStatic(checks, "checksPerTick", nowMs)).doubleValue());
            values.put(IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET, ((Number) ModReflection.callStatic(checks, "timingBudgetPercent", nowMs)).doubleValue());
            Map<?, ?> abilities = (Map<?, ?>) ModReflection.callStatic(checks, "abilitySnapshots", nowMs);
            for (Map.Entry<?, ?> ability : abilities.entrySet()) {
                putAbility(values, String.valueOf(ability.getKey()), ability.getValue());
            }
            Object core = ModReflection.callStatic(ModReflection.require(ADAPT_CORE), "current");
            Object ticker = core == null ? null : ModReflection.call(core, "getTicker");
            if (ticker != null) {
                values.put(IntegrationMetricSchema.ADAPT_SESSION_LOAD, ModReflection.number(ticker, "getWindowLoadPercent"));
            }
            return new ModIntegrationMetrics.Reading(values, List.of());
        }

        /** The four per-ability detail metrics the Bukkit integration publishes for the ability-impact map. */
        private static void putAbility(Map<String, Double> values, String abilityId, Object snapshot) throws ReflectiveOperationException {
            for (String key : IntegrationMetricSchema.adaptAbilityDetailKeys(abilityId)) {
                String accessor = switch (IntegrationMetricSchema.adaptAbilitySignal(key)) {
                    case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_OPS -> "executionOps";
                    case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_TIMING_MS -> "executionTimingMillis";
                    case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_GUARD_CHECKS -> "guardChecks";
                    case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_GUARD_TIMING_MS -> "guardTimingMillis";
                    default -> null;
                };
                if (accessor != null) {
                    values.put(key, ModReflection.number(snapshot, accessor));
                }
            }
        }
    }

    /** The default Adapt ability-ops metric the dashboard shows, as on a fresh Bukkit config. */
    static RendererAdaptMetrics adaptDashboard(ModIntegrationMetrics host) {
        return new RendererAdaptMetrics(host, () -> IntegrationMetricSchema.ADAPT_ABILITY_OPS, () -> "successful");
    }

    static RendererWormholesMetrics wormholesDashboard(ModIntegrationMetrics host) {
        return new RendererWormholesMetrics(host);
    }
}
