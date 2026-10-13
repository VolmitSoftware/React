package art.arcane.react.core.telemetry.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RegionalHealth {
  public static final long MAX_AGE_MS = 5_000L;
  private final Map<UUID, Observation> observations = new HashMap<>();
  private int cursor;

  public Snapshot sample(List<Anchor> players, Reader reader, int limit, long nowMs) {
    Set<UUID> current = new HashSet<>(players.size());
    for (Anchor player : players) {
      current.add(player.playerId());
      Observation previous = observations.get(player.playerId());
      if (previous != null && (!previous.anchor().equals(player) || (nowMs < previous.sampledAtMs() || nowMs - previous.sampledAtMs() > MAX_AGE_MS))) {
        observations.remove(player.playerId());
      }
    }
    observations.keySet().retainAll(current);
    if (players.isEmpty()) {
      cursor = 0;
      return new Snapshot(nowMs, 0D, 0, 0, 0, 0);
    }
    Map<Position, Double> positions = new HashMap<>();
    int count = Math.min(players.size(), Math.clamp(limit, 1, 512));
    int start = Math.floorMod(cursor, players.size());
    for (int offset = 0; offset < count; offset++) {
      Anchor player = players.get((start + offset) % players.size());
      double tps = positions.computeIfAbsent(player.position(), reader::read);
      if (Double.isFinite(tps) && tps >= 0D) {
        observations.put(player.playerId(), new Observation(player, Math.min(20D, tps), nowMs));
      } else {
        observations.remove(player.playerId());
      }
    }
    cursor = (start + count) % players.size();
    double lowest = 20D;
    int below18 = 0;
    int below15 = 0;
    for (Observation observation : observations.values()) {
      lowest = Math.min(lowest, observation.tps());
      if (observation.tps() < 18D) {
        below18++;
      }
      if (observation.tps() < 15D) {
        below15++;
      }
    }
    return new Snapshot(nowMs, observations.isEmpty() ? 0D : lowest, below18, below15, observations.size(), players.size());
  }

  public void clear() {
    observations.clear();
    cursor = 0;
  }

  @FunctionalInterface
  public interface Reader {
    double read(Position position);
  }

  public record Position(UUID worldId, int chunkX, int chunkZ) {
  }

  public record Anchor(UUID playerId, Position position) {
  }

  public record Snapshot(long sampledAtMs, double lowestTps, int below18, int below15, int observedPlayers, int onlinePlayers) {
    public static final Snapshot EMPTY = new Snapshot(0L, 0D, 0, 0, 0, 0);

    public double coveragePercent() {
      return onlinePlayers == 0 ? 100D : 100D * observedPlayers / onlinePlayers;
    }
  }

  private record Observation(Anchor anchor, double tps, long sampledAtMs) {
  }
}
