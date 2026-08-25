package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.MetricsSerializer;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.SamplerDto;
import art.arcane.react.api.web.resource.MetricsResource;
import art.arcane.react.core.controller.HistoryController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.history.MetricSnapshot;
import art.arcane.react.core.history.MetricSnapshotValue;
import art.arcane.react.util.project.registry.Registry;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MetricsResourceTest {
  @Test
  void snapshotReturnsEmptyArrayWhenRegistryIsNull() {
    SampleController controller = mock(SampleController.class);
    when(controller.getSamplers()).thenReturn(null);
    MetricsResource resource = new MetricsResource(controller, null, new MetricsSerializer());

    Context context = mock(Context.class);
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    resource.snapshot(context);
    verify(context).json(captor.capture());

    @SuppressWarnings("unchecked")
    Envelope<MetricsResource.SnapshotResponse> envelope =
        (Envelope<MetricsResource.SnapshotResponse>) captor.getValue();
    assertEquals(0, envelope.data().samplers().length);
  }

  @Test
  void snapshotUsesAuthoritativeCachedValues() {
    SampleController controller = mock(SampleController.class);
    HistoryController history = mock(HistoryController.class);
    when(history.latest()).thenReturn(MetricSnapshot.of(
        17L,
        1_000L,
        List.of(new MetricSnapshotValue("tick-time", "Tick Time", "ms", 42D, "42 ms", true))
    ));
    MetricsResource resource = new MetricsResource(controller, history, new MetricsSerializer());

    MetricsResource.SnapshotResponse response = resource.snapshotData();

    assertEquals(17L, response.sequence());
    assertEquals(1_000L, response.capturedAtMs());
    assertEquals(1, response.samplers().length);
    assertEquals("tick-time", response.samplers()[0].id);
    assertEquals(42D, response.samplers()[0].value);
    assertEquals(true, response.samplers()[0].available);
  }

  @Test
  void snapshotFallsBackToRegistryBeforeFirstCapture() {
    SampleController controller = mock(SampleController.class);
    @SuppressWarnings("unchecked")
    Registry<Sampler> registry = mock(Registry.class);
    when(controller.getSamplers()).thenReturn(registry);
    when(registry.all()).thenReturn(List.of(new FakeSampler("tps", 20D, "")));
    MetricsResource resource = new MetricsResource(controller, null, new MetricsSerializer());

    SamplerDto[] values = resource.snapshotData().samplers();

    assertEquals(1, values.length);
    assertEquals("tps", values[0].id);
  }

  @Test
  void historyReturns404WhenEveryRequestedIdIsUnknown() {
    HistoryController history = mock(HistoryController.class);
    when(history.latest()).thenReturn(MetricSnapshot.empty());
    when(history.effectiveMaxQuerySeries()).thenReturn(16);
    when(history.effectiveMaxQueryPoints()).thenReturn(4_096);
    when(history.effectiveQueryPagePoints()).thenReturn(256);
    when(history.knowsMetric("nope")).thenReturn(false);
    when(history.selectResolution(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(1_000L);
    MetricsResource resource = new MetricsResource(null, history, new MetricsSerializer());
    Context context = mock(Context.class);
    when(context.queryParam("ids")).thenReturn("nope");

    assertThrows(NotFoundResponse.class, () -> resource.history(context));
  }

  private static final class FakeSampler implements Sampler {
    private final String id;
    private final double value;
    private final String suffix;

    FakeSampler(String id, double value, String suffix) {
      this.id = id;
      this.value = value;
      this.suffix = suffix;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getName() {
      return id;
    }

    @Override
    public double sample() {
      return value;
    }

    @Override
    public String formattedValue(double sampled) {
      return String.valueOf(sampled);
    }

    @Override
    public String formattedSuffix(double sampled) {
      return suffix;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void render() {
    }
  }
}
