package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.ReactCachedRateSampler;
import art.arcane.volmlib.util.math.RollingSequence;
import org.bukkit.Material;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

class SamplerConfigurationBoundsTest {
  @Test
  void rateSamplerClampsNonPositiveAverageWindow() throws ReflectiveOperationException {
    TestRateSampler sampler = new TestRateSampler();
    setInt(ReactCachedRateSampler.class, sampler, "rollingAverageSamples", 0);

    sampler.start();

    RollingSequence average = (RollingSequence) get(ReactCachedRateSampler.class, sampler, "avg");
    Assertions.assertEquals(1, average.size());
  }

  @Test
  void standaloneAverageSamplersClampNonPositiveWindows() throws ReflectiveOperationException {
    SamplerBacklogGrowthRate backlog = new SamplerBacklogGrowthRate();
    setInt(SamplerBacklogGrowthRate.class, backlog, "averagingSamples", -1);
    backlog.start();
    RollingSequence backlogAverage = (RollingSequence) get(SamplerBacklogGrowthRate.class, backlog, "avg");

    SamplerPingJitter jitter = new SamplerPingJitter();
    setInt(SamplerPingJitter.class, jitter, "averagingSamples", 0);
    jitter.start();
    RollingSequence jitterAverage = (RollingSequence) get(SamplerPingJitter.class, jitter, "avg");

    Assertions.assertEquals(1, backlogAverage.size());
    Assertions.assertEquals(1, jitterAverage.size());
  }

  @Test
  void percentileSamplerClampsHistoryAndResetsWithTheClock() throws ReflectiveOperationException {
    TickClock clock = new TickClock(() -> null);
    SamplerTickPercentileBase sampler = new SamplerTickPercentileBase("test-p95", 0.95D, "ms P95", clock) {
    };
    setInt(SamplerTickPercentileBase.class, sampler, "historyTicks", -1);
    sampler.start();

    long base = 10_000_000_000L;
    clock.tick(base);
    clock.tick(base + 10_000_000L);
    clock.tick(base + 100_000_000L);
    clock.tick(base + 350_000_000L);

    Assertions.assertTrue(sampler.isSampleAvailable());
    Assertions.assertEquals(250D, sampler.onSample(), 1.0E-9D);
    Assertions.assertEquals("ms P95 GAP", sampler.formattedSuffix(250D));

    sampler.stop();
    sampler.start();

    Assertions.assertFalse(sampler.isSampleAvailable());
    Assertions.assertEquals(0D, sampler.onSample(), 1.0E-9D);
  }

  private static Object get(Class<?> owner, Object target, String name) throws ReflectiveOperationException {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static void setInt(Class<?> owner, Object target, String name, int value) throws ReflectiveOperationException {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    field.setInt(target, value);
  }

  private static final class TestRateSampler extends ReactCachedRateSampler {
    private TestRateSampler() {
      super("test-rate", 0L);
    }

    @Override
    public Material getIcon() {
      return Material.CLOCK;
    }

    @Override
    public String formattedValue(double value) {
      return Double.toString(value);
    }

    @Override
    public String formattedSuffix(double value) {
      return "";
    }
  }
}
