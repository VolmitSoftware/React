package art.arcane.react.core.telemetry.runtime;

import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaperPathfindingDecisionsTest {
  @Test
  void separatesCancelledDecisionsAndResetsEachElapsedWindow() {
    PaperPathfindingDecisions tracker = new PaperPathfindingDecisions();
    EntityPathfindEvent accepted = Mockito.mock(EntityPathfindEvent.class);
    EntityPathfindEvent cancelled = Mockito.mock(EntityPathfindEvent.class);
    Mockito.when(cancelled.isCancelled()).thenReturn(true);
    tracker.onPathfind(accepted);
    tracker.onPathfind(cancelled);
    PaperPathfindingDecisions.Snapshot snapshot = tracker.snapshot(2_000L, 2_000_000_000L);
    Assertions.assertEquals(1D, snapshot.decisionsPerSecond());
    Assertions.assertEquals(0.5D, snapshot.cancelledPerSecond());
    Assertions.assertEquals(0D, tracker.snapshot(3_000L, 1_000_000_000L).decisionsPerSecond());
  }
}
