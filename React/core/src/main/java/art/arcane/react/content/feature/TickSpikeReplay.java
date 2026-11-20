package art.arcane.react.content.feature;

import art.arcane.react.core.controller.LoadedChunkCoordinate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decaying per-chunk heat left by tick spikes around the chunk that was costliest when each spike happened.
 */
public final class TickSpikeReplay {
  public static final Settings DEFAULT_SETTINGS = new Settings() {
  };

  private final Settings settings;
  private final Map<ChunkKey, HeatCell> heat = new ConcurrentHashMap<>();
  private volatile long lastCaptureMs;
  private volatile long lastDecayMs;

  public TickSpikeReplay(Settings settings) {
    this.settings = settings;
  }

  public interface Settings {
    default double spikeThresholdMS() {
      return 50D;
    }

    default int spikeCaptureCooldownMS() {
      return 350;
    }

    default int captureRadiusChunks() {
      return 3;
    }

    default int maxTrackedChunks() {
      return 4096;
    }

    default int staleChunkMS() {
      return 120000;
    }

    default int decayEveryMS() {
      return 500;
    }

    default double decayFactor() {
      return 0.90D;
    }

    default double minimumHeat() {
      return 0.15D;
    }
  }

  /**
   * Per-chunk cost used to weight heat around the spike origin.
   */
  @FunctionalInterface
  public interface ChunkScore {
    double score(int chunkX, int chunkZ);
  }

  public void reset(long nowMs) {
    heat.clear();
    lastCaptureMs = nowMs;
    lastDecayMs = nowMs;
  }

  public void clear() {
    heat.clear();
  }

  public Settings settings() {
    return settings;
  }

  /**
   * True when {@code tickMs} is a spike and the capture cooldown has passed; the caller then captures it.
   */
  public boolean claimCapture(long nowMs, double tickMs) {
    if (tickMs >= settings.spikeThresholdMS() && nowMs - lastCaptureMs >= settings.spikeCaptureCooldownMS()) {
      lastCaptureMs = nowMs;
      return true;
    }
    return false;
  }

  public void decayIfDue(long nowMs) {
    if (nowMs - lastDecayMs < settings.decayEveryMS()) {
      return;
    }
    lastDecayMs = nowMs;
    for (Map.Entry<ChunkKey, HeatCell> entry : heat.entrySet()) {
      HeatCell cell = entry.getValue();
      cell.heat *= settings.decayFactor();
      if (cell.heat < settings.minimumHeat() || nowMs - cell.lastSeenMs > settings.staleChunkMS()) {
        heat.remove(entry.getKey(), cell);
      }
    }
  }

  /**
   * Heat for the loaded chunks around the origin, weighted by distance and by each chunk's own cost.
   */
  public void captureScored(UUID worldId, int originX, int originZ, Iterable<LoadedChunkCoordinate> loaded,
                            ChunkScore score, double tickMs, long nowMs) {
    double spikePressure = spikePressure(tickMs);
    for (LoadedChunkCoordinate coordinate : loaded) {
      int dx = coordinate.chunkX() - originX;
      int dz = coordinate.chunkZ() - originZ;
      double distance = Math.sqrt((dx * dx) + (dz * dz));
      double localScore = Math.max(0D, score.score(coordinate.chunkX(), coordinate.chunkZ()));
      double distanceWeight = 1D / (1D + distance);
      double scoreWeight = 1D + (Math.min(localScore, 250D) / 120D);
      addHeat(worldId, coordinate.chunkX(), coordinate.chunkZ(), spikePressure * distanceWeight * scoreWeight, nowMs);
    }
    addHeat(worldId, originX, originZ, spikePressure * 1.8D, nowMs);
  }

  /**
   * Heat for every chunk in the capture radius, weighted by distance only.
   */
  public void captureUniform(UUID worldId, int originX, int originZ, double tickMs, long nowMs) {
    if (worldId == null) {
      return;
    }
    int radius = Math.max(0, settings.captureRadiusChunks());
    double spikePressure = spikePressure(tickMs);
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        double distance = Math.sqrt((dx * dx) + (dz * dz));
        double distanceWeight = 1D / (1D + distance);
        addHeat(worldId, originX + dx, originZ + dz, spikePressure * distanceWeight, nowMs);
      }
    }
    addHeat(worldId, originX, originZ, spikePressure * 1.8D, nowMs);
  }

  public void addHeat(UUID worldId, int chunkX, int chunkZ, double amount, long nowMs) {
    if (worldId == null || amount <= 0D) {
      return;
    }
    ChunkKey key = new ChunkKey(worldId, chunkX, chunkZ);
    HeatCell cell = heat.computeIfAbsent(key, (ChunkKey ignored) -> heat.size() >= settings.maxTrackedChunks() ? null : new HeatCell());
    if (cell == null) {
      return;
    }
    cell.heat = Math.min(10000D, cell.heat + amount);
    cell.lastSeenMs = nowMs;
  }

  public double heat(UUID worldId, int chunkX, int chunkZ) {
    if (worldId == null) {
      return 0D;
    }
    HeatCell cell = heat.get(new ChunkKey(worldId, chunkX, chunkZ));
    return cell == null ? 0D : Math.max(0D, cell.heat);
  }

  public int trackedChunks() {
    return heat.size();
  }

  private double spikePressure(double tickMs) {
    return Math.max(1D, tickMs - settings.spikeThresholdMS() + 1D);
  }

  private static final class HeatCell {
    private double heat;
    private long lastSeenMs;
  }

  private record ChunkKey(UUID world, int x, int z) {
  }
}
