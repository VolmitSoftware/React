package art.arcane.react.content.sampler;

import art.arcane.volmlib.util.math.RollingSequence;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

class SamplerChunkEventDurationBaseTest {
  @Test
  void listenerSamplersUseTheListenerIds() {
    Assertions.assertEquals("chunk-load-listener-ms", new SamplerChunkLoadListenerMS().getId());
    Assertions.assertEquals("chunk-gen-listener-ms", new SamplerChunkGenListenerMS().getId());
  }

  @Test
  void startBuildsHistoryFromTheLoadedConfiguration() throws ReflectiveOperationException {
    SamplerChunkLoadListenerMS sampler = new SamplerChunkLoadListenerMS();
    setMaxHistory(sampler, 3);

    sampler.start();

    Assertions.assertEquals(3, average(sampler).size());
  }

  @Test
  void restartRebuildsHistoryAfterAConfigurationChange() throws ReflectiveOperationException {
    SamplerChunkLoadListenerMS sampler = new SamplerChunkLoadListenerMS();
    setMaxHistory(sampler, 3);
    sampler.start();
    RollingSequence initial = average(sampler);

    setMaxHistory(sampler, 7);
    sampler.stop();
    sampler.start();
    RollingSequence reloaded = average(sampler);

    Assertions.assertNotSame(initial, reloaded);
    Assertions.assertEquals(7, reloaded.size());
  }

  @Test
  void nonPositiveHistoryIsClampedToOneSample() throws ReflectiveOperationException {
    SamplerChunkLoadListenerMS sampler = new SamplerChunkLoadListenerMS();
    setMaxHistory(sampler, 0);

    sampler.start();

    Assertions.assertEquals(1, average(sampler).size());
  }

  private void setMaxHistory(SamplerChunkEventDurationBase sampler, int maxHistory) throws ReflectiveOperationException {
    Field field = SamplerChunkEventDurationBase.class.getDeclaredField("maxHistory");
    field.setAccessible(true);
    field.setInt(sampler, maxHistory);
  }

  private RollingSequence average(SamplerChunkEventDurationBase sampler) throws ReflectiveOperationException {
    Field field = SamplerChunkEventDurationBase.class.getDeclaredField("average");
    field.setAccessible(true);
    return (RollingSequence) field.get(sampler);
  }
}
