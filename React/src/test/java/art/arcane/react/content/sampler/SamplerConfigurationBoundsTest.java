package art.arcane.react.content.sampler;

import art.arcane.react.api.event.layer.ServerTickEvent;
import art.arcane.react.api.sampler.ReactCachedRateSampler;
import art.arcane.volmlib.util.math.RollingSequence;
import org.bukkit.Material;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;

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
  void percentileSamplerClampsHistoryAndClearsItOnRestart() throws ReflectiveOperationException {
    SamplerTickMsP95 sampler = new SamplerTickMsP95();
    setInt(SamplerTickPercentileBase.class, sampler, "historyTicks", -1);
    sampler.start();
    setLong(SamplerTickPercentileBase.class, sampler, "lastTickMS", System.currentTimeMillis() - 50L);

    sampler.on(new ServerTickEvent());
    setLong(SamplerTickPercentileBase.class, sampler, "lastTickMS", System.currentTimeMillis() - 50L);
    sampler.on(new ServerTickEvent());

    ArrayDeque<?> history = (ArrayDeque<?>) get(SamplerTickPercentileBase.class, sampler, "tickDurations");
    Assertions.assertEquals(1, history.size());

    sampler.stop();
    sampler.start();

    Assertions.assertTrue(history.isEmpty());
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

  private static void setLong(Class<?> owner, Object target, String name, long value) throws ReflectiveOperationException {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    field.setLong(target, value);
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
