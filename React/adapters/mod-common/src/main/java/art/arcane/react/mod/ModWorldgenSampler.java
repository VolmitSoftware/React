package art.arcane.react.mod;

import art.arcane.react.api.sampler.ReactEventSampler;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.mod.cost.ModCostProbe;
import art.arcane.react.mod.cost.WorldgenStep;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.cost.OwnerCostModel;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;

/**
 * World generation time in ms/s on the generator threads, either one mod's features, structures and carvers
 * ({@code worldgen-mod-<id>}) or one whole chunk step ({@code worldgen-step-<step>}).
 */
final class ModWorldgenSampler extends ReactEventSampler {
    static final String PREFIX = "worldgen-";
    private final transient String owner;
    private final transient WorldgenStep step;
    private final transient String displayName;
    private final transient OwnerCostModel model;
    private final transient ModCostProbe probe;

    private ModWorldgenSampler(String id, String owner, WorldgenStep step, String displayName, OwnerCostModel model, ModCostProbe probe) {
        super(id, 1000);
        this.owner = owner;
        this.step = step;
        this.displayName = displayName;
        this.model = model;
        this.probe = probe;
    }

    static String ownerId(String owner) {
        return PREFIX + OwnerCostModel.samplerId(owner);
    }

    static String stepId(WorldgenStep step) {
        return PREFIX + "step-" + step.id();
    }

    static ModWorldgenSampler owner(String owner, String displayName, OwnerCostModel model, ModCostProbe probe) {
        String name = displayName == null || displayName.isBlank() ? owner : displayName;
        return new ModWorldgenSampler(ownerId(owner), owner, null, name, model, probe);
    }

    static ModWorldgenSampler step(WorldgenStep step, OwnerCostModel model, ModCostProbe probe) {
        return new ModWorldgenSampler(stepId(step), null, step, null, model, probe);
    }

    @Override
    public String getName() {
        if (step != null) {
            return MapMessages.localizedSamplerName(getId(), getId());
        }
        return ReactLanguage.plain(MapMessages.SAMPLER_WORLDGEN_OWNER, MessageArgument.untrusted("name", displayName));
    }

    @Override
    public String getIcon() {
        return "minecraft:grass_block";
    }

    @Override
    protected double onMeasuredSample(EventMeasurement measurement) {
        ModCostProbe.Worldgen window = probe.worldgen();
        long nanos = step == null ? window.ownerNanos(owner) : window.stepNanos(step);
        return nanos / 1.0E6D / Math.max(1.0E-6D, model.windowSeconds());
    }

    @Override
    public Metadata metadata() {
        Metadata metadata = super.metadata();
        return new Metadata(metadata.intervalMs(), metadata.windowMs(), metadata.quality(), "ms/s");
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
