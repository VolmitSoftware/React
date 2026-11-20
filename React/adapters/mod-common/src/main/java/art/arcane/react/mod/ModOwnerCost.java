package art.arcane.react.mod;

import art.arcane.react.core.controller.SampleController;
import art.arcane.react.mod.cost.CostRegistry;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.cost.OwnerCostModel;
import art.arcane.react.platform.cost.OwnerCostProbe;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Per-mod cost on a mod server: the shared owner cost model over the loader's probe, published once a second, with a
 * {@code mod-<id>} sampler registered for every loaded mod except React, as Bukkit does for plugins, plus any other
 * owner the probe reports.
 */
final class ModOwnerCost implements EventMeasurement {
    private final OwnerCostModel model;
    private final Supplier<SampleController> samples;
    private final Set<String> registered = new HashSet<>();
    private boolean loadedModsRegistered;

    ModOwnerCost(OwnerCostProbe probe, Supplier<SampleController> samples) {
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
    }

    /** Server thread, once a second. */
    void publish() {
        model.tick();
        SampleController controller = samples.get();
        if (controller == null) {
            return;
        }
        if (!loadedModsRegistered) {
            loadedModsRegistered = true;
            register(controller, CostRegistry.costOwners());
        }
        register(controller, model.displayNames());
    }

    static boolean sampled(String owner) {
        return !owner.isBlank() && !owner.equals(ReactModRuntime.ID);
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
    public int getListenerCount() {
        return 0;
    }

    @Override
    public double getCallsPerTick() {
        return 0D;
    }
}
