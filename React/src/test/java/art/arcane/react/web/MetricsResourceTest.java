package art.arcane.react.web;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.MetricsSerializer;
import art.arcane.react.api.web.WebRevisions;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.MetricHistoryDescriptorDto;
import art.arcane.react.api.web.dto.SamplerDto;
import art.arcane.react.api.web.resource.MetricsResource;
import art.arcane.react.core.controller.HistoryController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.history.MetricDescriptor;
import art.arcane.react.core.history.MetricSnapshot;
import art.arcane.react.core.history.MetricSnapshotValue;
import art.arcane.react.util.project.registry.Registry;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
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
    when(history.latestObserved()).thenReturn(MetricSnapshot.of(
        17L,
        1_000L,
        List.of(new MetricSnapshotValue("tick-time", "Tick Time", "ms", 42D, "42 ms", true, 1L, Sampler.Metadata.UNKNOWN, "", Sampler.Diagnostics.EMPTY))
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
  void reusesDtosWhileEveryReadStillMarksViewerActivity() {
    HistoryController history = mock(HistoryController.class);
    MetricSnapshotValue value = new MetricSnapshotValue("tick-time", "Tick Time", "ms", 42D, "42", true,
        123L, Sampler.Metadata.UNKNOWN, "", Sampler.Diagnostics.EMPTY);
    MetricSnapshot first = MetricSnapshot.of(17L, 1_000L, List.of(value));
    MetricSnapshot next = MetricSnapshot.of(18L, 1_500L, List.of(value));
    MetricSnapshot replacement = MetricSnapshot.of(18L, 2_000L, List.of(value));
    when(history.latestObserved()).thenReturn(first, first, next, next, replacement);
    MetricsSerializer serializer = spy(new MetricsSerializer());
    MetricsResource resource = new MetricsResource(null, history, serializer);

    MetricsResource.SnapshotResponse a = resource.snapshotData();
    assertSame(a, resource.snapshotData());
    MetricsResource.SnapshotResponse b = resource.snapshotData();
    assertNotSame(a, b);
    assertSame(b, resource.snapshotData());
    MetricsResource.SnapshotResponse c = resource.snapshotData();
    assertNotSame(b, c);
    assertEquals(2_000L, c.capturedAtMs());
    verify(history, times(5)).latestObserved();
    verify(serializer, times(3)).toDto(value);
  }

  @Test
  void registryFallbackKeepsReadingAndMarkingDemand() {
    SampleController controller = mock(SampleController.class);
    @SuppressWarnings("unchecked")
    Registry<Sampler> registry = mock(Registry.class);
    Sampler sampler = mock(Sampler.class);
    when(sampler.captureReading()).thenReturn(new Sampler.Reading(20D, 123L, true, Sampler.Metadata.UNKNOWN, ""));
    when(controller.getSamplers()).thenReturn(registry);
    when(registry.all()).thenReturn(List.of(sampler));
    MetricsResource resource = new MetricsResource(controller, null, new MetricsSerializer());

    assertNotSame(resource.snapshotData(), resource.snapshotData());
    verify(sampler, times(2)).markDemand();
    verify(sampler, times(2)).captureReading();
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
  void catalogMarksWhichMetricsAreAvailableNow() {
    HistoryController history = mock(HistoryController.class);
    MetricSnapshotValue live = new MetricSnapshotValue("tps", "TPS", "", 20D, "20", true,
        123L, Sampler.Metadata.UNKNOWN, "", Sampler.Diagnostics.EMPTY);
    MetricSnapshotValue off = new MetricSnapshotValue("adapt-xp", "Adapt XP", "", 0D, "0", false,
        123L, Sampler.Metadata.UNKNOWN, "plugin-missing", Sampler.Diagnostics.EMPTY);
    when(history.latest()).thenReturn(MetricSnapshot.of(3L, 1_000L, List.of(live, off)));
    when(history.descriptors()).thenReturn(List.of(
        new MetricDescriptor("tps", "TPS", "", 1L, 2L, true, Sampler.Metadata.UNKNOWN),
        new MetricDescriptor("adapt-xp", "Adapt XP", "", 1L, 2L, true, Sampler.Metadata.UNKNOWN),
        new MetricDescriptor("retired", "Retired", "", 1L, 2L, false, Sampler.Metadata.UNKNOWN)
    ));
    Context context = mock(Context.class);
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

    new MetricsResource(null, history, new MetricsSerializer()).historyCatalog(context);

    verify(context).json(captor.capture());
    MetricHistoryDescriptorDto[] data = (MetricHistoryDescriptorDto[]) ((Envelope<?>) captor.getValue()).data();
    assertEquals(true, data[0].available());
    assertNull(data[0].unavailableReason());
    assertEquals(false, data[1].available());
    assertEquals("plugin-missing", data[1].unavailableReason());
    assertEquals(false, data[2].available());
  }

  @Test
  void liveFrameKeepsOnlyAvailableSamplersAndIsReusedUntilSomethingMoves() {
    HistoryController history = mock(HistoryController.class);
    MetricSnapshotValue live = new MetricSnapshotValue("tps", "TPS", "", 20D, "20", true,
        123L, Sampler.Metadata.UNKNOWN, "", new Sampler.Diagnostics(1L, 1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));
    MetricSnapshotValue off = new MetricSnapshotValue("adapt-xp", "Adapt XP", "", 0D, "0", false,
        123L, Sampler.Metadata.UNKNOWN, "plugin-missing", Sampler.Diagnostics.EMPTY);
    MetricSnapshot snapshot = MetricSnapshot.of(3L, 1_000L, List.of(live, off));
    when(history.latestObserved()).thenReturn(snapshot);
    MetricsResource resource = new MetricsResource(null, history, new MetricsSerializer());

    MetricsResource.LiveFrame first = resource.liveFrame();
    assertEquals(1, first.samplers().length);
    assertEquals("tps", first.samplers()[0].id());
    assertSame(first, resource.liveFrame(), "an unchanged poll must not allocate a new frame");
    WebRevisions.bump(WebRevisions.Kind.CONFIG);
    MetricsResource.LiveFrame moved = resource.liveFrame();
    assertNotSame(first, moved);
    assertSame(first.samplers(), moved.samplers(), "a revision change reuses the sampler array");
    assertEquals(first.revisions().config() + 1L, moved.revisions().config());
  }

  @Test
  void historyReturns404WhenEveryRequestedIdIsUnknown() {
    HistoryController history = mock(HistoryController.class);
    when(history.latest()).thenReturn(MetricSnapshot.empty());
    when(history.latestObserved()).thenReturn(MetricSnapshot.empty());
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
