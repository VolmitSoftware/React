package art.arcane.react.platform.cost;

import art.arcane.volmlib.util.format.Form;

/**
 * A per-owner reading that is not time: chunks an owner force-loads and the network traffic of its channels. Each
 * owner gets one sampler per gauge, with the id {@code <prefix>-<owner>}.
 */
public enum OwnerGauge {
  CHUNK_LOADERS("chunk-loader", "Forced Chunks", "minecraft:lodestone", "count", 5_000L),
  NETWORK_OUT("network-out", "Network Out", "minecraft:observer", "bytes/s", 1_000L),
  NETWORK_IN("network-in", "Network In", "minecraft:observer", "bytes/s", 1_000L),
  PACKETS_OUT("packets-out", "Packets Out", "minecraft:repeater", "/s", 1_000L),
  PACKETS_IN("packets-in", "Packets In", "minecraft:repeater", "/s", 1_000L);

  private static final OwnerGauge[] ALL = values();
  private final String prefix;
  private final String label;
  private final String icon;
  private final String unit;
  private final long intervalMs;

  OwnerGauge(String prefix, String label, String icon, String unit, long intervalMs) {
    this.prefix = prefix;
    this.label = label;
    this.icon = icon;
    this.unit = unit;
    this.intervalMs = intervalMs;
  }

  public String samplerId(String owner) {
    return OwnerCostModel.samplerId(prefix, owner);
  }

  public String label() {
    return label;
  }

  public String icon() {
    return icon;
  }

  public String unit() {
    return unit;
  }

  public long intervalMs() {
    return intervalMs;
  }

  public boolean network() {
    return this != CHUNK_LOADERS;
  }

  /** The gauge a sampler id belongs to, or null. */
  public static OwnerGauge forId(String id) {
    if (id == null) {
      return null;
    }
    for (OwnerGauge gauge : ALL) {
      if (id.length() > gauge.prefix.length() + 1 && id.startsWith(gauge.prefix) && id.charAt(gauge.prefix.length()) == '-') {
        return gauge;
      }
    }
    return null;
  }

  public String formattedValue(double value) {
    return switch (this) {
      case CHUNK_LOADERS -> Form.f(Math.round(value));
      case NETWORK_OUT, NETWORK_IN -> Form.memSizeSplit(Math.max(0L, Math.round(value)), 1)[0];
      case PACKETS_OUT, PACKETS_IN -> Form.f(value, 1);
    };
  }

  public String formattedSuffix(double value) {
    return switch (this) {
      case CHUNK_LOADERS -> " chunks";
      case NETWORK_OUT, NETWORK_IN -> " " + Form.memSizeSplit(Math.max(0L, Math.round(value)), 1)[1] + "/s";
      case PACKETS_OUT, PACKETS_IN -> " packets/s";
    };
  }
}
