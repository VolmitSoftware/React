package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.platform.timing.SaveTimings;
import art.arcane.volmlib.util.format.Form;

public class SamplerWorldSaveFrequency extends ReactCachedSampler {
  public static final String ID = "world-save-frequency";

  public SamplerWorldSaveFrequency() {
    super(ID, 1000);
  }

  @Override
  public boolean autoRegister() {
    return SaveTimingSource.timings().isPresent();
  }

  @Override
  public String getIcon() {
    return "minecraft:writable_book";
  }

  @Override
  public boolean isSampleAvailable() {
    return SaveTimingSource.timings().isPresent();
  }

  @Override
  public double onSample() {
    SaveTimings timings = SaveTimingSource.timings().orElse(null);
    return timings == null ? 0D : timings.savesPerHour();
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 1);
  }

  @Override
  public String formattedSuffix(double t) {
    return "SAVES/h";
  }
}
