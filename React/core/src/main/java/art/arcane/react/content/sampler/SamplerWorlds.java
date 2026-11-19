package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.platform.WorldStatsHost;
import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.volmlib.util.format.Form;

public class SamplerWorlds extends ReactCachedSampler {
  public static final String ID = "worlds";

  public SamplerWorlds() {
    super(ID, 5000);
  }

  @Override
  public String getIcon() {
    return "minecraft:globe_banner_pattern";
  }

  @Override
  public double onSample() {
    return SamplerHosts.worlds().map(WorldStatsHost::worldCount).orElse(0);
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(Math.round(t));
  }

  @Override
  public String formattedSuffix(double t) {
    return " worlds";
  }
}
