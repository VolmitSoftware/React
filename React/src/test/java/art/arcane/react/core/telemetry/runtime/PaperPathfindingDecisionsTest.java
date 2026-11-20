package art.arcane.react.core.telemetry.runtime;

import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaperPathfindingDecisionsTest {
  @Test
  void separatesCancelledDecisionsAndResetsEachElapsedWindow() {
    PathfindingRates rates = new PathfindingRates(false);
    rates.reset(0L);
    PaperPathfindingDecisions tracker = new PaperPathfindingDecisions(rates);
    EntityPathfindEvent accepted = Mockito.mock(EntityPathfindEvent.class);
    EntityPathfindEvent cancelled = Mockito.mock(EntityPathfindEvent.class);
    Mockito.when(cancelled.isCancelled()).thenReturn(true);
    tracker.onPathfind(accepted);
    tracker.onPathfind(cancelled);
    PathfindingRates.Snapshot snapshot = rates.refresh(2_000L, 2_000_000_000L);
    Assertions.assertEquals(1D, snapshot.decisionsPerSecond());
    Assertions.assertEquals(0.5D, snapshot.cancelledPerSecond());
    Assertions.assertEquals(0D, rates.refresh(3_000L, 3_000_000_000L).decisionsPerSecond());
  }

  @Test
  void reportsNodesAsUnsupportedOnPaper() {
    PathfindingRates rates = new PathfindingRates(false);
    rates.reset(0L);
    rates.refresh(1_000L, 1_000_000_000L);
    RuntimeMetric nodes = rates.metric(PathfindingRates.NODES, 1_000L);
    Assertions.assertFalse(nodes.available());
    Assertions.assertEquals(PathfindingRates.NODES_UNSUPPORTED, nodes.unavailableReason());
    Assertions.assertTrue(rates.metric(PathfindingRates.DECISIONS, 1_000L).available());
  }
}
