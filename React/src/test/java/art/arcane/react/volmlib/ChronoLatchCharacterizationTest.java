package art.arcane.react.volmlib;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

/** Expected outcomes were recorded from Chrono 22.9.10 with the same scenario code. */
class ChronoLatchCharacterizationTest {
  private static Map<String, String> outcomes;

  @BeforeAll
  static void runScenarios() {
    outcomes = TimingScenarios.latch(VolmLibAdapters.latches());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(delimiter = '|', value = {
      "defaultCouldFlipTwice|true,true",
      "defaultFirstFlip|true",
      "defaultSecondFlip|false",
      "defaultCouldFlipAfterFlip|false",
      "openedFirstFlip|true,false",
      "closedCouldFlip|false",
      "closedFirstFlip|false",
      "flipDownCloses|false,false",
      "closedFlipBeforeInterval|false",
      "closedFlipsAfterInterval|true",
      "flipRearmsInterval|false",
      "couldFlipAfterRearmWait|true",
      "couldFlipDoesNotConsume|true,true,false",
      "zeroIntervalNeedsElapsedMillisecond|true",
      "zeroIntervalFlipsAfterMillisecond|true",
      "negativeClosedAlwaysFlips|true,true,true",
      "maxIntervalNeverOpens|false,false"
  })
  void matchesChrono(String scenario, String expected) {
    Assertions.assertEquals(expected, outcomes.get(scenario));
  }

  @ParameterizedTest(name = "covers {0} scenarios")
  @CsvSource("17")
  void everyScenarioHasAnExpectation(int expected) {
    Assertions.assertEquals(expected, outcomes.size());
  }
}
