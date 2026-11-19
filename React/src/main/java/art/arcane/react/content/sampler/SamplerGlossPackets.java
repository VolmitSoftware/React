package art.arcane.react.content.sampler;

import art.arcane.volmlib.integration.IntegrationMetricSchema;

public class SamplerGlossPackets extends RemoteIntegrationSampler {
  public static final String ID = "gloss-packets";

  public SamplerGlossPackets() {
    super(ID, "gloss", IntegrationMetricSchema.GLOSS_PACKETS_PER_SECOND, 0, " pkt/s");
  }

  @Override
  public String getIcon() {
    return "minecraft:repeater";
  }
}
