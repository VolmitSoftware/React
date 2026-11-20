package art.arcane.react.platform.cost;

import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Per-owner cost in ms/s, split by kind of work and fed only by an {@link OwnerCostProbe}. Decides when the probe
 * should measure (always, on demand after a read, or in a periodic duty-cycle window) and publishes each drain as a
 * window.
 */
public final class OwnerCostModel {
  public static final int AVERAGE_SAMPLES = 5;
  public static final long DEFAULT_ACTIVITY_WINDOW_MS = 15_000L;
  public static final long DEFAULT_DUTY_ON_MS = 5_000L;
  public static final long DEFAULT_DUTY_PERIOD_MS = 60_000L;
  public static final long DEFAULT_FRESHNESS_MS = 15_000L;
  private static final long MIN_DUTY_PERIOD_MS = 1_000L;
  private static final String PLUGIN_API_PREFIX = "plugin-api-";
  private static final double RECENT_WEIGHT = 0.3D;
  private static final double RECENT_FLOOR_MS_PER_SECOND = 1.0E-4D;
  private static final Window NO_WINDOW = new Window(0D, 0L, 0L, 0L, 0L, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

  private final OwnerCostProbe probe;
  private final LongSupplier clockMs;
  private final LongSupplier clockNanos;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean requested = new AtomicBoolean(false);
  private final Map<String, RollingAverage> averages = new ConcurrentHashMap<>();
  private final Map<String, Double> recent = new ConcurrentHashMap<>();
  private volatile OwnerCostMode mode = OwnerCostMode.ON_DEMAND;
  private volatile long activityWindowMs = DEFAULT_ACTIVITY_WINDOW_MS;
  private volatile long dutyOnMs = DEFAULT_DUTY_ON_MS;
  private volatile long dutyPeriodMs = DEFAULT_DUTY_PERIOD_MS;
  private volatile long freshnessMs = DEFAULT_FRESHNESS_MS;
  private volatile long lastActivityMs;
  private volatile long dutyWindowStartMs;
  private volatile long windowStartNanos;
  private volatile Window window = NO_WINDOW;

  public OwnerCostModel(OwnerCostProbe probe) {
    this(probe, System::currentTimeMillis, System::nanoTime);
  }

  public OwnerCostModel(OwnerCostProbe probe, LongSupplier clockMs, LongSupplier clockNanos) {
    if (probe == null) {
      throw new IllegalArgumentException("probe");
    }
    this.probe = probe;
    this.clockMs = clockMs;
    this.clockNanos = clockNanos;
  }

  public static String samplerId(String owner) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return samplerId(platform == null ? "plugin" : platform.ownerNoun(), owner);
  }

  /**
   * Whether a sampler or renderer id belongs to the per-owner group: {@code plugin-} ids always, plus this
   * platform's own owner prefix.
   */
  public static boolean isOwnerScopedId(String id) {
    return id != null && (id.startsWith("plugin-") || id.startsWith(samplerId("")));
  }

  /**
   * Whether a sampler id is one owner's cost sampler, as opposed to another {@code plugin-} metric such as a Plugin API
   * pack's.
   */
  public static boolean isOwnerCostSamplerId(String id) {
    return isOwnerScopedId(id) && !id.startsWith(PLUGIN_API_PREFIX);
  }

  public static String samplerId(String ownerNoun, String owner) {
    String base = (owner == null ? "" : owner).trim().toLowerCase(Locale.ROOT);
    return ownerNoun + "-" + base.replaceAll("[^a-z0-9_-]+", "-");
  }

  public OwnerCostProbe probe() {
    return probe;
  }

  public OwnerCostMode mode() {
    OwnerCostMode configured = mode;
    return configured == null ? OwnerCostMode.ON_DEMAND : configured;
  }

  public void mode(OwnerCostMode mode) {
    this.mode = mode == null ? OwnerCostMode.ON_DEMAND : mode;
  }

  public void activityWindowMs(long activityWindowMs) {
    this.activityWindowMs = activityWindowMs;
  }

  public void dutyOnMs(long dutyOnMs) {
    this.dutyOnMs = dutyOnMs;
  }

  public void dutyPeriodMs(long dutyPeriodMs) {
    this.dutyPeriodMs = dutyPeriodMs;
  }

  public void freshnessMs(long freshnessMs) {
    this.freshnessMs = freshnessMs;
  }

  public long lastActivityMs() {
    return lastActivityMs;
  }

  public void lastActivityMs(long lastActivityMs) {
    this.lastActivityMs = lastActivityMs;
  }

  public long dutyWindowStartMs() {
    return dutyWindowStartMs;
  }

  public void dutyWindowStartMs(long dutyWindowStartMs) {
    this.dutyWindowStartMs = dutyWindowStartMs;
  }

  public boolean running() {
    return running.get();
  }

