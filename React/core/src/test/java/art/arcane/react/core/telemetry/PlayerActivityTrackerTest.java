package art.arcane.react.core.telemetry;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class PlayerActivityTrackerTest {
  @Test
  void ratesAndUniquePlayersExpireAtTheirWindowBoundaries() {
    PlayerActivityTracker tracker = new PlayerActivityTracker();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    tracker.recordJoin(first, 1_000L);
    tracker.recordJoin(second, 2_000L);
    tracker.recordJoin(first, 3_000L);
    tracker.recordQuit(first, 4_000L);
    tracker.recordQuit(second, 4_000L);

    Assertions.assertEquals(3D, tracker.joinsPerMinute(60_999L));
    Assertions.assertEquals(2D, tracker.quitsPerMinute(60_999L));
    Assertions.assertEquals(2, tracker.uniquePlayers(60_999L));
    Assertions.assertEquals(1D, tracker.joinsPerMinute(62_000L));
    Assertions.assertEquals(0D, tracker.quitsPerMinute(64_000L));
    Assertions.assertEquals(0, tracker.uniquePlayers(86_404_000L));
  }

  @Test
  void continuouslyOnlinePlayersRemainUniquePastTheWindow() {
    PlayerActivityTracker tracker = new PlayerActivityTracker();
    UUID playerId = UUID.randomUUID();
    tracker.recordJoin(playerId, 1_000L);

    Assertions.assertEquals(1, tracker.uniquePlayers(86_402_000L));
    tracker.recordQuit(playerId, 86_403_000L);
    Assertions.assertEquals(1, tracker.uniquePlayers(86_404_000L));
    Assertions.assertEquals(0, tracker.uniquePlayers(172_804_000L));
  }
}
