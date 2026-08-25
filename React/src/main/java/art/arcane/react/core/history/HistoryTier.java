package art.arcane.react.core.history;

public enum HistoryTier {
  RAW(0, "raw", 1_000L, 15L * 60L * 1_000L),
  TEN_SECONDS(1, "10s", 10_000L, 3L * 60L * 60L * 1_000L),
  ONE_MINUTE(2, "1m", 60_000L, 12L * 60L * 60L * 1_000L),
  FIFTEEN_MINUTES(3, "15m", 15L * 60L * 1_000L, 6L * 24L * 60L * 60L * 1_000L),
  ONE_HOUR(4, "1h", 60L * 60L * 1_000L, 30L * 24L * 60L * 60L * 1_000L);

  private final int id;
  private final String directory;
  private final long intervalMs;
  private final long segmentDurationMs;

  HistoryTier(int id, String directory, long intervalMs, long segmentDurationMs) {
    this.id = id;
    this.directory = directory;
    this.intervalMs = intervalMs;
    this.segmentDurationMs = segmentDurationMs;
  }

  public int id() {
    return id;
  }

  public String directory() {
    return directory;
  }

  public long intervalMs() {
    return intervalMs;
  }

  public long segmentDurationMs() {
    return segmentDurationMs;
  }

  public long segmentStart(long timestampMs) {
    return Math.floorDiv(timestampMs, segmentDurationMs) * segmentDurationMs;
  }

  public static HistoryTier byId(int id) {
    for (HistoryTier tier : values()) {
      if (tier.id == id) {
        return tier;
      }
    }
    throw new IllegalArgumentException("Unknown history tier " + id);
  }
}
