package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.Ticker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class SamplerTicksPerSecondTest {
  @Test
  void syncCadenceUsesElapsedTimeBetweenServerTicks() {
    React previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      SamplerTicksPerSecond sampler = new SamplerTicksPerSecond();
      long firstTick = System.currentTimeMillis() + 1000L;

      sampler.onSyncTick(firstTick);
      sampler.onSyncTick(firstTick + 100L);
      Assertions.assertEquals(10D, sampler.onSample(firstTick + 100L), 0.0001D);

      sampler.onSyncTick(firstTick + 150L);
      Assertions.assertEquals(20D, sampler.onSample(firstTick + 150L), 0.0001D);
    } finally {
      React.instance = previous;
    }
  }

  @Test
  void timeSinceLastTickWinsWhenTheServerStalls() {
    React previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      SamplerTicksPerSecond sampler = new SamplerTicksPerSecond();
      long firstTick = System.currentTimeMillis() + 1000L;

      sampler.onSyncTick(firstTick);
      sampler.onSyncTick(firstTick + 50L);

      Assertions.assertEquals(2D, sampler.onSample(firstTick + 550L), 0.0001D);
    } finally {
      React.instance = previous;
    }
  }

  @Test
  void samplerRestartRecreatesTheSynchronousTickTask() {
    React previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(0))).thenReturn(17, 23);
      SamplerTicksPerSecond sampler = new SamplerTicksPerSecond();

      sampler.start();
      sampler.stop();
      sampler.start();

      scheduling.verify(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(0)), Mockito.times(2));
      scheduling.verify(() -> J.csr(17));
    } finally {
      React.instance = previous;
    }
  }
}
