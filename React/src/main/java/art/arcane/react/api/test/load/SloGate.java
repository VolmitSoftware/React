package art.arcane.react.api.test.load;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SloGate {
  public static final double MIN_TPS = 18.0;
  public static final double MAX_AVG_MSPT = 50.0;
  public static final double FREEZE_TICK_MS = 1000.0;
  public static final double MAX_HEAP_GROWTH_MB = 256.0;

  private SloGate() {
  }

  public static SloResult evaluate(LoadSummary summary) {
    List<String> breaches = new ArrayList<String>();
    if (summary.avgTps() < MIN_TPS) {
      breaches.add(String.format("low TPS: avg %.2f < %.1f", summary.avgTps(), MIN_TPS));
    }
    if (summary.avgMspt() >= MAX_AVG_MSPT) {
      breaches.add(String.format("high MSPT: avg %.2fms >= %.1fms", summary.avgMspt(), MAX_AVG_MSPT));
    }
    if (summary.maxTickMs() > FREEZE_TICK_MS) {
      breaches.add(String.format("main-thread freeze: %.0fms tick > %.0fms", summary.maxTickMs(), FREEZE_TICK_MS));
    }
    if (summary.oom()) {
      breaches.add("OutOfMemory observed during run");
    }
    double heapGrowth = summary.heapEndMb() - summary.heapStartMb();
    if (summary.heapMonotonicGrowth() && heapGrowth > MAX_HEAP_GROWTH_MB) {
      breaches.add(String.format("unbounded heap growth: +%.0fMB with no GC recovery", heapGrowth));
    }
    if (summary.reactPathExceptions() > 0) {
      breaches.add("React-path exceptions: " + summary.reactPathExceptions());
    }

    Map<String, Double> metrics = new LinkedHashMap<String, Double>();
    metrics.put("avgMspt", summary.avgMspt());
    metrics.put("p95Mspt", summary.p95Mspt());
    metrics.put("maxTickMs", summary.maxTickMs());
    metrics.put("avgTps", summary.avgTps());
    metrics.put("minTps", summary.minTps());
    metrics.put("heapStartMb", summary.heapStartMb());
    metrics.put("heapEndMb", summary.heapEndMb());
    metrics.put("heapGrowthMb", heapGrowth);
    metrics.put("reactPathExceptions", (double) summary.reactPathExceptions());

    return new SloResult(breaches.isEmpty(), breaches, metrics);
  }
}
