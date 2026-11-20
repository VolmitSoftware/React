package art.arcane.react.core.telemetry.runtime;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JfrSessionTest {
  @Test
  void reportsDisabledFailedAndLiveRecordings() {
    JfrSession session = new JfrSession();
    session.activate();
    session.configure(false, 10L);
    assertEquals("disabled", session.metric("jfr-chunk-generation-rate", 1_000L).unavailableReason());
    try (MockedStatic<JfrTelemetry> recordings = Mockito.mockStatic(JfrTelemetry.class)) {
      recordings.when(() -> JfrTelemetry.start(Mockito.any(), Mockito.any())).thenThrow(new IllegalStateException("no jfr"));
      session.configure(true, 10L);
      assertEquals("source-unavailable", session.metric("jfr-chunk-generation-rate", 1_000L).unavailableReason());
      JfrTelemetry recording = Mockito.mock(JfrTelemetry.class);
      RuntimeMetric value = new RuntimeMetric(4D, 1_000L, true, "");
      Mockito.when(recording.snapshot(Mockito.anyLong())).thenReturn(new JfrTelemetry.Snapshot(
          1_000L, 1_000L, 10L, true, true, Set.of(), Map.of("jfr-chunk-generation-rate", value), List.of()));
      recordings.when(() -> JfrTelemetry.start(Mockito.any(), Mockito.any())).thenReturn(recording);
      session.configure(true, 20L);
      session.refresh(1_000L);
      assertEquals(4D, session.metric("jfr-chunk-generation-rate", 1_500L).value());
      assertEquals("stale", session.metric("jfr-chunk-generation-rate", 9_000L).unavailableReason());
      session.configure(false, 20L);
      Mockito.verify(recording).close();
      assertTrue(session.metric("jfr-chunk-generation-rate", 1_500L).unavailableReason().equals("disabled"));
    }
  }
}
