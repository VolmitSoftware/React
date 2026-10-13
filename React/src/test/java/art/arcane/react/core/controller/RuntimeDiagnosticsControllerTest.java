package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.history.MetricSnapshot;
import art.arcane.react.core.history.MetricSnapshotValue;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.react.util.project.registry.Registry;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeDiagnosticsControllerTest {
  @Test
  void registersPublishesAndInvalidatesDiagnosticReadings() {
    React previous = React.instance;
    React plugin = Mockito.mock(React.class);
    React.instance = plugin;
    SampleController samples = Mockito.mock(SampleController.class);
    HistoryController history = Mockito.mock(HistoryController.class);
    Ticker ticker = new Ticker(Runnable::run);
    @SuppressWarnings("unchecked")
    Registry<Sampler> registry = Mockito.mock(Registry.class);
    List<Sampler> registered = new ArrayList<>();
    Mockito.when(plugin.getTicker()).thenReturn(ticker);
    Mockito.when(samples.getSamplers()).thenReturn(registry);
    Mockito.when(registry.all()).thenReturn(registered);
    Mockito.when(samples.registerSampler(Mockito.any())).thenAnswer(invocation -> {
      Sampler sampler = invocation.getArgument(0);
      sampler.start();
      registered.add(sampler);
      return true;
    });
    long sourceTime = System.currentTimeMillis() - 10_000L;
    Mockito.when(history.latest()).thenReturn(MetricSnapshot.of(1L, System.currentTimeMillis(), List.of(
        new MetricSnapshotValue("cached", "Cached", "ms", 1D, "1", true, sourceTime,
            new Sampler.Metadata(1_000L, 0L, Sampler.Quality.MEASURED, "ms"), "", Sampler.Diagnostics.EMPTY))));
    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(SampleController.class)).thenReturn(samples);
      react.when(() -> React.controller(HistoryController.class)).thenReturn(history);
      RuntimeDiagnosticsController controller = new RuntimeDiagnosticsController();
      controller.start();
      controller.postStart();
      assertEquals(20, registered.size());
      assertFalse(registered.getFirst().captureReading().available());
      controller.onTick();
      controller.onTick();
      for (Sampler sampler : registered) {
        Sampler.Reading reading = sampler.captureReading();
        assertTrue(reading.available(), sampler.getId());
        assertEquals(Sampler.Quality.MEASURED, reading.metadata().quality());
        assertTrue(reading.metadata().windowMs() > 0L);
        if (sampler.getId().equals("react-sampler-stale")) {
          assertEquals(1D, reading.value());
        }
        if (sampler.getId().equals("react-sampler-oldest-age-ms")) {
          assertTrue(reading.value() >= 10_000D);
        }
      }
      controller.stop();
      assertFalse(registered.getFirst().captureReading().available());
      Mockito.verify(samples, Mockito.times(20)).unregisterSampler(Mockito.anyString());
    } finally {
      ticker.close();
      React.instance = previous;
    }
  }
}
