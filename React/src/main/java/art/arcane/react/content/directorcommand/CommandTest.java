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
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.nio.file.Path;

@Director(
    name = "test",
    aliases = {"selftest"},
    origin = DirectorOrigin.BOTH,
    description = "Run React validation and load tests",
    descriptionKey = "command.description.test"
)
public class CommandTest implements DirectorExecutor {

  @Director(
      name = "run",
      aliases = {"r"},
      origin = DirectorOrigin.BOTH,
      sync = true,
      description = "Run the React validation suite",
      descriptionKey = "command.description.test.run"
  )
  public void run(
      @Param(name = "full", description = "Run the full deep test suite", descriptionKey = "command.parameter.test.full", defaultValue = "true", aliases = {"f"})
      boolean full,
      @Param(name = "json", description = "Write a JSON report file", descriptionKey = "command.parameter.test.json", defaultValue = "true", aliases = {"j"})
      boolean json
  ) {
    VolmitSender out = sender();
    String platform = Bukkit.getName();
    String mcVersion = Bukkit.getBukkitVersion();
    boolean folia = J.isFoliaThreading();
    TestReport report = new TestReport(platform, mcVersion, folia, full, System.currentTimeMillis());

    ReactLanguage.sendPrefixed(
        out,
        CommandMessages.TEST_RUN_START,
        MessageArgument.untrusted("platform", platform),
        MessageArgument.untrusted("minecraft", mcVersion),
        MessageArgument.untrusted(
            "threading",
            ReactLanguage.plain(folia ? CommandMessages.TEST_THREADING_FOLIA : CommandMessages.TEST_THREADING_STANDARD)
        )
    );

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
      String reportPath = ReactLanguage.plain(CommandMessages.TEST_REPORT_NOT_WRITTEN);
      if (json) {
        try {
          Path path = TestReportWriter.write(done, "selftest");
          reportPath = path.toString();
        } catch (Throwable e) {
          ReactLanguage.sendPrefixed(out, CommandMessages.TEST_REPORT_FAILED, MessageArgument.untrusted("reason", String.valueOf(e.getMessage())));
          React.reportError(e);
        }
      }
      ReactLanguage.sendPrefixed(
          out,
          done.passed() ? CommandMessages.TEST_SUMMARY_PASS : CommandMessages.TEST_SUMMARY_FAIL,
          MessageArgument.untrusted("pass", pass),
          MessageArgument.untrusted("fail", fail),
          MessageArgument.untrusted("warn", warn),
          MessageArgument.untrusted("skip", skip)
      );
      ReactLanguage.sendPrefixed(out, CommandMessages.TEST_REPORT_PATH, MessageArgument.untrusted("path", reportPath));
    });
  }

  @Director(
      name = "loadtest",
      aliases = {"load"},
      origin = DirectorOrigin.BOTH,
      sync = true,
      description = "Run a two-pass synthetic load test",
      descriptionKey = "command.description.test.load"
  )
  public void loadtest(
      @Param(name = "confirm", description = "Must be true; this spawns heavy synthetic load on world 0", descriptionKey = "command.parameter.test.confirm")
      boolean confirm,
      @Param(name = "players", description = "Synthetic player count", descriptionKey = "command.parameter.test.players", defaultValue = "1000", aliases = {"p"})
      int players,
      @Param(name = "duration", description = "Seconds per pass, first with React on and then with React off", descriptionKey = "command.parameter.test.duration", defaultValue = "600", aliases = {"d"})
      int duration
  ) {
    VolmitSender out = sender();
    if (!confirm) {
      ReactLanguage.sendPrefixed(out, CommandMessages.LOAD_CONFIRM_REQUIRED);
      return;
    }
    if (Bukkit.getWorlds().isEmpty()) {
      ReactLanguage.sendPrefixed(out, CommandMessages.LOAD_NO_WORLDS);
      return;
    }

    World world = Bukkit.getWorlds().get(0);
    String platform = Bukkit.getName();
    String mcVersion = Bukkit.getBukkitVersion();
    boolean folia = J.isFoliaThreading();
    TestReport report = new TestReport(platform, mcVersion, folia, true, System.currentTimeMillis());
    ReactLanguage.sendPrefixed(
        out,
        CommandMessages.LOAD_START,
        MessageArgument.untrusted("platform", platform),
        MessageArgument.untrusted("players", players),
        MessageArgument.untrusted("duration", duration)
    );

    new LoadTest(out, players, duration, world, report, (TestReport done) -> {
      String reportPath = ReactLanguage.plain(CommandMessages.TEST_REPORT_NOT_WRITTEN);
      try {
        Path path = TestReportWriter.write(done, "loadtest");
        reportPath = path.toString();
      } catch (Throwable e) {
        ReactLanguage.sendPrefixed(out, CommandMessages.LOAD_REPORT_FAILED, MessageArgument.untrusted("reason", String.valueOf(e.getMessage())));
        React.reportError(e);
      }
      ReactLanguage.sendPrefixed(out, done.passed() ? CommandMessages.LOAD_SUMMARY_PASS : CommandMessages.LOAD_SUMMARY_FAIL);
      ReactLanguage.sendPrefixed(out, CommandMessages.TEST_REPORT_PATH, MessageArgument.untrusted("path", reportPath));
    }).run();
  }
}
