package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.Ticker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class SamplerMemoryPressureTest {
  private static final long MIB = 1024L * 1024L;

  private React previous;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void allocationRateUsesTheMeasuredGapBetweenSamples() {
    AtomicLong used = new AtomicLong(512L * MIB);
    AtomicLong nanos = new AtomicLong(TimeUnit.SECONDS.toNanos(100L));
    SamplerMemoryPressure sampler = new SamplerMemoryPressure(used::get, nanos::get);
    sampler.onSample();

    nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(100L));
    used.addAndGet(10L * MIB);

    Assertions.assertEquals(100D * MIB, sampler.onSample(), 1.0D);
  }

  @Test
  void firstSampleAfterALongIdleGapReportsNoAllocationSpike() {
    AtomicLong used = new AtomicLong(512L * MIB);
    AtomicLong nanos = new AtomicLong(TimeUnit.SECONDS.toNanos(100L));
    SamplerMemoryPressure sampler = new SamplerMemoryPressure(used::get, nanos::get);
    nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(50L));
    used.addAndGet(MIB);
    sampler.onSample();

    nanos.addAndGet(TimeUnit.MINUTES.toNanos(10L));
    used.addAndGet(2048L * MIB);
    Assertions.assertEquals(0D, sampler.onSample(), 1.0E-9D);

    nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(50L));
    used.addAndGet(5L * MIB);
    Assertions.assertEquals(100D * MIB, sampler.onSample(), 1.0D);
  }
}
