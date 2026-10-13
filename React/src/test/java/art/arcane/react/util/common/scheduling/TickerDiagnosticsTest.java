package art.arcane.react.util.common.scheduling;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickerDiagnosticsTest {
  @Test
  void recordsQueuedFailedAndSkippedCallbacksSeparately() {
    List<Runnable> queued = new ArrayList<>();
    Ticked task = Mockito.mock(Ticked.class);
    Mockito.when(task.getTid()).thenReturn("diagnostics");
    Mockito.when(task.shouldTick()).thenReturn(true);
    Mockito.doThrow(new IllegalStateException("callback failed")).when(task).tick();
    Ticker ticker = new Ticker(queued::add);
    try {
      ticker.register(task);
      ticker.tick();
      ticker.tick();
      ticker.tick();
      assertEquals(1L, ticker.diagnostics().submitted());
      assertEquals(0L, ticker.diagnostics().executed());
      assertEquals(1L, ticker.diagnostics().skipped());

      queued.removeFirst().run();

      Ticker.Diagnostics diagnostics = ticker.diagnostics();
      assertEquals(1L, diagnostics.executed());
      assertEquals(1L, diagnostics.failures());
      assertEquals(0L, diagnostics.rejected());
      assertEquals(0, diagnostics.active());
      assertTrue(diagnostics.runtimeNanos() > 0L);
      assertTrue(diagnostics.queueNanos() > 0L);
      assertEquals(diagnostics.queueNanos(), diagnostics.maxQueueNanos());
    } finally {
      ticker.close();
    }
  }

  @Test
  void rejectionDoesNotCountAsExecutionOrSubmission() {
    Ticked task = Mockito.mock(Ticked.class);
    Mockito.when(task.getTid()).thenReturn("rejected");
    Mockito.when(task.shouldTick()).thenReturn(true);
    Ticker ticker = new Ticker(command -> {
      throw new RejectedExecutionException("full");
    });
    try {
      ticker.register(task);
      ticker.tick();
      assertThrows(IllegalStateException.class, ticker::tick);
      Ticker.Diagnostics diagnostics = ticker.diagnostics();
      assertEquals(1L, diagnostics.rejected());
      assertEquals(0L, diagnostics.submitted());
      assertEquals(0L, diagnostics.executed());
      assertEquals(0L, diagnostics.runtimeNanos());
    } finally {
      ticker.close();
    }
  }
}
