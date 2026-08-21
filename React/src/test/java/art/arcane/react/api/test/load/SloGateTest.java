package art.arcane.react.api.test.load;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SloGateTest {
  private LoadSummary healthy() {
    return new LoadSummary(600, 18.0, 25.0, 120.0, 19.8, 19.0, 2000.0, 2100.0, 4096.0, false, false, 0);
  }

  @Test
  void healthySummaryPasses() {
    SloResult result = SloGate.evaluate(healthy());
    Assertions.assertTrue(result.passed(), "unexpected breaches: " + result.breaches());
    Assertions.assertTrue(result.breaches().isEmpty());
  }

  @Test
  void lowTpsFails() {
    LoadSummary summary = new LoadSummary(600, 70.0, 90.0, 200.0, 12.0, 9.0, 2000.0, 2100.0, 4096.0, false, false, 0);
    SloResult result = SloGate.evaluate(summary);
    Assertions.assertFalse(result.passed());
    Assertions.assertTrue(result.breaches().stream().anyMatch(breach -> breach.contains("TPS")));
  }

  @Test
  void highMsptFails() {
    LoadSummary summary = new LoadSummary(600, 80.0, 120.0, 300.0, 19.0, 18.0, 2000.0, 2100.0, 4096.0, false, false, 0);
    SloResult result = SloGate.evaluate(summary);
    Assertions.assertFalse(result.passed());
    Assertions.assertTrue(result.breaches().stream().anyMatch(breach -> breach.contains("MSPT")));
  }

  @Test
  void mainThreadFreezeFails() {
    LoadSummary summary = new LoadSummary(600, 20.0, 30.0, 1500.0, 19.0, 18.0, 2000.0, 2100.0, 4096.0, false, false, 0);
    SloResult result = SloGate.evaluate(summary);
    Assertions.assertFalse(result.passed());
    Assertions.assertTrue(result.breaches().stream().anyMatch(breach -> breach.contains("freeze")));
  }

  @Test
  void outOfMemoryFails() {
    LoadSummary summary = new LoadSummary(600, 20.0, 30.0, 100.0, 19.5, 19.0, 2000.0, 2100.0, 4096.0, false, true, 0);
    SloResult result = SloGate.evaluate(summary);
    Assertions.assertFalse(result.passed());
    Assertions.assertTrue(result.breaches().stream().anyMatch(breach -> breach.contains("OutOfMemory")));
  }

  @Test
  void monotonicHeapGrowthBeyondThresholdFails() {
    LoadSummary summary = new LoadSummary(600, 20.0, 30.0, 100.0, 19.5, 19.0, 1000.0, 1400.0, 1500.0, true, false, 0);
    SloResult result = SloGate.evaluate(summary);
    Assertions.assertFalse(result.passed());
    Assertions.assertTrue(result.breaches().stream().anyMatch(breach -> breach.contains("heap")));
  }

  @Test
  void boundedHeapGrowthPasses() {
    LoadSummary summary = new LoadSummary(600, 20.0, 30.0, 100.0, 19.5, 19.0, 1000.0, 1100.0, 1500.0, true, false, 0);
    SloResult result = SloGate.evaluate(summary);
    Assertions.assertTrue(result.passed(), "unexpected breaches: " + result.breaches());
  }

  @Test
  void reactPathExceptionsFail() {
    LoadSummary summary = new LoadSummary(600, 20.0, 30.0, 100.0, 19.5, 19.0, 2000.0, 2100.0, 4096.0, false, false, 3);
    SloResult result = SloGate.evaluate(summary);
    Assertions.assertFalse(result.passed());
    Assertions.assertTrue(result.breaches().stream().anyMatch(breach -> breach.contains("exception")));
  }
}
