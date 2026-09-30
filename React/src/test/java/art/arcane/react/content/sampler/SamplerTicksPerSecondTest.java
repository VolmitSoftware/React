package art.arcane.react.content.sampler;

import art.arcane.react.React;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SamplerTicksPerSecondTest {
  private static final long MS = 1_000_000L;

  @Test
  void steadyTicksReadTwentyTicksPerSecond() {
    TickClock clock = new TickClock(() -> null);
    SamplerTicksPerSecond sampler = new SamplerTicksPerSecond(clock);
    sampler.start();
    long at = System.nanoTime() - 9_850L * MS;
    for (int i = 0; i < 196; i++) {
      at += 50L * MS;
      clock.tick(at);
    }

    Assertions.assertTrue(sampler.isSampleAvailable());
    Assertions.assertEquals(20D, sampler.onSample(), 1.0E-9D);
    Assertions.assertEquals("20", sampler.formattedValue(20D));
    Assertions.assertEquals("TPS", sampler.formattedSuffix(20D));
  }

  @Test
  void halvedTickRateReadsTenTicksPerSecond() {
    TickClock clock = new TickClock(() -> null);
    SamplerTicksPerSecond sampler = new SamplerTicksPerSecond(clock);
    sampler.start();
    long at = System.nanoTime() - 10_050L * MS;
    for (int i = 0; i < 100; i++) {
      at += 100L * MS;
      clock.tick(at);
    }

    Assertions.assertEquals(10D, sampler.onSample(), 1.0E-9D);
    Assertions.assertEquals("10", sampler.formattedValue(10.1D));
  }

  @Test
  void halfSecondTicksReadTwoTicksPerSecond() {
    TickClock clock = new TickClock(() -> null);
    SamplerTicksPerSecond sampler = new SamplerTicksPerSecond(clock);
    sampler.start();
    long at = System.nanoTime() - 10_050L * MS;
    for (int i = 0; i < 20; i++) {
      at += 500L * MS;
      clock.tick(at);
    }

    Assertions.assertEquals(2D, sampler.onSample(), 1.0E-9D);
    Assertions.assertEquals("2", sampler.formattedValue(2D));
  }

  @Test
  void stalledServerCountsUpSinceTheLastTick() {
    TickClock clock = new TickClock(() -> null);
    SamplerTicksPerSecond sampler = new SamplerTicksPerSecond(clock);
    sampler.start();
    long at = System.nanoTime() - 14_000L * MS;
    for (int i = 0; i < 196; i++) {
      at += 50L * MS;
      clock.tick(at);
    }

    Assertions.assertTrue(sampler.onSample() < 5D);
    Assertions.assertEquals("4.2", sampler.formattedValue(3D));
    Assertions.assertEquals("s", sampler.formattedSuffix(3D));
  }

  @Test
  void unavailableBeforeTheFirstTick() {
    TickClock clock = new TickClock(() -> null);
    SamplerTicksPerSecond sampler = new SamplerTicksPerSecond(clock);
    sampler.start();

    Assertions.assertFalse(sampler.isSampleAvailable());
    Assertions.assertEquals(0D, sampler.onSample(), 1.0E-9D);
    Assertions.assertEquals("TPS", sampler.formattedSuffix(0D));
  }

  @Test
  void restartReacquiresTheSharedClock() {
    React previous = React.instance;
    React plugin = Mockito.mock(React.class);
    React.instance = plugin;
    try {
      TickClock clock = new TickClock(() -> null);
      SamplerTicksPerSecond sampler = new SamplerTicksPerSecond(clock);

      sampler.start();
      sampler.stop();
      sampler.start();

      Mockito.verify(plugin, Mockito.times(2)).registerListener(clock);
      Mockito.verify(plugin, Mockito.times(1)).unregisterListener(clock);
    } finally {
      React.instance = previous;
    }
  }
}
