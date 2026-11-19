package art.arcane.react.content.feature;

import art.arcane.react.content.feature.PlayerChunkBudget.Budgets;
import art.arcane.react.content.feature.PlayerChunkBudget.ChunkPosition;
import art.arcane.react.content.feature.PlayerChunkBudget.Distances;
import art.arcane.react.content.feature.PlayerChunkBudget.Limits;
import art.arcane.react.content.feature.PlayerChunkBudget.Recovery;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerChunkBudgetTest {
  @Test
  void sweepMatchesIndependentBruteForceForRandomOverlappingFootprints() {
    Random random = new Random(319807L);
    for (int iteration = 0; iteration < 300; iteration++) {
      List<ChunkPosition> positions = new ArrayList<>();
      int radius = random.nextInt(9);
      int players = random.nextInt(30);
      for (int player = 0; player < players; player++) {
        positions.add(new ChunkPosition(random.nextInt(41) - 20, random.nextInt(41) - 20));
      }
      assertEquals(bruteForce(positions, radius), PlayerChunkBudget.count(positions, radius));
    }
  }

  @Test
  void countsDuplicateNegativeAndExtremeChunkCoordinatesWithoutOverflow() {
    assertEquals(9L, PlayerChunkBudget.count(List.of(new ChunkPosition(-1, -1), new ChunkPosition(-1, -1)), 1));
    assertEquals(12L, PlayerChunkBudget.count(List.of(new ChunkPosition(-1, -1), new ChunkPosition(0, -1)), 1));
    assertEquals(18L, PlayerChunkBudget.count(List.of(new ChunkPosition(Integer.MIN_VALUE, 0), new ChunkPosition(Integer.MAX_VALUE, 0)), 1));
    assertEquals(4225L, PlayerChunkBudget.count(List.of(new ChunkPosition(0, 0)), 32));
    assertEquals(0L, PlayerChunkBudget.count(List.of(), 32));
  }

  @Test
  void budgetsSeparateSimulationFromViewOnlyAndRespectConfiguredFloors() {
    Limits limits = new Limits(6, 4);
    Distances ceiling = new Distances(16, 10);
    Budgets budgets = new Budgets(121L, 104L);
    assertEquals(new Distances(7, 5), PlayerChunkBudget.constrain(List.of(new ChunkPosition(0, 0)), limits, ceiling, budgets, 10));
    assertEquals(new Distances(7, 5), PlayerChunkBudget.constrain(List.of(new ChunkPosition(0, 0), new ChunkPosition(0, 0)), limits, ceiling, budgets, 10));
    assertEquals(new Distances(6, 4), PlayerChunkBudget.constrain(List.of(new ChunkPosition(0, 0), new ChunkPosition(100, 100)), limits, ceiling, budgets, 10));
    assertEquals(ceiling, PlayerChunkBudget.constrain(List.of(), limits, ceiling, budgets, 10));
  }

  @Test
  void intermediateRecoveryDoesNotSpendTheFinalSimulationFootprintBudget() {
    List<ChunkPosition> positions = List.of(new ChunkPosition(0, 0));
    Distances current = new Distances(6, 4);
    Distances target = PlayerChunkBudget.constrain(positions, new Limits(6, 4),
        new Distances(9, 8), new Budgets(1000L, 100L), current.simulation());
    Distances next = new Recovery().next(current, target, true, 1, 1);
    assertEquals(new Distances(6, 5), next);
    assertEquals(48L, PlayerChunkBudget.count(positions, next.view()) - PlayerChunkBudget.count(positions, next.simulation()));
  }

  @Test
  void recoveryNeedsConsecutiveHealthyChecksAndLimitsEachIncrease() {
    Recovery recovery = new Recovery();
    Distances current = new Distances(6, 4);
    Distances target = new Distances(12, 8);
    assertEquals(current, recovery.next(current, target, true, 3, 1));
    assertEquals(current, recovery.next(current, target, false, 3, 1));
    assertEquals(current, recovery.next(current, target, true, 3, 1));
    assertEquals(current, recovery.next(current, target, true, 3, 1));
    Distances increased = new Distances(7, 5);
    assertEquals(increased, recovery.next(current, target, true, 3, 1));
    assertEquals(increased, recovery.next(increased, target, true, 3, 1));
    assertEquals(new Distances(6, 4), recovery.next(increased, new Distances(6, 4), false, 3, 1));
  }

  private long bruteForce(List<ChunkPosition> positions, int radius) {
    Set<Long> chunks = new HashSet<>();
    for (ChunkPosition position : positions) {
      for (int x = position.x() - radius; x <= position.x() + radius; x++) {
        for (int z = position.z() - radius; z <= position.z() + radius; z++) {
          chunks.add(((long) x << 32) | (z & 0xffffffffL));
        }
      }
    }
    return chunks.size();
  }
}
