package art.arcane.react.api.test.load;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class SloGateTest {
  private LoadSummary healthy() {
    return new LoadSummary(600, 18.0, 25.0, 120.0, 19.8, 19.0, 2000.0, 2100.0, 4096.0, false, false, 0);
  }

  static Stream<Arguments> breachingSummaries() {
    return Stream.of(
        Arguments.of(new LoadSummary(600, 70.0, 90.0, 200.0, 12.0, 9.0, 2000.0, 2100.0, 4096.0, false, false, 0), "TPS"),
        Arguments.of(new LoadSummary(600, 80.0, 120.0, 300.0, 19.0, 18.0, 2000.0, 2100.0, 4096.0, false, false, 0), "MSPT"),
        Arguments.of(new LoadSummary(600, 20.0, 30.0, 1500.0, 19.0, 18.0, 2000.0, 2100.0, 4096.0, false, false, 0), "freeze"),
        Arguments.of(new LoadSummary(600, 20.0, 30.0, 100.0, 19.5, 19.0, 2000.0, 2100.0, 4096.0, false, true, 0), "OutOfMemory"),
        Arguments.of(new LoadSummary(600, 20.0, 30.0, 100.0, 19.5, 19.0, 1000.0, 1400.0, 1500.0, true, false, 0), "heap"),
        Arguments.of(new LoadSummary(600, 20.0, 30.0, 100.0, 19.5, 19.0, 2000.0, 2100.0, 4096.0, false, false, 3), "exception")
    );
  }

  @Test
  void healthySummaryPasses() {
    SloResult result = SloGate.evaluate(healthy());
    Assertions.assertTrue(result.passed(), "unexpected breaches: " + result.breaches());
    Assertions.assertTrue(result.breaches().isEmpty());
  }

  @Test
  void boundedHeapGrowthPasses() {
    LoadSummary summary = new LoadSummary(600, 20.0, 30.0, 100.0, 19.5, 19.0, 1000.0, 1100.0, 1500.0, true, false, 0);
    SloResult result = SloGate.evaluate(summary);
    Assertions.assertTrue(result.passed(), "unexpected breaches: " + result.breaches());
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("breachingSummaries")
  void evaluateFailsAndNamesTheBreachedSlo(LoadSummary summary, String expectedBreach) {
    SloResult result = SloGate.evaluate(summary);
    Assertions.assertFalse(result.passed());
    Assertions.assertTrue(result.breaches().stream().anyMatch(breach -> breach.contains(expectedBreach)),
        "no breach mentioning " + expectedBreach + " in " + result.breaches());
  }
}
