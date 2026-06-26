package art.arcane.react.api.test;

import art.arcane.react.React;
import art.arcane.react.util.format.C;
import art.arcane.react.util.plugin.VolmitSender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ReactTestRunner {
  private final List<ReactSubsystemCheck> syncChecks;
  private final List<ReactAsyncSubsystemCheck> asyncChecks;

  public ReactTestRunner() {
    this.syncChecks = new ArrayList<ReactSubsystemCheck>();
    this.asyncChecks = new ArrayList<ReactAsyncSubsystemCheck>();
  }

  public ReactTestRunner add(ReactSubsystemCheck check) {
    syncChecks.add(check);
    return this;
  }

  public ReactTestRunner addAsync(ReactAsyncSubsystemCheck check) {
    asyncChecks.add(check);
    return this;
  }

  public void run(VolmitSender out, TestReport report, Consumer<TestReport> onComplete) {
    for (ReactSubsystemCheck check : syncChecks) {
      runSync(out, report, check);
    }
    runAsyncChain(out, report, 0, onComplete);
  }

  private void runSync(VolmitSender out, TestReport report, ReactSubsystemCheck check) {
    int before = report.checks().size();
    try {
      check.run(report);
    } catch (Throwable e) {
      report.fail(check.subsystem(), "exception", e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
      React.reportError(e);
    }
    emit(out, report, before);
  }

  private void runAsyncChain(VolmitSender out, TestReport report, int index, Consumer<TestReport> onComplete) {
    if (index >= asyncChecks.size()) {
      onComplete.accept(report);
      return;
    }

    ReactAsyncSubsystemCheck check = asyncChecks.get(index);
    int before = report.checks().size();
    AtomicBoolean advanced = new AtomicBoolean(false);
    Runnable next = () -> {
      if (!advanced.compareAndSet(false, true)) {
        return;
      }
      emit(out, report, before);
      runAsyncChain(out, report, index + 1, onComplete);
    };

    try {
      check.run(report, next);
    } catch (Throwable e) {
      report.fail(check.subsystem(), "exception", e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
      React.reportError(e);
      next.run();
    }
  }

  private void emit(VolmitSender out, TestReport report, int fromIndex) {
    List<TestCheck> checks = report.checks();
    for (int i = fromIndex; i < checks.size(); i++) {
      TestCheck check = checks.get(i);
      out.sendMessage("  " + tag(check.status()) + C.WHITE + check.subsystem() + "/" + check.name() + C.GRAY + " — " + check.detail());
    }
  }

  private String tag(TestStatus status) {
    return switch (status) {
      case PASS -> C.GREEN + "[PASS] ";
      case FAIL -> C.RED + "[FAIL] ";
      case WARN -> C.YELLOW + "[WARN] ";
      case SKIP -> C.YELLOW + "[SKIP] ";
      case INFO -> C.GRAY + "[INFO] ";
    };
  }
}
