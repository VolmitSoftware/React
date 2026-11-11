package art.arcane.react.volmlib;

import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

/**
 * Expected outcomes were recorded from Chrono 22.9.10 with the same scenario code. Timing is
 * reduced to wide bounds inside the scenarios, so only the outcome strings are compared.
 */
class PrecisionStopwatchCharacterizationTest {
  private static final String UNSET = "nanos=-1 millis=-1 startNano=-1 startMillis=-1 time=0.0 nanoseconds=0 profiling=false";
  private static Map<String, String> outcomes;

  @BeforeAll
  static void runScenarios() {
    outcomes = TimingScenarios.stopwatch(VolmLibAdapters.stopwatches());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', value = {
      "fresh|" + UNSET,
      "endWithoutBegin|" + UNSET,
      "resetAfterBegin|" + UNSET,
      "unstartedReadsEpochScale|true",
      "unstartedReadKeepsProfilingOff|true",
      "startIsProfiling|true",
      "startStampsNano|true",
      "startStampsMillis|true",
      "startLeavesElapsedUnset|-1,-1,0.0",
      "endStopsProfiling|true",
      "endTimeIsMilliseconds|true",
      "endNanosAreNanoseconds|true",
      "endMillisAreMilliseconds|true",
      "nanosecondsDeriveFromTime|true",
      "timeIsStableWithoutRead|true",
      "secondEndIsIgnored|true",
      "readAfterEndKeepsCounting|true",
      "readOverwritesTime|true",
      "readOverwritesNanos|true",
      "readWhileRunningKeepsProfiling|true",
      "readWhileRunningIsMilliseconds|true",
      "nanosMatchMilliseconds|true",
      "millisMatchMilliseconds|true",
      "unitsAreOrdered|true",
      "unitsStayNearMilliseconds|true",
      "beginRestarts|true"
  })
  void matchesChrono(String scenario, String expected) {
    Assertions.assertEquals(expected, outcomes.get(scenario));
  }

  @ParameterizedTest(name = "covers {0} scenarios")
  @CsvSource("26")
  void everyScenarioHasAnExpectation(int expected) {
    Assertions.assertEquals(expected, outcomes.size());
  }

  @ParameterizedTest(name = "{0} ms")
  @CsvSource({"30", "120"})
  void millisecondsTrackWallClockSleep(long sleepMs) throws InterruptedException {
    PrecisionStopwatch stopwatch = PrecisionStopwatch.start();
    long started = System.nanoTime();
    Thread.sleep(sleepMs);
    double measured = stopwatch.getMilliseconds();
    double reference = (System.nanoTime() - started) / 1_000_000D;

    Assertions.assertTrue(measured >= sleepMs - 2D, "measured " + measured);
    Assertions.assertTrue(measured <= reference + 2D, "measured " + measured + " reference " + reference);
  }
}
