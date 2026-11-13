package art.arcane.react.core.history;

public enum ChartRange {
  HOUR("1h", 10_000L),
  SIX_HOURS("6h", 60_000L),
  DAY("24h", 240_000L);

  public static final int BUCKETS = 360;

  private final String id;
  private final long stepMs;

  ChartRange(String id, long stepMs) {
    this.id = id;
    this.stepMs = stepMs;
  }

  public String id() {
    return id;
  }

  public long stepMs() {
    return stepMs;
  }

  public long spanMs() {
    return stepMs * BUCKETS;
  }

  public static ChartRange byId(String id) {
    for (ChartRange range : values()) {
      if (range.id.equals(id)) {
        return range;
      }
    }
    return null;
  }
}
