package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.api.test.ReactTestRunner;
import art.arcane.react.api.test.TestReport;
import art.arcane.react.api.test.TestReportWriter;
import art.arcane.react.api.test.TestStatus;
import art.arcane.react.api.test.checks.ActionSuiteCheck;
import art.arcane.react.api.test.checks.BridgeCheck;
import art.arcane.react.api.test.checks.ErrorScanCheck;
import art.arcane.react.api.test.checks.FeatureLifecycleCheck;
import art.arcane.react.api.test.checks.MapsFrameWallCheck;
import art.arcane.react.api.test.checks.MapsRenderingCheck;
import art.arcane.react.api.test.checks.MonitoringCheck;
import art.arcane.react.api.test.load.LoadTest;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.platform.bukkit.BukkitCommandSender;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.nio.file.Path;

public final class BukkitCommandTest {

  public void run(VolmitSender sender, boolean full, boolean json) {
    VolmitSender out = sender;
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
        .addAsync(new MapsFrameWallCheck())
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

  public void loadtest(VolmitSender sender, boolean confirm, int players, int duration) {
    VolmitSender out = sender;
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
