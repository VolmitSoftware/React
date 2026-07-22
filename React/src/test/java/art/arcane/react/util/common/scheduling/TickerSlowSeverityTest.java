package art.arcane.react.util.common.scheduling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TickerSlowSeverityTest {
  @Test
  void longOperationIsHighWhileServerMsptIsHealthy() {
    assertEquals(Ticker.SlowSeverity.HIGH, Ticker.classifySlowSeverity(200L, 49.9D));
  }

  @Test
  void longOperationIsHighWhenServerMsptIsUnavailable() {
    assertEquals(Ticker.SlowSeverity.HIGH, Ticker.classifySlowSeverity(200L, -1D));
    assertEquals(Ticker.SlowSeverity.HIGH, Ticker.classifySlowSeverity(200L, Double.NaN));
  }

  @Test
  void criticalRequiresLongOperationAndImpactedServerMspt() {
    assertEquals(Ticker.SlowSeverity.HIGH, Ticker.classifySlowSeverity(99L, 50D));
    assertEquals(Ticker.SlowSeverity.CRITICAL, Ticker.classifySlowSeverity(100L, 50D));
  }

  @Test
  void lowerSeverityBoundariesRemainStable() {
    assertEquals(Ticker.SlowSeverity.LOW, Ticker.classifySlowSeverity(64L, 60D));
    assertEquals(Ticker.SlowSeverity.MEDIUM, Ticker.classifySlowSeverity(65L, 60D));
    assertEquals(Ticker.SlowSeverity.MEDIUM, Ticker.classifySlowSeverity(79L, 60D));
    assertEquals(Ticker.SlowSeverity.HIGH, Ticker.classifySlowSeverity(80L, 60D));
  }
}
