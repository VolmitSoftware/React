package art.arcane.react.core.telemetry.runtime;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class RegionalHealthTest {
  @Test
  void rotatesBoundedSamplesAndReportsObservedPlayersWithoutExtrapolation() {
    RegionalHealth health = new RegionalHealth();
    List<RegionalHealth.Anchor> players = players(6);
    AtomicInteger queries = new AtomicInteger();
    RegionalHealth.Reader reader = position -> {
      queries.incrementAndGet();
      return position.chunkX() < 2 ? 14D : 19D;
    };
    RegionalHealth.Snapshot first = health.sample(players, reader, 2, 1_000L);
    Assertions.assertEquals(2, queries.get());
    Assertions.assertEquals(2, first.below15());
    Assertions.assertEquals(100D / 3D, first.coveragePercent(), 0.0001D);
    RegionalHealth.Snapshot second = health.sample(players, reader, 2, 2_000L);
    Assertions.assertEquals(4, second.observedPlayers());
    Assertions.assertEquals(2, second.below15());
    Assertions.assertEquals(14D, second.lowestTps());
  }

  @Test
  void discardsExpiredMovedAndDisconnectedObservations() {
    RegionalHealth health = new RegionalHealth();
    List<RegionalHealth.Anchor> players = players(2);
    health.sample(players, ignored -> 12D, 2, 1_000L);
    RegionalHealth.Anchor moved = new RegionalHealth.Anchor(players.getFirst().playerId(),
        new RegionalHealth.Position(UUID.randomUUID(), 10, 10));
    RegionalHealth.Snapshot snapshot = health.sample(List.of(moved), ignored -> Double.NaN, 1, 2_000L);
    Assertions.assertEquals(0, snapshot.observedPlayers());
    health.sample(players, ignored -> 12D, 2, 3_000L);
    snapshot = health.sample(players, ignored -> 20D, 1, 9_000L);
    Assertions.assertEquals(1, snapshot.observedPlayers());
    Assertions.assertEquals(0, snapshot.below15());
    Assertions.assertEquals(20D, snapshot.lowestTps());
  }

  @Test
  void coLocatedPlayersShareQueryButCountAsSeparateObservedPlayers() {
    RegionalHealth health = new RegionalHealth();
    RegionalHealth.Position position = new RegionalHealth.Position(UUID.randomUUID(), 3, 3);
    AtomicInteger queries = new AtomicInteger();
    RegionalHealth.Snapshot result = health.sample(List.of(
        new RegionalHealth.Anchor(UUID.randomUUID(), position),
        new RegionalHealth.Anchor(UUID.randomUUID(), position)), ignored -> {
          queries.incrementAndGet();
          return 17D;
        }, 64, 1_000L);
    Assertions.assertEquals(1, queries.get());
    Assertions.assertEquals(2, result.below18());
    Assertions.assertEquals(100D, result.coveragePercent());
  }

  @Test
  void emptyPlayersHaveCompleteCoverageAndNoInventedTps() {
    RegionalHealth.Snapshot result = new RegionalHealth().sample(List.of(), ignored -> 20D, 64, 1_000L);
    Assertions.assertEquals(100D, result.coveragePercent());
    Assertions.assertEquals(0D, result.lowestTps());
    Assertions.assertEquals(0, result.observedPlayers());
  }

  private static List<RegionalHealth.Anchor> players(int count) {
    UUID world = UUID.randomUUID();
    List<RegionalHealth.Anchor> result = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      result.add(new RegionalHealth.Anchor(UUID.randomUUID(), new RegionalHealth.Position(world, index, 0)));
    }
    return List.copyOf(result);
  }
}
