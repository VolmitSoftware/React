package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.history.MetricSnapshotValue;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.react.util.plugin.IController;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public final class RuntimeDiagnosticsController extends TickedObject implements IController {
  private final List<String> registeredIds = new ArrayList<>();
  private volatile Snapshot snapshot = new Snapshot(0L, 0L, new double[Metric.values().length]);
  private Totals previous;
  private long previousNanos;
  private volatile boolean active;

  public RuntimeDiagnosticsController() {
    super("react", "runtime-diagnostics", 1_000L);
  }

  @Override
  public String getName() {
    return "Runtime Diagnostics";
  }

  @Override
  public void start() {
    active = true;
    previous = null;
    previousNanos = 0L;
    snapshot = new Snapshot(0L, 0L, new double[Metric.values().length]);
    registeredIds.clear();
  }

  @Override
  public void postStart() {
    SampleController controller = React.controller(SampleController.class);
    if (controller == null) {
      return;
    }
    for (Metric metric : Metric.values()) {
      if (controller.registerSampler(new DiagnosticSampler(metric))) {
        registeredIds.add(metric.id);
      }
    }
  }

  @Override
  public void stop() {
    active = false;
    SampleController controller = React.controller(SampleController.class);
    if (controller != null) {
      for (String id : registeredIds) {
        controller.unregisterSampler(id);
      }
    }
    registeredIds.clear();
    snapshot = new Snapshot(0L, 0L, new double[Metric.values().length]);
  }

  @Override
  public void onTick() {
    if (!active) {
      return;
    }
    SampleController controller = React.controller(SampleController.class);
    Ticker ticker = React.instance.getTicker();
    if (controller == null || controller.getSamplers() == null || ticker == null) {
      return;
    }
    long nowNanos = System.nanoTime();
    long nowMs = System.currentTimeMillis();
    Totals current = collect(controller.getSamplers().all(), ticker.diagnostics());
    Totals prior = previous;
    long elapsedNanos = nowNanos - previousNanos;
    previous = current;
    previousNanos = nowNanos;
    if (prior == null || elapsedNanos <= 0L) {
      return;
    }
    double[] values = new double[Metric.values().length];
    double seconds = elapsedNanos / 1_000_000_000D;
    put(values, Metric.SAMPLER_REFRESHES, rate(current.attempts, prior.attempts, seconds));
    put(values, Metric.SAMPLER_FAILURES, rate(current.failures, prior.failures, seconds));
    put(values, Metric.SAMPLER_CACHE_HITS, rate(current.cacheHits, prior.cacheHits, seconds));
    put(values, Metric.SAMPLER_SKIPS, rate(current.skips, prior.skips, seconds));
    put(values, Metric.SAMPLER_RUNTIME, rate(current.runtime, prior.runtime, seconds) / 1_000_000D);
    put(values, Metric.SAMPLER_QUEUE, rate(current.queue, prior.queue, seconds) / 1_000_000D);
    put(values, Metric.SAMPLER_MAX_RUNTIME, current.maxRuntime / 1_000_000D);
    put(values, Metric.SAMPLER_MAX_QUEUE, current.maxQueue / 1_000_000D);
    Ticker.Diagnostics t = current.ticker;
    Ticker.Diagnostics p = prior.ticker;
    put(values, Metric.TICKER_SUBMITTED, rate(t.submitted(), p.submitted(), seconds));
    put(values, Metric.TICKER_EXECUTED, rate(t.executed(), p.executed(), seconds));
    put(values, Metric.TICKER_FAILED, rate(t.failures(), p.failures(), seconds));
    put(values, Metric.TICKER_SKIPPED, rate(t.skipped(), p.skipped(), seconds));
    put(values, Metric.TICKER_REJECTED, rate(t.rejected(), p.rejected(), seconds));
    put(values, Metric.TICKER_BACKOFF, rate(t.backedOff(), p.backedOff(), seconds));
    put(values, Metric.TICKER_RUNTIME, rate(t.runtimeNanos(), p.runtimeNanos(), seconds) / 1_000_000D);
    put(values, Metric.TICKER_QUEUE, rate(t.queueNanos(), p.queueNanos(), seconds) / 1_000_000D);
    put(values, Metric.TICKER_MAX_QUEUE, t.maxQueueNanos() / 1_000_000D);
    put(values, Metric.TICKER_ACTIVE, t.active());
    HistoryController history = React.controller(HistoryController.class);
    if (history != null) {
      long oldestAgeMs = 0L;
      int stale = 0;
      for (MetricSnapshotValue value : history.latest().values()) {
        if (value.sampledAtMs() <= 0L) {
          continue;
        }
        long ageMs = Math.max(0L, nowMs - value.sampledAtMs());
        oldestAgeMs = Math.max(oldestAgeMs, ageMs);
        long interval = value.metadata().intervalMs();
        if (value.available() && interval > 0L && ageMs > Math.max(3_000L, interval * 3L)) {
          stale++;
        }
      }
      put(values, Metric.SAMPLER_OLDEST, oldestAgeMs);
      put(values, Metric.SAMPLER_STALE, stale);
    }
    snapshot = new Snapshot(nowMs, Math.max(1L, elapsedNanos / 1_000_000L), values);
  }

  private static Totals collect(Iterable<Sampler> samplers, Ticker.Diagnostics ticker) {
    long attempts = 0L;
    long failures = 0L;
    long cacheHits = 0L;
    long skips = 0L;
    long runtime = 0L;
    long queue = 0L;
    long maxRuntime = 0L;
    long maxQueue = 0L;
    for (Sampler sampler : samplers) {
      Sampler.Diagnostics diagnostics = sampler.diagnostics();
      attempts += diagnostics.attempts();
      failures += diagnostics.failures();
      cacheHits += diagnostics.cacheHits();
      skips += diagnostics.inFlightSkips();
      runtime += diagnostics.totalDurationNanos();
      queue += diagnostics.totalQueueNanos();
      maxRuntime = Math.max(maxRuntime, diagnostics.maxDurationNanos());
      maxQueue = Math.max(maxQueue, diagnostics.maxQueueNanos());
    }
    return new Totals(attempts, failures, cacheHits, skips, runtime, queue, maxRuntime, maxQueue, ticker);
  }

  private static double rate(long current, long previous, double seconds) {
    return Math.max(0L, current - previous) / seconds;
  }

  private static void put(double[] values, Metric metric, double value) {
    values[metric.ordinal()] = value;
  }

  private record Totals(long attempts, long failures, long cacheHits, long skips, long runtime,
                        long queue, long maxRuntime, long maxQueue, Ticker.Diagnostics ticker) {
  }

  private record Snapshot(long sampledAtMs, long windowMs, double[] values) {
  }

  private enum Metric {
    SAMPLER_REFRESHES("react-sampler-refreshes", "Sampler Refreshes", "/s"),
    SAMPLER_FAILURES("react-sampler-refresh-failures", "Sampler Refresh Failures", "/s"),
    SAMPLER_CACHE_HITS("react-sampler-cache-hits", "Sampler Cache Hits", "/s"),
    SAMPLER_SKIPS("react-sampler-refresh-skips", "Sampler In-flight Skips", "/s"),
    SAMPLER_RUNTIME("react-sampler-refresh-ms", "Sampler Refresh Time", "ms/s"),
    SAMPLER_QUEUE("react-sampler-queue-ms", "Sampler Queue Wait", "ms/s"),
    SAMPLER_MAX_RUNTIME("react-sampler-max-refresh-ms", "Maximum Sampler Refresh", "ms"),
    SAMPLER_MAX_QUEUE("react-sampler-max-queue-ms", "Maximum Sampler Queue Wait", "ms"),
    SAMPLER_OLDEST("react-sampler-oldest-age-ms", "Oldest Sampler Source Age", "ms"),
    SAMPLER_STALE("react-sampler-stale", "Available Stale Samplers", "count"),
    TICKER_SUBMITTED("react-ticker-submitted", "Ticker Submissions", "/s"),
    TICKER_EXECUTED("react-ticker-executed", "Ticker Executions", "/s"),
    TICKER_FAILED("react-ticker-failed", "Ticker Failures", "/s"),
    TICKER_SKIPPED("react-ticker-skipped", "Ticker In-flight Skips", "/s"),
    TICKER_REJECTED("react-ticker-rejected", "Ticker Rejections", "/s"),
    TICKER_BACKOFF("react-ticker-backoff", "Ticker Ownership Backoffs", "/s"),
    TICKER_RUNTIME("react-ticker-runtime-ms", "Ticker Execution Time", "ms/s"),
    TICKER_QUEUE("react-ticker-queue-ms", "Ticker Queue Wait", "ms/s"),
    TICKER_MAX_QUEUE("react-ticker-max-queue-ms", "Maximum Ticker Queue Wait", "ms"),
    TICKER_ACTIVE("react-ticker-active", "Active Ticker Executions", "count");

    private final String id;
    private final String title;
    private final String unit;

    Metric(String id, String title, String unit) {
      this.id = id;
      this.title = title;
      this.unit = unit;
    }
  }

  private final class DiagnosticSampler implements Sampler {
    private final Metric metric;
    private volatile boolean started;

    private DiagnosticSampler(Metric metric) {
      this.metric = metric;
    }

    @Override
    public String getId() {
      return metric.id;
    }

    @Override
    public String getName() {
      return metric.title;
    }

    @Override
    public Material getIcon() {
      return Material.COMPARATOR;
    }

    @Override
    public void loadConfiguration() {
    }

    @Override
    public void start() {
      started = true;
    }

    @Override
    public void stop() {
      started = false;
    }

    @Override
    public Metadata metadata() {
      return new Metadata(1_000L, snapshot.windowMs, Quality.MEASURED, metric.unit);
    }

    @Override
    public Reading captureReading() {
      Snapshot captured = snapshot;
      boolean available = started && active && captured.sampledAtMs > 0L
          && System.currentTimeMillis() - captured.sampledAtMs <= 5_000L;
      return new Reading(captured.values[metric.ordinal()], captured.sampledAtMs, available,
          new Metadata(1_000L, captured.windowMs, Quality.MEASURED, metric.unit),
          captured.sampledAtMs == 0L ? "warming-up" : "stale");
    }

    @Override
    public double sample() {
      return captureReading().value();
    }

    @Override
    public boolean isSampleAvailable() {
      return captureReading().available();
    }

    @Override
    public String formattedValue(double value) {
      return Form.f(value, 2);
    }

    @Override
    public String formattedSuffix(double value) {
      return metric.unit;
    }
  }
}
