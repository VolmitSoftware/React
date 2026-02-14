package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerAdaptSessionLoad extends RemoteIntegrationSampler {
    public static final String ID = "adapt-session-load";

    public SamplerAdaptSessionLoad() {
        super(ID, "adapt", IntegrationMetricSchema.ADAPT_SESSION_LOAD, 1, " %");
    }

    @Override
    public Material getIcon() {
        return Material.COMPASS;
    }
}
