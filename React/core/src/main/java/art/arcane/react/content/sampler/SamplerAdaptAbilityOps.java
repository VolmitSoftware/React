package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

import java.util.function.Supplier;

public class SamplerAdaptAbilityOps extends RemoteIntegrationSampler {
  public static final String ID = "adapt-ability-ops";
  private static volatile Supplier<String> metricKey = () -> IntegrationMetricSchema.ADAPT_ABILITY_OPS;

  public SamplerAdaptAbilityOps() {
    super(ID, "adapt", IntegrationMetricSchema.ADAPT_ABILITY_OPS, 0, " ops/m");
  }

  /** Picks the Adapt metric this sampler reads; null restores successful ability checks. */
  public static void bindMetricKey(Supplier<String> key) {
    metricKey = key == null ? () -> IntegrationMetricSchema.ADAPT_ABILITY_OPS : key;
  }

  @Override
  protected String metricKey() {
    return metricKey.get();
  }

  @Override
  public String getIcon() {
    return "minecraft:bookshelf";
  }
}