  /**
   * Resets windows and demand. The probe is switched on by the next {@link #tick()} or {@link #markActivity()}.
   */
  public void start() {
    lastActivityMs = 0L;
    dutyWindowStartMs = 0L;
    window = NO_WINDOW;
    averages.clear();
    recent.clear();
    requested.set(mode() == OwnerCostMode.ALWAYS);
    beginWindow();
    running.set(true);
  }

  public void stop() {
    running.set(false);
    if (requested.getAndSet(false)) {
      probe.setActive(false);
    }
  }

  public void markActivity() {
    lastActivityMs = clockMs.getAsLong();
    if (running.get()
        && probe.available()
        && mode() != OwnerCostMode.ALWAYS
        && !probe.active()
        && requested.compareAndSet(false, true)) {
      beginWindow();
      probe.setActive(true);
    }
  }

  /**
   * Applies the mode to the probe and publishes whatever it measured since the last drain.
   *
   * @return whether the probe should be measuring now
   */
  public boolean tick() {
    boolean was = requested.get();
    boolean wanted = applyMode();
    if (wanted || was) {
      collect();
    }
    return wanted;
  }

  /**
   * Applies the mode to the probe without draining it, for probes that measure on another thread and call
   * {@link #collect()} themselves once their counters are drained.
   *
   * @return whether the probe should be measuring now
   */
  public boolean applyMode() {
    if (!running.get() || !probe.available()) {
      return false;
    }

    boolean wanted = wanted(clockMs.getAsLong());
    boolean was = requested.getAndSet(wanted);
    if (wanted && !was) {
      beginWindow();
    }
    probe.setActive(wanted);
    return wanted;
  }

  public void beginWindow() {
    windowStartNanos = clockNanos.getAsLong();
  }

  /**
   * Drains the probe and publishes the result as a new window.
   */
  public synchronized void collect() {
    Map<String, Long> nanos = new HashMap<>();
    Map<String, Long> calls = new HashMap<>();
    Map<String, String> names = new HashMap<>();
    Map<String, Map<String, Long>> kindNanos = new HashMap<>();
    long[] totals = new long[3];
    probe.drain((String ownerId, String displayName, String kind, long exclusiveNanos, long syncCalls, long asyncCalls) -> {
      String owner = ownerId == null ? "" : ownerId;
      long ownerNanos = Math.max(0L, exclusiveNanos);
      nanos.merge(owner, ownerNanos, Long::sum);
      calls.merge(owner, Math.max(0L, syncCalls), Long::sum);
      names.putIfAbsent(owner, displayName == null || displayName.isBlank() ? owner : displayName);
      kindNanos.computeIfAbsent(owner, (String ignored) -> new HashMap<>())
          .merge(normalizeKind(kind), ownerNanos, Long::sum);
      totals[0] += ownerNanos;
      totals[1] += Math.max(0L, syncCalls);
      totals[2] += Math.max(0L, asyncCalls);
    });
    long nowNanos = clockNanos.getAsLong();
    long elapsedNanos = nowNanos - windowStartNanos;
    windowStartNanos = nowNanos;
    double windowSeconds = Math.max(1.0E-6D, elapsedNanos / 1.0E9D);
    Map<String, Double> ownerMs = new HashMap<>();
    Map<String, Integer> ownerCalls = new HashMap<>();
    for (Map.Entry<String, Long> entry : nanos.entrySet()) {
      ownerMs.put(entry.getKey(), entry.getValue() / 1.0E6D);
      ownerCalls.put(entry.getKey(), saturatingInt(calls.getOrDefault(entry.getKey(), 0L)));
    }
    Map<String, Map<String, Double>> ownerKindMs = new HashMap<>();
    Map<String, Double> kindMs = new HashMap<>();
    for (Map.Entry<String, Map<String, Long>> owner : kindNanos.entrySet()) {
      Map<String, Double> kinds = new HashMap<>();
      for (Map.Entry<String, Long> kind : owner.getValue().entrySet()) {
        double ms = kind.getValue() / 1.0E6D;
        kinds.put(kind.getKey(), ms);
        kindMs.merge(kind.getKey(), ms, Double::sum);
      }
      ownerKindMs.put(owner.getKey(), Map.copyOf(kinds));
    }
    foldRecent(ownerMs, windowSeconds);
    window = new Window(
        windowSeconds,
        clockMs.getAsLong(),
        totals[0],
        totals[1],
        totals[2],
        Map.copyOf(ownerMs),
        Map.copyOf(ownerCalls),
        Map.copyOf(names),
        Map.copyOf(ownerKindMs),
        Map.copyOf(kindMs)
    );
  }

  private void foldRecent(Map<String, Double> ownerMs, double windowSeconds) {
    for (Map.Entry<String, Double> entry : ownerMs.entrySet()) {
      double current = entry.getValue() / windowSeconds;
      recent.merge(entry.getKey(), current, (Double previous, Double next) -> previous + ((next - previous) * RECENT_WEIGHT));
    }
    recent.replaceAll((String owner, Double previous) -> ownerMs.containsKey(owner) ? previous : previous * (1D - RECENT_WEIGHT));
    recent.values().removeIf((Double value) -> value < RECENT_FLOOR_MS_PER_SECOND);
  }

