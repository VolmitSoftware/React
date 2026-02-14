package art.arcane.react.content.sampler;

import art.arcane.react.model.ReactConfiguration;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.bukkit.Material;

public class SamplerAdaptAbilityOps extends RemoteIntegrationSampler {
    public static final String ID = "adapt-ability-ops";

    public SamplerAdaptAbilityOps() {
        super(ID, "adapt", IntegrationMetricSchema.ADAPT_ABILITY_OPS, 0, " ops/m");
    }

    @Override
    protected String metricKey() {
        return ReactConfiguration.adaptAbilityOpsMetricKey();
    }

    @Override
    public Material getIcon() {
        return Material.BLAZE_POWDER;
    }
}
