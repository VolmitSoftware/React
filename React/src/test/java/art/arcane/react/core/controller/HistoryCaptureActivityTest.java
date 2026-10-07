package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerEventTime;
import art.arcane.react.core.history.MetricSnapshot;
import art.arcane.react.core.history.MetricSnapshotValue;
import art.arcane.react.core.plugincost.SamplerPluginCost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

class HistoryCaptureActivityTest {
  private React previous;
  private MockedStatic<React> react;
  private MockedStatic<J> scheduling;
  private MockedStatic<Bukkit> bukkit;
  private MockedStatic<FoliaScheduler> global;
  private EventController controller;
  private SamplerEventTime sampler;
  private HistoryController history;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
    react = Mockito.mockStatic(React.class);
    scheduling = Mockito.mockStatic(J.class);
    bukkit = Mockito.mockStatic(Bukkit.class);
    global = Mockito.mockStatic(FoliaScheduler.class);
    scheduling.when(J::isFoliaThreading).thenReturn(false);
    bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
    controller = new EventController();
    controller.setInstrumentation(EventController.InstrumentationMode.ON_DEMAND);
    react.when(() -> React.controller(EventController.class)).thenReturn(controller);
    controller.start();
    sampler = new SamplerEventTime();
    sampler.start();
    history = new HistoryController();
  }

  @AfterEach
  void tearDown() {
    global.close();
    bukkit.close();
    scheduling.close();
    react.close();
    React.instance = previous;
  }

  @Test
  void registryOrderingRefreshesForReplacementAndRemoval() {
    Sampler first = Mockito.mock(Sampler.class);
    Mockito.when(first.getId()).thenReturn("dynamic");
    Mockito.when(first.getName()).thenReturn("First");
    Sampler replacement = Mockito.mock(Sampler.class);
    Mockito.when(replacement.getId()).thenReturn("dynamic");
    Mockito.when(replacement.getName()).thenReturn("Replacement");

    Assertions.assertEquals("First", history.captureSnapshot(List.of(first, sampler), 1L).value("dynamic").name());
    Assertions.assertEquals("Replacement", history.captureSnapshot(List.of(sampler, replacement), 2L).value("dynamic").name());
    Assertions.assertNull(history.captureSnapshot(List.of(sampler), 3L).value("dynamic"));
    Mockito.verify(first, Mockito.times(1)).capture();
    Mockito.verify(replacement, Mockito.times(1)).capture();
  }

  @Test
  void captureDoesNotMarkEventActivityAndRecordsUnavailableAsAGap() {
    MetricSnapshot snapshot = history.captureSnapshot(List.of(sampler), System.currentTimeMillis());

    MetricSnapshotValue value = snapshot.value(SamplerEventTime.ID);
    Assertions.assertNotNull(value);
    Assertions.assertFalse(value.available());
    Assertions.assertEquals(0D, value.value());
    Assertions.assertEquals(0L, controller.getLastSamplerActivity());
    Assertions.assertFalse(controller.isSpiesInjected());
    Assertions.assertEquals(1, history.getUnavailableSamplerCount());
    Assertions.assertEquals(0, history.getAvailableSamplerCount());
  }

  @Test
  void directConsumerReadMarksEventActivity() {
    sampler.sample();

    Assertions.assertTrue(controller.getLastSamplerActivity() > 0L);
    Assertions.assertTrue(controller.isSpiesInjected());
  }

  @Test
  void captureMarksEventActivityWhileALiveViewerIsAttached() {
    history.latestObserved();

    history.captureSnapshot(List.of(sampler), System.currentTimeMillis());

    Assertions.assertTrue(controller.getLastSamplerActivity() > 0L);
  }

  @Test
  void captureAfterMeasurementStartsDoesNotRecordACachedZero() {
    SamplerPluginCost pluginCost = new SamplerPluginCost("MeasuredPlugin");
    pluginCost.start();
    history.captureSnapshot(List.of(sampler, pluginCost), System.currentTimeMillis());
    Assertions.assertFalse(controller.isMeasuring());

    controller.setInstrumentation(EventController.InstrumentationMode.ALWAYS);
    controller.onTick();
    controller.onTick();
    Assertions.assertTrue(controller.isMeasuring());

    MetricSnapshot snapshot = history.captureSnapshot(List.of(sampler, pluginCost), System.currentTimeMillis());

    MetricSnapshotValue eventTime = snapshot.value(SamplerEventTime.ID);
    MetricSnapshotValue cost = snapshot.value(pluginCost.getId());
    Assertions.assertNotNull(eventTime);
    Assertions.assertNotNull(cost);
    Assertions.assertFalse(eventTime.available(), "a value cached before measurement started must stay a gap");
    Assertions.assertFalse(cost.available(), "a value cached before measurement started must stay a gap");
  }

  @Test
  void captureRecordsAvailableValuesOnceAWindowIsPublished() {
    controller.setInstrumentation(EventController.InstrumentationMode.ALWAYS);
    controller.onTick();
    controller.onTick();
    Assertions.assertTrue(controller.isMeasuring());

    MetricSnapshot snapshot = history.captureSnapshot(List.of(sampler), System.currentTimeMillis());

    MetricSnapshotValue value = snapshot.value(SamplerEventTime.ID);
    Assertions.assertNotNull(value);
    Assertions.assertTrue(value.available());
    Assertions.assertEquals(0L, controller.getLastSamplerActivity());
  }
}
