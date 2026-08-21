package art.arcane.react.api.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TestReport {
  private final String platform;
  private final String mcVersion;
  private final boolean foliaThreading;
  private final boolean full;
  private final long startedAtMillis;
  private int bridgeAvailable;
  private int bridgeUnavailable;
  private final List<TestCheck> checks;

  public TestReport(String platform, String mcVersion, boolean foliaThreading, boolean full, long startedAtMillis) {
    this.platform = platform;
    this.mcVersion = mcVersion;
    this.foliaThreading = foliaThreading;
    this.full = full;
    this.startedAtMillis = startedAtMillis;
    this.checks = new ArrayList<TestCheck>();
  }

  public void setBridgeHealth(int available, int unavailable) {
    this.bridgeAvailable = available;
    this.bridgeUnavailable = unavailable;
  }

  public void add(TestCheck check) {
    checks.add(check);
  }

  public void record(String subsystem, String name, TestStatus status, String detail) {
    checks.add(new TestCheck(subsystem, name, status, detail, null));
  }

  public void record(String subsystem, String name, TestStatus status, String detail, Map<String, Object> data) {
    checks.add(new TestCheck(subsystem, name, status, detail, data));
  }

  public void pass(String subsystem, String name, String detail) {
    record(subsystem, name, TestStatus.PASS, detail);
  }

  public void fail(String subsystem, String name, String detail) {
    record(subsystem, name, TestStatus.FAIL, detail);
  }

  public void warn(String subsystem, String name, String detail) {
    record(subsystem, name, TestStatus.WARN, detail);
  }

  public void skip(String subsystem, String name, String detail) {
    record(subsystem, name, TestStatus.SKIP, detail);
  }

  public void info(String subsystem, String name, String detail) {
    record(subsystem, name, TestStatus.INFO, detail);
  }

  public boolean passed() {
    for (TestCheck check : checks) {
      if (check.status() == TestStatus.FAIL) {
        return false;
      }
    }
    return true;
  }

  public int count(TestStatus status) {
    int total = 0;
    for (TestCheck check : checks) {
      if (check.status() == status) {
        total++;
      }
    }
    return total;
  }

  public List<TestCheck> checks() {
    return checks;
  }

  public String platform() {
    return platform;
  }

  public String mcVersion() {
    return mcVersion;
  }

  public boolean foliaThreading() {
    return foliaThreading;
  }

  public boolean full() {
    return full;
  }

  public long startedAtMillis() {
    return startedAtMillis;
  }

  public int bridgeAvailable() {
    return bridgeAvailable;
  }

  public int bridgeUnavailable() {
    return bridgeUnavailable;
  }
}
