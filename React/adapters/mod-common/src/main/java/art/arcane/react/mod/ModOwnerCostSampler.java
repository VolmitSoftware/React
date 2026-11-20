package art.arcane.react.mod;

import art.arcane.react.api.sampler.ReactEventSampler;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.cost.OwnerCostModel;
import art.arcane.react.platform.cost.OwnerCostSampler;
import art.arcane.volmlib.util.format.Form;

/** One mod's exclusive server-thread time in ms/s, the mod counterpart of Bukkit's plugin cost sampler. */
final class ModOwnerCostSampler extends ReactEventSampler implements OwnerCostSampler {
    private final transient String owner;
    private final transient String displayName;
    private final transient OwnerCostModel model;

    ModOwnerCostSampler(String owner, String displayName, OwnerCostModel model) {
        super(OwnerCostModel.samplerId(owner), 1000);
        this.owner = owner;
        this.displayName = displayName == null || displayName.isBlank() ? owner : displayName;
        this.model = model;
    }

    @Override
    public String getName() {
        return displayName;
    }

    @Override
    public String getIcon() {
        return "minecraft:note_block";
    }

    @Override
    protected double onMeasuredSample(EventMeasurement measurement) {
        return model.sampleAverage(owner);
    }

    @Override
    public double recentCostMsPerSecond() {
        return model.recentMsPerSecond(owner);
    }

    @Override
    public String format(double t) {
        return formattedValue(t) + formattedSuffix(t);
    }

    @Override
    public String formattedValue(double t) {
        return Form.f(t, 2);
    }

    @Override
    public String formattedSuffix(double t) {
        return " ms/s";
    }

    @Override
    public void loadConfiguration() {
    }

    @Override
    public boolean reloadConfiguration() {
        return true;
    }
}
