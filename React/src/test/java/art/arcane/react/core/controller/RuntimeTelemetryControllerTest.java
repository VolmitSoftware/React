package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.core.telemetry.runtime.JfrTelemetry;
import art.arcane.react.util.common.scheduling.J;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class RuntimeTelemetryControllerTest {
  private MockedStatic<React> react;
  private MockedStatic<J> scheduling;
  private MockedStatic<JfrTelemetry> recordings;
  private RuntimeTelemetryController controller;
  private JfrTelemetry recording;

  @BeforeEach
  void setUp() {
    react = Mockito.mockStatic(React.class);
    scheduling = Mockito.mockStatic(J.class);
    recordings = Mockito.mockStatic(JfrTelemetry.class);
    recording = Mockito.mock(JfrTelemetry.class);
    Mockito.when(recording.snapshot(Mockito.anyLong())).thenReturn(new JfrTelemetry.Snapshot(
        1_000L, 1_000L, 10L, true, true, Set.of("jdk.ThreadPark"), Map.of(), List.of()));
    recordings.when(() -> JfrTelemetry.start(Mockito.any(), Mockito.any())).thenReturn(recording);
    controller = new RuntimeTelemetryController();
    controller.start();
  }

  @AfterEach
  void tearDown() {
    controller.stop();
    recordings.close();
    scheduling.close();
    react.close();
  }

  @Test
  void defaultConfigurationLeavesRecordingOffAndPaperRegionValuesUnavailable() throws Exception {
    refresh();
    recordings.verifyNoInteractions();
    Assertions.assertEquals("disabled", controller.metric("jfr-chunk-generation-rate").unavailableReason());
    Assertions.assertEquals("source-unavailable", controller.metric("region-tps-lowest").unavailableReason());
  }

  @Test
  void configurationChangesStartAndCloseExactlyOneStream() throws Exception {
    configure(true, 10L);
    refresh();
    refresh();
    recordings.verify(() -> JfrTelemetry.start(Mockito.eq(new JfrTelemetry.Options(10L)), Mockito.any()), Mockito.times(1));
    configure(true, 20L);
    refresh();
    Mockito.verify(recording).close();
    recordings.verify(() -> JfrTelemetry.start(Mockito.eq(new JfrTelemetry.Options(20L)), Mockito.any()));
    configure(false, 20L);
    refresh();
    Mockito.verify(recording, Mockito.times(2)).close();
    Assertions.assertEquals("disabled", controller.metric("jfr-chunk-generation-rate").unavailableReason());
  }

  @Test
  void stopClosesRecordingEvenWhenExecutorDrainIsInterrupted() throws Exception {
    configure(true, 10L);
    refresh();
    ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
    Mockito.when(executor.awaitTermination(5L, TimeUnit.SECONDS)).thenThrow(new InterruptedException("interrupted"));
    set(controller, "executor", executor);
    try {
      Assertions.assertThrows(IllegalStateException.class, controller::stop);
      Assertions.assertTrue(Thread.currentThread().isInterrupted());
      Mockito.verify(recording).close();
      Assertions.assertEquals("stopped", controller.metric("region-tps-lowest").unavailableReason());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void failedStartStaysUnavailableUntilConfigurationChanges() throws Exception {
    recordings.when(() -> JfrTelemetry.start(Mockito.any(), Mockito.any())).thenThrow(new IllegalStateException("recording unavailable"));
    configure(true, 10L);
    refresh();
    refresh();
    recordings.verify(() -> JfrTelemetry.start(Mockito.any(), Mockito.any()), Mockito.times(1));
    Assertions.assertEquals("source-unavailable", controller.metric("jfr-chunk-generation-rate").unavailableReason());
    recordings.when(() -> JfrTelemetry.start(Mockito.any(), Mockito.any())).thenReturn(recording);
    configure(true, 10L);
    refresh();
    recordings.verify(() -> JfrTelemetry.start(Mockito.any(), Mockito.any()), Mockito.times(2));
  }

  private void configure(boolean enabled, long threshold) throws Exception {
    RuntimeTelemetryController configuration = new RuntimeTelemetryController();
    set(configuration, "jfrEnabled", enabled);
    set(configuration, "jfrSlowThresholdMs", threshold);
    Assertions.assertTrue(controller.applyConfigurationSnapshot(configuration));
  }

  private void refresh() throws Exception {
    Method refresh = RuntimeTelemetryController.class.getDeclaredMethod("refresh");
    refresh.setAccessible(true);
    refresh.invoke(controller);
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Field field = RuntimeTelemetryController.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
