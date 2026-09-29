package art.arcane.react.util.common.scheduling;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TickerCadenceTest {
  @Test
  void fiftyMillisecondTickFiresOnEveryFiftyMillisecondLoop() {
    AtomicLong clock = new AtomicLong(1_000L);
    ClockTicked ticked = new ClockTicked(clock::get, "cadence", 50L);
    Ticker ticker = new Ticker(Runnable::run);
    try {
      ticker.register(ticked);
      ticker.tick();

      for (int loop = 0; loop < 20; loop++) {
        clock.addAndGet(50L);
        ticker.tick();
      }

      assertEquals(20, ticked.runs);
    } finally {
      ticker.close();
    }
  }

  @Test
  void loopDelayKeepsAFixedFiftyMillisecondRate() {
    assertEquals(50L, Ticker.nextDelayMS(0L));
    assertEquals(30L, Ticker.nextDelayMS(20L));
    assertEquals(0L, Ticker.nextDelayMS(50L));
    assertEquals(0L, Ticker.nextDelayMS(120L));
  }

  @Test
  void inFlightTickIsNotDispatchedAgainUntilItFinishes() {
    AtomicLong clock = new AtomicLong(1_000L);
    List<Runnable> queued = new ArrayList<>();
    ClockTicked ticked = new ClockTicked(clock::get, "slow", 50L);
    Ticker ticker = new Ticker(queued::add);
    try {
      ticker.register(ticked);
      ticker.tick();

      for (int loop = 0; loop < 3; loop++) {
        clock.addAndGet(50L);
        ticker.tick();
      }
      assertEquals(1, queued.size());

      queued.remove(0).run();
      clock.addAndGet(50L);
      ticker.tick();

      assertEquals(1, queued.size());
      assertEquals(1, ticked.runs);
    } finally {
      ticker.close();
    }
  }
}
