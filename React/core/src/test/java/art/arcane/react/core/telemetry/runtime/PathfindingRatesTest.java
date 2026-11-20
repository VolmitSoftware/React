package art.arcane.react.core.telemetry.runtime;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PathfindingRatesTest {
  @Test
  void publishesDecisionAndNodeRatesPerElapsedSecond() {
    PathfindingRates rates = new PathfindingRates(true);
    rates.reset(0L);
    rates.decision(false);
    rates.decision(true);
    rates.visited(300L);
    rates.visited(100L);
    PathfindingRates.Snapshot snapshot = rates.refresh(2_000L, 2_000_000_000L);
    Assertions.assertEquals(1D, snapshot.decisionsPerSecond());
    Assertions.assertEquals(0.5D, snapshot.cancelledPerSecond());
    Assertions.assertEquals(200D, snapshot.nodesPerSecond());
    RuntimeMetric nodes = rates.metric(PathfindingRates.NODES, 2_500L);
    Assertions.assertTrue(nodes.available());
    Assertions.assertEquals(200D, nodes.value());
    Assertions.assertEquals(0D, rates.refresh(3_000L, 3_000_000_000L).nodesPerSecond());
  }

  @Test
  void keepsCountingThroughWindowsShorterThanAQuarterSecond() {
    PathfindingRates rates = new PathfindingRates(true);
    rates.reset(0L);
    rates.visited(50L);
    Assertions.assertSame(PathfindingRates.Snapshot.EMPTY, rates.refresh(100L, 100_000_000L));
    Assertions.assertEquals(50D, rates.refresh(1_000L, 1_000_000_000L).nodesPerSecond());
  }

  @Test
  void marksStaleAndUnknownMetricsUnavailable() {
    PathfindingRates rates = new PathfindingRates(true);
    rates.reset(0L);
    Assertions.assertEquals("source-unavailable", rates.metric(PathfindingRates.DECISIONS, 1_000L).unavailableReason());
    rates.refresh(1_000L, 1_000_000_000L);
    Assertions.assertFalse(rates.metric(PathfindingRates.DECISIONS, 7_000L).available());
    Assertions.assertFalse(rates.metric("pathfinding-other", 1_000L).available());
  }

  @Test
  void ignoresNonPositiveNodeCounts() {
    PathfindingRates rates = new PathfindingRates(true);
    rates.reset(0L);
    rates.visited(0L);
    rates.visited(-5L);
    Assertions.assertEquals(0D, rates.refresh(1_000L, 1_000_000_000L).nodesPerSecond());
  }
}
