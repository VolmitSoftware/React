package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.volmlib.util.math.RollingSequence;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Chunk;
import org.bukkit.event.world.ChunkLoadEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

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

  @Test
  void listenerTimeDecaysToZeroOnceChunkLoadsStop() {
    AtomicLong nanos = new AtomicLong(1_000_000_000L);
    SamplerChunkLoadListenerMS sampler = new SamplerChunkLoadListenerMS(nanos::get);
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.get(ArgumentMatchers.any(Chunk.class), ArgumentMatchers.any(Sampler.class)))
        .thenAnswer(invocation -> new AtomicDouble());

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      sampler.start();
      Assertions.assertEquals(0D, sampler.onSample(), 1.0E-9D);

      load(sampler, nanos, 78_000L);
      Assertions.assertEquals(0.078D, sampler.onSample(), 1.0E-9D);

      nanos.addAndGet(9_000_000_000L);
      Assertions.assertEquals(0.078D, sampler.onSample(), 1.0E-9D);

      nanos.addAndGet(2_000_000_000L);
      Assertions.assertEquals(0D, sampler.onSample(), 1.0E-9D);

      nanos.addAndGet(420_000_000_000L);
      Assertions.assertEquals(0D, sampler.onSample(), 1.0E-9D);

      load(sampler, nanos, 2_000_000L);
      Assertions.assertEquals(2D, sampler.onSample(), 1.0E-9D);
    }
  }

  private static void load(SamplerChunkEventDurationBase sampler, AtomicLong nanos, long durationNanos) {
    ChunkLoadEvent event = Mockito.mock(ChunkLoadEvent.class);
    Mockito.when(event.getChunk()).thenReturn(Mockito.mock(Chunk.class));
    sampler.onStart(event);
    nanos.addAndGet(durationNanos);
    sampler.onEnd(event);
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
