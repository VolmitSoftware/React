package art.arcane.react.api.test.load;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.TestMessages;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
      breaches.add(ReactLanguage.raw(
          TestMessages.SLO_LOW_TPS,
          MessageArgument.untrusted("average", format("%.2f", summary.avgTps())),
          MessageArgument.untrusted("minimum", format("%.1f", MIN_TPS))
      ));
    }
    if (summary.avgMspt() >= MAX_AVG_MSPT) {
      breaches.add(ReactLanguage.raw(
          TestMessages.SLO_HIGH_MSPT,
          MessageArgument.untrusted("average", format("%.2f", summary.avgMspt())),
          MessageArgument.untrusted("maximum", format("%.1f", MAX_AVG_MSPT))
      ));
    }
    if (summary.maxTickMs() > FREEZE_TICK_MS) {
      breaches.add(ReactLanguage.raw(
          TestMessages.SLO_FREEZE,
          MessageArgument.untrusted("tick", format("%.0f", summary.maxTickMs())),
          MessageArgument.untrusted("maximum", format("%.0f", FREEZE_TICK_MS))
      ));
    }
    if (summary.oom()) {
      breaches.add(ReactLanguage.raw(TestMessages.SLO_OOM));
    }
    double heapGrowth = summary.heapEndMb() - summary.heapStartMb();
    if (summary.heapMonotonicGrowth() && heapGrowth > MAX_HEAP_GROWTH_MB) {
      breaches.add(ReactLanguage.raw(
          TestMessages.SLO_HEAP_GROWTH,
          MessageArgument.untrusted("growth", format("%.0f", heapGrowth))
      ));
    }
    if (summary.reactPathExceptions() > 0) {
      breaches.add(ReactLanguage.raw(
          TestMessages.SLO_EXCEPTIONS,
          MessageArgument.untrusted("count", summary.reactPathExceptions())
      ));
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

  private static String format(String format, double value) {
    return String.format(Locale.ROOT, format, value);
  }
}
