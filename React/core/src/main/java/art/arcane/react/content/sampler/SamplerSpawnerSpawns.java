package art.arcane.react.content.sampler;

import art.arcane.react.platform.SamplerEventCounters;
import art.arcane.react.api.sampler.ReactCachedRateSampler;
import art.arcane.volmlib.util.format.Form;

public class SamplerSpawnerSpawns extends ReactCachedRateSampler {
  public static final String ID = "spawner-spawns";

  public SamplerSpawnerSpawns() {
    super(ID, 1000);
    countWhileRunning(SamplerEventCounters.Counter.SPAWNER_SPAWN, (subject, amount) -> increment());
  }

  @Override
  public String getIcon() {
    return "minecraft:spawner";
  }



  @Override
  public String formattedValue(double t) {
    return Form.f(t, 1);
  }

  @Override
  public String formattedSuffix(double t) {
    return "/s";
  }
}
