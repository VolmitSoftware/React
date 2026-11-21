package art.arcane.react.mod;

import art.arcane.react.core.controller.SampleController;
import art.arcane.react.mod.cost.CostKind;
import art.arcane.react.mod.cost.CostRegistry;
import art.arcane.react.mod.cost.ModCostProbe;
import art.arcane.react.mod.cost.WorldgenCost;
import art.arcane.react.mod.cost.WorldgenStep;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.cost.OwnerCostModel;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Per-mod cost on a mod server: the shared owner cost model over the loader's probe, published once a second, with a
 * {@code mod-<id>} sampler registered for every loaded mod except React, as Bukkit does for plugins, plus any other
 * owner the probe reports. World generation is part of each mod's total and also gets its own
 * {@code worldgen-mod-<id>} sampler, a {@code worldgen-step-<step>} sampler per chunk step and a rolling share for
 * the worldgen pie.
 */
final class ModOwnerCost implements EventMeasurement {
    private static final double WORLDGEN_DECAY_PER_WINDOW = 0.96D;
    private static final double WORLDGEN_MIN_SHARE_MS = 0.01D;
    private final ModCostProbe probe;
    private final OwnerCostModel model;
    private final Supplier<SampleController> samples;
    private final Set<String> registered = new HashSet<>();
    private final Set<String> worldgenRegistered = new HashSet<>();
    private final Map<String, Double> worldgenShareMs = new ConcurrentHashMap<>();
    private final Map<String, RollingShare> kindShares = new ConcurrentHashMap<>();
    private boolean startupRegistered;
    private long worldgenWindowAtMs;

    ModOwnerCost(ModCostProbe probe, Supplier<SampleController> samples) {
        this.probe = probe;
        this.model = new OwnerCostModel(probe);
        this.samples = samples;
    }

    OwnerCostModel model() {
        return model;
    }

    void start() {
        model.start();
    }

    void stop() {
        model.stop();
        worldgenShareMs.clear();
        kindShares.clear();
    }

    /** Server thread, once a second. */
    void publish() {
        model.tick();
        foldWorldgenShare();
        SampleController controller = samples.get();
        if (controller == null) {
            return;
        }
        if (!startupRegistered) {
            startupRegistered = true;
            register(controller, CostRegistry.costOwners());
            for (WorldgenStep step : WorldgenStep.values()) {
                controller.registerSampler(ModWorldgenSampler.step(step, model, probe));
            }
        }
        register(controller, model.displayNames());
        registerWorldgen(controller, WorldgenCost.owners());
        registerWorldgen(controller, probe.worldgen().ownerNanos().keySet());
    }

    /** Each owner's rolling world generation milliseconds by display name, for the worldgen pie. */
    Map<String, Long> worldgenShare() {
        Map<String, Long> share = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : worldgenShareMs.entrySet()) {
            share.merge(probe.displayName(entry.getKey()), Math.max(1L, Math.round(entry.getValue() * 1000D)), Long::sum);
        }
        return share;
    }

    /**
     * Each owner's recent ms/s of one kind of cost, in microseconds by display name, for that kind's pie. Reading it
     * keeps the probe measuring, as the other cost maps do.
     */
    Map<String, Long> kindShare(CostKind kind) {
        model.markActivity();
        RollingShare share = kindShares.get(kind.id());
        return share == null ? new LinkedHashMap<>() : share.buckets(1000D, probe::displayName);
    }

    private void foldKindShares() {
        double seconds = model.windowSeconds();
        if (seconds <= 0D) {
            return;
        }
        Map<String, Map<String, Double>> byKind = new LinkedHashMap<>();
        for (CostKind kind : CostKind.values()) {
            byKind.put(kind.id(), new LinkedHashMap<>());
        }
        for (Map.Entry<String, Map<String, Double>> owner : model.snapshotOwnerKindMs().entrySet()) {
            for (Map.Entry<String, Double> kind : owner.getValue().entrySet()) {
                byKind.computeIfAbsent(kind.getKey(), (String id) -> new LinkedHashMap<>())
                        .merge(owner.getKey(), (kind.getValue() == null ? 0D : kind.getValue()) / seconds, Double::sum);
            }
        }
        for (Map.Entry<String, Map<String, Double>> kind : byKind.entrySet()) {
            kindShares.computeIfAbsent(kind.getKey(), (String id) -> new RollingShare()).fold(kind.getValue());
        }
    }

    private void foldWorldgenShare() {
        long windowAt = model.publishedAtMs();
        if (windowAt == worldgenWindowAtMs || !model.measuring()) {
            return;
        }
        worldgenWindowAtMs = windowAt;
        foldKindShares();
        worldgenShareMs.replaceAll((String owner, Double ms) -> ms * WORLDGEN_DECAY_PER_WINDOW);
        for (Map.Entry<String, Long> entry : probe.worldgen().ownerNanos().entrySet()) {
            worldgenShareMs.merge(entry.getKey(), entry.getValue() / 1.0E6D, Double::sum);
        }
        worldgenShareMs.values().removeIf((Double ms) -> ms < WORLDGEN_MIN_SHARE_MS);
    }

    private void registerWorldgen(SampleController controller, Set<String> owners) {
        for (String owner : owners) {
            if (sampled(owner) && worldgenRegistered.add(owner)) {
                controller.registerSampler(ModWorldgenSampler.owner(owner, probe.displayName(owner), model, probe));
            }
        }
    }

    static boolean sampled(String owner) {
        return !owner.isBlank();
    }

    private void register(SampleController controller, Map<String, String> owners) {
        for (Map.Entry<String, String> owner : owners.entrySet()) {
            if (sampled(owner.getKey()) && registered.add(owner.getKey())) {
                controller.registerSampler(new ModOwnerCostSampler(owner.getKey(), owner.getValue(), model));
            }
        }
    }

    @Override
    public boolean isMeasuring() {
        return model.measuring();
    }

    @Override
    public long getWindowPublishedAtMs() {
        return model.publishedAtMs();
    }

    @Override
    public double getWindowSeconds() {
        return model.windowSeconds();
    }

    @Override
    public void markSamplerActivity() {
        model.markActivity();
    }

    @Override
    public double getEventTimeMsPerSecond() {
        return model.totalMsPerSecond();
    }

    @Override
    public Map<String, Double> snapshotPluginEventTimeMS() {
        return model.snapshotMs();
    }

    @Override
    public Map<String, Integer> snapshotPluginEventCalls() {
        return model.snapshotCalls();
    }

    @Override
    public Map<String, String> ownerDisplayNames() {
        return model.displayNames();
    }

    @Override
    public OwnerCostModel ownerCostModel() {
        return model;
    }

    @Override
    public Map<String, String> ownerCatalog() {
        Map<String, String> owners = new TreeMap<>(CostRegistry.costOwners());
        for (Map.Entry<String, String> owner : model.displayNames().entrySet()) {
            owners.putIfAbsent(owner.getKey(), owner.getValue());
        }
        return owners;
    }

    @Override
    public int getListenerCount() {
        return 0;
    }

    @Override
    public double getCallsPerTick() {
        return 0D;
    }
}
