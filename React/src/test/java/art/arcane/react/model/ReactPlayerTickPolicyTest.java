package art.arcane.react.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ReactPlayerTickPolicyTest {
  @Test
  void tickIsDueAtIntervalBoundary() {
    Assertions.assertFalse(ReactPlayer.isTickDue(1049L, 1000L, 50L));
    Assertions.assertTrue(ReactPlayer.isTickDue(1050L, 1000L, 50L));
    Assertions.assertTrue(ReactPlayer.isTickDue(1100L, 1000L, 50L));
  }

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
