package art.arcane.react.core.telemetry.timing;

import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.platform.timing.WorldTickTimes;
import art.arcane.volmlib.util.format.Form;

import java.util.UUID;

/**
 * One world's measured tick time in ms per tick, averaged over the last published window.
 */
final class WorldTickSampler extends ReactCachedSampler {
  private final transient UUID worldId;
  private final transient String worldKey;
  private final transient WorldTickTimes times;

  WorldTickSampler(String id, UUID worldId, String worldKey, WorldTickTimes times) {
    super(id, 1000);
    this.worldId = worldId;
    this.worldKey = worldKey;
    this.times = times;
  }

  @Override
  public String getName() {
    return worldKey;
  }

  @Override
  public String getIcon() {
    return "minecraft:recovery_compass";
  }

  @Override
  public boolean isSampleAvailable() {
    return times.world(worldId).isPresent();
  }

  @Override
  public double onSample() {
    return times.world(worldId).map(WorldTickTimes.World::meanMs).orElse(0D);
  }

  @Override
  public Metadata metadata() {
    return new Metadata(sampleDelay, 1_000L, Quality.MEASURED, "ms");
  }

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 2);
  }

  @Override
  public String formattedSuffix(double t) {
    return "ms/t";
  }

  @Override
  public void loadConfiguration() {
  }

  @Override
  public boolean reloadConfiguration() {
    return true;
  }
}
