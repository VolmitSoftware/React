package art.arcane.react.content.sampler;

import art.arcane.react.platform.SamplerEventCounters;
import art.arcane.react.api.sampler.ReactCachedRateSampler;
import art.arcane.volmlib.util.format.Form;

public class SamplerChunkUnloads extends ReactCachedRateSampler {
  public static final String ID = "chunk-unloads";

  public SamplerChunkUnloads() {
    super(ID, 1000);
    countWhileRunning(SamplerEventCounters.Counter.CHUNK_UNLOAD, (chunk, amount) -> {
      increment();
      getChunkCounter(chunk).addAndGet(1D);
    });
  }

  @Override
  public String getIcon() {
    return "minecraft:ice";
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
