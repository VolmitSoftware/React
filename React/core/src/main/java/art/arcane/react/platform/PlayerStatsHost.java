package art.arcane.react.platform;

import java.util.Map;
import java.util.UUID;

public interface PlayerStatsHost {
  int onlinePlayerCount();

  /**
   * Latency in milliseconds per online player, in online-list order. Players whose ping cannot be read are left out.
   */
  Map<UUID, Integer> playerPings();
}
