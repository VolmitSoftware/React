package art.arcane.react.content.directorcommand;

import art.arcane.react.React;
import art.arcane.react.api.test.ReactTestRunner;
import art.arcane.react.api.test.TestReport;
import art.arcane.react.api.test.TestReportWriter;
import art.arcane.react.api.test.TestStatus;
import art.arcane.react.api.test.checks.ActionSuiteCheck;
import art.arcane.react.api.test.checks.BridgeCheck;
import art.arcane.react.api.test.checks.ErrorScanCheck;
import art.arcane.react.api.test.checks.FeatureLifecycleCheck;
import art.arcane.react.api.test.checks.MapsRenderingCheck;
import art.arcane.react.api.test.checks.MonitoringCheck;
import art.arcane.react.api.test.load.LoadTest;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.format.C;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.nio.file.Path;

@Director(
    name = "test",
    aliases = {"selftest"},
    origin = DirectorOrigin.BOTH,
    description = "React comprehensive in-game self-test and load harness."
)
public class CommandTest implements DirectorExecutor {

  @Director(
      name = "run",
      aliases = {"r"},
      origin = DirectorOrigin.BOTH,
      sync = true,
      description = "Run the full React subsystem self-test and write a JSON report."
  )
  public void run(
      @Param(name = "full", description = "Run the full deep test suite.", defaultValue = "true", aliases = {"f"})
      boolean full,
      @Param(name = "json", description = "Write a JSON report file.", defaultValue = "true", aliases = {"j"})
      boolean json
  ) {
    VolmitSender out = sender();
    String platform = Bukkit.getName();
    String mcVersion = Bukkit.getBukkitVersion();
    boolean folia = J.isFoliaThreading();
    TestReport report = new TestReport(platform, mcVersion, folia, full, System.currentTimeMillis());

    out.sendMessage(C.REACT + "React test run — platform=" + platform + " mc=" + mcVersion + (folia ? " (Folia region-threaded)" : ""));

    ReactTestRunner runner = new ReactTestRunner()
        .add(new MonitoringCheck())
        .add(new MapsRenderingCheck())
        .addAsync(new FeatureLifecycleCheck())
        .addAsync(new ActionSuiteCheck())
        .addAsync(new BridgeCheck())
        .addAsync(new ErrorScanCheck());

    runner.run(out, report, (TestReport done) -> {
      int pass = done.count(TestStatus.PASS);
      int fail = done.count(TestStatus.FAIL);
      int warn = done.count(TestStatus.WARN);
      int skip = done.count(TestStatus.SKIP);
      String verdict = done.passed() ? (C.GREEN + "PASS") : (C.RED + "FAIL");
      String reportPath = "(not written)";
      if (json) {
        try {
          Path path = TestReportWriter.write(done, "selftest");
          reportPath = path.toString();
        } catch (Throwable e) {
          out.sendMessage(C.RED + "Failed to write report: " + e.getMessage());
          React.reportError(e);
        }
      }
      out.sendMessage(C.REACT + "React test: " + verdict + C.GRAY + " pass=" + pass + " fail=" + fail + " warn=" + warn + " skip=" + skip);
      out.sendMessage(C.REACT + "Report: " + reportPath);
    });
  }

  @Director(
      name = "loadtest",
      aliases = {"load"},
      origin = DirectorOrigin.BOTH,
      sync = true,
      description = "Synthetic 1k-player load/stress test with a hard SLO gate and a React-on/off baseline."
  )
  public void loadtest(
      @Param(name = "confirm", description = "Must be true; this spawns heavy synthetic load on world 0.")
      boolean confirm,
      @Param(name = "players", description = "Synthetic player count.", defaultValue = "1000", aliases = {"p"})
      int players,
      @Param(name = "duration", description = "Seconds per pass (React-on then React-off).", defaultValue = "600", aliases = {"d"})
      int duration
  ) {
    VolmitSender out = sender();
    if (!confirm) {
      out.sendMessage(C.REACT + "Refused: pass confirm=true to run the load test (it spawns heavy synthetic load on world 0).");
      return;
    }
    if (Bukkit.getWorlds().isEmpty()) {
      out.sendMessage(C.REACT + "Refused: no worlds loaded.");
      return;
    }

    World world = Bukkit.getWorlds().get(0);
    String platform = Bukkit.getName();
    String mcVersion = Bukkit.getBukkitVersion();
    boolean folia = J.isFoliaThreading();
    TestReport report = new TestReport(platform, mcVersion, folia, true, System.currentTimeMillis());
    out.sendMessage(C.REACT + "Loadtest starting — platform=" + platform + " players=" + players + " duration=" + duration + "s x2 passes");

    new LoadTest(out, players, duration, world, report, (TestReport done) -> {
      String reportPath = "(not written)";
      try {
        Path path = TestReportWriter.write(done, "loadtest");
        reportPath = path.toString();
      } catch (Throwable e) {
        out.sendMessage(C.RED + "Failed to write loadtest report: " + e.getMessage());
        React.reportError(e);
      }
      String verdict = done.passed() ? (C.GREEN + "PASS") : (C.RED + "FAIL");
      out.sendMessage(C.REACT + "Loadtest: " + verdict);
      out.sendMessage(C.REACT + "Report: " + reportPath);
    }).run();
  }
}
