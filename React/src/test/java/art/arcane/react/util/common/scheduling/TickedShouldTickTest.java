package art.arcane.react.util.common.scheduling;

import art.arcane.volmlib.util.math.M;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickedShouldTickTest {
  @Test
  void tickIsDueWithinHalfALoopOfTheInterval() {
    assertFalse(Ticked.isTickDue(1024L, 1000L, 50L));
    assertTrue(Ticked.isTickDue(1025L, 1000L, 50L));
    assertTrue(Ticked.isTickDue(1050L, 1000L, 50L));
    assertTrue(Ticked.isTickDue(1100L, 1000L, 50L));
    assertFalse(Ticked.isTickDue(1974L, 1000L, 1000L));
    assertTrue(Ticked.isTickDue(1975L, 1000L, 1000L));
  }

  @Test
  void defaultShouldTickIsDueOneIntervalAfterTheLastTick() {
    Ticked ticked = new BoundaryTicked(50L);

    assertTrue(ticked.shouldTick());
  }

  private static final class BoundaryTicked implements Ticked {
    private final long interval;
    private final long lastTick;

    private BoundaryTicked(long interval) {
      this.interval = interval;
      this.lastTick = M.ms() - interval;
    }

    @Override
    public void unregister() {
    }

    @Override
    public boolean isBursting() {
      return false;
    }

    @Override
    public boolean isSkipping() {
      return false;
    }

    @Override
    public void stopBursting() {
    }

    @Override
    public void stopSkipping() {
    }

    @Override
    public long getTickCount() {
      return 0L;
    }

    @Override
    public long getAge() {
      return 0L;
    }

    @Override
    public void burst(int ticks) {
    }

    @Override
    public void skip(int ticks) {
    }

    @Override
    public long getTlastTick() {
      return lastTick;
    }

    @Override
    public long getTinterval() {
      return interval;
    }

    @Override
    public void setTinterval(long ms) {
    }

    @Override
    public void tick() {
    }

    @Override
    public String getTgroup() {
      return "test";
    }

    @Override
    public String getTid() {
      return "boundary";
    }
  }
}
