package art.arcane.react.api.sampler;

import art.arcane.react.React;
import art.arcane.react.content.sampler.SamplerEventTime;
import art.arcane.react.core.controller.EventController;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReactEventSamplerMetadataTest {
  @Test
  void aNewEventWindowDoesNotRestampTheCachedValue() {
    EventController controller = Mockito.mock(EventController.class);
    Mockito.when(controller.isMeasuring()).thenReturn(true);
    Mockito.when(controller.getWindowPublishedAtMs()).thenReturn(123L);
    Mockito.when(controller.getWindowSeconds()).thenReturn(1D);
    Mockito.when(controller.getEventTimeMsPerSecond()).thenReturn(12D);
    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(EventController.class)).thenReturn(controller);
      SamplerEventTime sampler = new SamplerEventTime();
      sampler.start();
      Sampler.Reading first = sampler.captureReading();
      Mockito.when(controller.getWindowPublishedAtMs()).thenReturn(456L);
      Mockito.when(controller.getWindowSeconds()).thenReturn(2D);
      Mockito.when(controller.getEventTimeMsPerSecond()).thenReturn(24D);
      Sampler.Reading cached = sampler.captureReading();
      assertEquals(first, cached);
      assertEquals(123L, cached.sampledAtMs());
      assertEquals(1_000L, cached.metadata().windowMs());
    }
  }
}
