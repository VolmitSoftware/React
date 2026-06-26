package art.arcane.react.api.test.load;

import art.arcane.react.React;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.test.TestReport;
import art.arcane.react.api.test.TestStatus;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.format.C;
import art.arcane.react.util.plugin.VolmitSender;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class LoadTest {
  private final VolmitSender out;
  private final int players;
  private final int durationSeconds;
  private final World world;
  private final TestReport report;
  private final Consumer<TestReport> onComplete;
  private final SyntheticPlayerLoad synthetic;
  private final WorldLoadGenerator worldLoad;
  private final LoadProfile profile;

  private LoadSummary onSummary;
  private LoadSummary offSummary;

  public LoadTest(VolmitSender out, int players, int durationSeconds, World world, TestReport report, Consumer<TestReport> onComplete) {
    this.out = out;
    this.players = players;
    this.durationSeconds = durationSeconds;
    this.world = world;
    this.report = report;
    this.onComplete = onComplete;
    this.synthetic = new SyntheticPlayerLoad();
    this.worldLoad = new WorldLoadGenerator();
    this.profile = LoadProfile.forPlayers(players);
  }

  public void run() {
    out.sendMessage(C.REACT + "Loadtest pass A (React ON): " + players + " synthetic players, " + durationSeconds + "s");
    runPass((LoadSummary summaryOn) -> {
      onSummary = summaryOn;
      out.sendMessage(C.REACT + "Loadtest pass B (React OFF baseline): " + durationSeconds + "s");
      List<Feature> disabled = disableAllFeatures();
      runPass((LoadSummary summaryOff) -> {
        offSummary = summaryOff;
        restoreFeatures(disabled);
        finish();
      });
    });
  }

  private void runPass(Consumer<LoadSummary> onPassDone) {
    Location center = world.getSpawnLocation();
    LoadRecorder recorder = new LoadRecorder(System.currentTimeMillis());
    synthetic.begin(players, world, 256L);
    worldLoad.begin(world, center, profile);
    long passEndMillis = System.currentTimeMillis() + durationSeconds * 1000L;

    try {
      BukkitTask[] taskRef = new BukkitTask[1];
      taskRef[0] = Bukkit.getScheduler().runTaskTimer(React.instance, () -> {
        if (System.currentTimeMillis() >= passEndMillis) {
          taskRef[0].cancel();
          endPass(recorder, onPassDone);
          return;
        }
        tickOnce(recorder);
      }, 1L, 1L);
    } catch (Throwable e) {
      driveTick(recorder, passEndMillis, onPassDone);
    }
  }

  private void tickOnce(LoadRecorder recorder) {
    try {
      synthetic.tickMovement();
      worldLoad.tick();
      recorder.sampleTick();
    } catch (Throwable e) {
      React.reportError(e);
    }
  }

  private void endPass(LoadRecorder recorder, Consumer<LoadSummary> onPassDone) {
    synthetic.end();
    worldLoad.end();
    onPassDone.accept(recorder.summarize());
  }

  private void driveTick(LoadRecorder recorder, long passEndMillis, Consumer<LoadSummary> onPassDone) {
    if (System.currentTimeMillis() >= passEndMillis) {
      endPass(recorder, onPassDone);
      return;
    }
    J.s(() -> {
      tickOnce(recorder);
      driveTick(recorder, passEndMillis, onPassDone);
    }, 1);
  }

  private List<Feature> disableAllFeatures() {
    FeatureController controller = React.controller(FeatureController.class);
    List<Feature> wasEnabled = new ArrayList<Feature>();
    if (controller == null || controller.getFeatures() == null) {
      return wasEnabled;
    }
    for (Feature feature : new ArrayList<Feature>(controller.getFeatures().all())) {
      if (feature != null && feature.isEnabled() && feature instanceof ReactFeature reactFeature) {
        wasEnabled.add(feature);
        reactFeature.setEnabled(false);
      }
    }
    if (controller.getActiveFeatures() != null) {
      for (Feature feature : new ArrayList<Feature>(controller.getActiveFeatures().values())) {
        try {
          controller.deactivateFeature(feature);
        } catch (Throwable e) {
          React.reportError(e);
        }
      }
    }
    return wasEnabled;
  }

  private void restoreFeatures(List<Feature> wasEnabled) {
    FeatureController controller = React.controller(FeatureController.class);
    if (controller == null) {
      return;
    }
    for (Feature feature : wasEnabled) {
      if (feature instanceof ReactFeature reactFeature) {
        reactFeature.setEnabled(true);
      }
      try {
        controller.activateFeature(feature);
      } catch (Throwable e) {
        React.reportError(e);
      }
    }
  }

  private void finish() {
    SloResult slo = SloGate.evaluate(onSummary);
    String breaches = slo.breaches().isEmpty() ? "all SLOs met" : String.join("; ", slo.breaches());
    report.record("loadtest", "slo-gate", slo.passed() ? TestStatus.PASS : TestStatus.FAIL, breaches, new LinkedHashMap<String, Object>(slo.metrics()));
    report.record("loadtest", "react-on", TestStatus.INFO, passDetail(onSummary), summaryData(onSummary));
    report.record("loadtest", "react-off-baseline", TestStatus.INFO, passDetail(offSummary), summaryData(offSummary));
    report.record("loadtest", "react-overhead-delta", TestStatus.INFO, deltaDetail(), deltaData());
    onComplete.accept(report);
  }

  private String passDetail(LoadSummary summary) {
    return String.format("avgMSPT=%.2f p95=%.2f maxTickGap=%.0fms avgTPS=%.2f heap=%.0f->%.0fMB exceptions=%d",
        summary.avgMspt(), summary.p95Mspt(), summary.maxTickMs(), summary.avgTps(), summary.heapStartMb(), summary.heapEndMb(), summary.reactPathExceptions());
  }

  private String deltaDetail() {
    double dMspt = onSummary.avgMspt() - offSummary.avgMspt();
    double dTps = onSummary.avgTps() - offSummary.avgTps();
    double dHeap = onSummary.heapEndMb() - offSummary.heapEndMb();
    return String.format("React vs baseline: dMSPT=%+.2fms dTPS=%+.2f dHeapEnd=%+.0fMB", dMspt, dTps, dHeap);
  }

  private Map<String, Object> summaryData(LoadSummary summary) {
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("samples", summary.samples());
    data.put("avgMspt", summary.avgMspt());
    data.put("p95Mspt", summary.p95Mspt());
    data.put("maxTickMs", summary.maxTickMs());
    data.put("avgTps", summary.avgTps());
    data.put("minTps", summary.minTps());
    data.put("heapStartMb", summary.heapStartMb());
    data.put("heapEndMb", summary.heapEndMb());
    data.put("heapMaxMb", summary.heapMaxMb());
    data.put("heapMonotonicGrowth", summary.heapMonotonicGrowth());
    data.put("oom", summary.oom());
    data.put("reactPathExceptions", summary.reactPathExceptions());
    return data;
  }

  private Map<String, Object> deltaData() {
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("deltaAvgMspt", onSummary.avgMspt() - offSummary.avgMspt());
    data.put("deltaAvgTps", onSummary.avgTps() - offSummary.avgTps());
    data.put("deltaHeapEndMb", onSummary.heapEndMb() - offSummary.heapEndMb());
    return data;
  }
}