  static String normalizeKind(String kind) {
    return kind == null || kind.isBlank() ? OwnerCostProbe.KIND_EVENT : kind.trim().toLowerCase(Locale.ROOT);
  }

  public boolean measuring() {
    return isFresh(window);
  }

  public double msPerSecond(String owner) {
    Window published = window;
    return isFresh(published) ? ownerMs(published, owner) / published.windowSeconds() : 0D;
  }

  public double ms(String owner) {
    Window published = window;
    return isFresh(published) ? ownerMs(published, owner) : 0D;
  }

  public double totalMsPerSecond() {
    Window published = window;
    return isFresh(published) ? published.totalMs() / published.windowSeconds() : 0D;
  }

  public Map<String, Double> snapshotMs() {
    Window published = window;
    return isFresh(published) ? new HashMap<>(published.ownerMs()) : new HashMap<>();
  }

  public Map<String, Integer> snapshotCalls() {
    Window published = window;
    return isFresh(published) ? new HashMap<>(published.ownerCalls()) : new HashMap<>();
  }

  /**
   * Milliseconds per owner and kind of work in the published window, keyed by owner id then kind id.
   */
  public Map<String, Map<String, Double>> snapshotOwnerKindMs() {
    Window published = window;
    return isFresh(published) ? published.ownerKindMs() : Map.of();
  }

  /**
   * Milliseconds per kind of work across all owners in the published window.
   */
  public Map<String, Double> snapshotKindMs() {
    Window published = window;
    return isFresh(published) ? published.kindMs() : Map.of();
  }

  /**
   * The owner's smoothed ms/s; it keeps its last value while the probe is not measuring.
   */
  public double recentMsPerSecond(String owner) {
    Double value = owner == null ? null : recent.get(owner);
    return value == null ? 0D : value;
  }

  public Map<String, Double> recentSnapshot() {
    return new HashMap<>(recent);
  }

  public Map<String, String> displayNames() {
    return window.displayNames();
  }

  public double totalMs() {
    return window.totalMs();
  }

  public long calls() {
    return window.syncCalls();
  }

  public long asyncCalls() {
    return window.asyncCalls();
  }

  public double windowSeconds() {
    return window.windowSeconds();
  }

  public long publishedAtMs() {
    return window.publishedAtMs();
  }

  /**
   * Feeds the owner's current ms/s into its {@value #AVERAGE_SAMPLES}-sample rolling average and returns the average.
   */
  public double sampleAverage(String owner) {
    RollingAverage average = averages.computeIfAbsent(owner == null ? "" : owner, ignored -> new RollingAverage(AVERAGE_SAMPLES));
    return average.put(msPerSecond(owner));
  }

  public void forgetOwner(String owner) {
    averages.remove(owner == null ? "" : owner);
    recent.remove(owner == null ? "" : owner);
  }

  private boolean wanted(long nowMs) {
    boolean demanded = nowMs - lastActivityMs <= activityWindowMs;
    return switch (mode()) {
      case ALWAYS -> true;
      case ON_DEMAND -> demanded;
      case DUTY_CYCLE -> demanded || dutyWindowOpen(nowMs);
    };
  }

  private boolean dutyWindowOpen(long nowMs) {
    long period = Math.max(MIN_DUTY_PERIOD_MS, dutyPeriodMs);
    long on = Math.max(1L, Math.min(dutyOnMs, period));
    long windowStart = dutyWindowStartMs;
    if (windowStart == 0L || nowMs - windowStart >= period) {
      dutyWindowStartMs = nowMs;
      return true;
    }

    return nowMs - windowStart < on;
  }

  private boolean isFresh(Window published) {
    long publishedAt = published.publishedAtMs();
    return running.get() && publishedAt > 0L && clockMs.getAsLong() - publishedAt <= freshnessMs;
  }

  private static double ownerMs(Window published, String owner) {
    Double time = owner == null ? null : published.ownerMs().get(owner);
    return time == null ? 0D : time;
  }

  private static int saturatingInt(long value) {
    return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
  }

  private record Window(double windowSeconds, long publishedAtMs, long totalNanos, long syncCalls, long asyncCalls,
                        Map<String, Double> ownerMs, Map<String, Integer> ownerCalls,
                        Map<String, String> displayNames, Map<String, Map<String, Double>> ownerKindMs,
                        Map<String, Double> kindMs) {
    private double totalMs() {
      return totalNanos / 1.0E6D;
    }
  }

  private static final class RollingAverage {
    private final double[] values;
    private int cursor;
    private boolean seeded;

    private RollingAverage(int size) {
      values = new double[size];
    }

    private synchronized double put(double value) {
      if (!seeded) {
        Arrays.fill(values, value);
        seeded = true;
      } else {
        values[cursor] = value;
        cursor = cursor + 1 < values.length ? cursor + 1 : 0;
      }

      double sum = 0D;
      for (double sample : values) {
        sum += sample;
      }
      return sum / values.length;
    }
  }
}
