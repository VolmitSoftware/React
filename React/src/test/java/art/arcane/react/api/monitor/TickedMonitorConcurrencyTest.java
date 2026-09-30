package art.arcane.react.api.monitor;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.util.common.scheduling.Ticker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class TickedMonitorConcurrencyTest {
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
  void visibilityChangesFromTheOwningThreadDuringATickDoNotBreakSampling() {
    RecordingMonitor monitor = new RecordingMonitor();
    Sampler late = sampler("late", null);
    AtomicBoolean mutated = new AtomicBoolean(false);
    List<Sampler> initial = new ArrayList<>();
    for (int index = 0; index < 4; index++) {
      initial.add(sampler("initial-" + index, () -> {
        if (mutated.compareAndSet(false, true)) {
          monitor.clearVisibility();
          monitor.setVisible(initial, true);
          monitor.setVisible(late, true);
        }
      }));
    }
    monitor.setVisible(initial, true);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      monitor.start();

      Assertions.assertDoesNotThrow(monitor::onTick);
      Assertions.assertDoesNotThrow(monitor::onTick);
    }

    Assertions.assertTrue(mutated.get());
    Assertions.assertTrue(monitor.getSamplers().keySet().containsAll(initial));
    Assertions.assertTrue(monitor.getSamplers().containsKey(late));
  }

  @Test
  void nullSamplersAreNotTrackedAsVisible() {
    RecordingMonitor monitor = new RecordingMonitor();
    List<Sampler> samplers = new ArrayList<>();
    samplers.add(null);
    samplers.add(sampler("present", null));

    Assertions.assertDoesNotThrow(() -> monitor.setVisible(samplers, true));

    Assertions.assertEquals(1, monitor.getVisible().size());
  }

  private static Sampler sampler(String id, Runnable onSample) {
    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.getId()).thenReturn(id);
    Mockito.when(sampler.sample()).thenAnswer(invocation -> {
      if (onSample != null) {
        onSample.run();
      }
      return 1D;
    });
    return sampler;
  }

  private static final class RecordingMonitor extends TickedMonitor {
    private RecordingMonitor() {
      super("concurrency-test", 50L);
    }

    @Override
    public void flush() {
    }
  }
}
