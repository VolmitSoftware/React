package art.arcane.react.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ReactPlayerTickPolicyTest {
  @Test
  void activeRuntimeSleepsOnlyAfterIdleDelay() {
    Assertions.assertFalse(ReactPlayer.shouldUseInactiveRate(11_000L, 1_000L, 50L));
    Assertions.assertTrue(ReactPlayer.shouldUseInactiveRate(11_001L, 1_000L, 50L));
  }

  @Test
  void inactiveRuntimeDoesNotReapplySleepRate() {
    Assertions.assertFalse(ReactPlayer.shouldUseInactiveRate(30_000L, 1_000L, 1_000L));
  }
}
