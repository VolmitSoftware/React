package art.arcane.react.model;

import art.arcane.react.platform.GameLocation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class CircuitServer {
  private final Map<UUID, CircuitWorld> circuitWorlds;
  private final Function<UUID, String> worldNames;

  public CircuitServer(Function<UUID, String> worldNames) {
    circuitWorlds = new ConcurrentHashMap<>();
    this.worldNames = worldNames;
  }

  public CircuitObservation event(GameLocation block, long now) {
    UUID worldId = block.worldId();
    CircuitWorld circuitWorld = circuitWorlds.get(worldId);
    if (circuitWorld == null) {
      circuitWorld = circuitWorlds.computeIfAbsent(worldId, ignored -> new CircuitWorld(worldId, worldNames.apply(worldId)));
    }
    return circuitWorld.event(block.blockX(), block.blockY(), block.blockZ(), now);
  }

  public void remove(GameLocation block, long now) {
    CircuitWorld circuitWorld = circuitWorlds.get(block.worldId());
    if (circuitWorld == null) {
      return;
    }
    circuitWorld.remove(block.blockX(), block.blockY(), block.blockZ(), now);
  }

  public void rollWindow(long now, long inactivityMs) {
    for (Map.Entry<UUID, CircuitWorld> entry : circuitWorlds.entrySet()) {
      CircuitWorld circuitWorld = entry.getValue();
      circuitWorld.rollWindow(now, inactivityMs);
      if (circuitWorld.countCircuits() == 0) {
        circuitWorlds.remove(entry.getKey(), circuitWorld);
      }
    }
  }

  public CircuitSnapshot throttleWorst(long now, long durationMs) {
    CircuitWorld candidateWorld = null;
    CircuitSnapshot candidate = null;
    for (CircuitWorld world : circuitWorlds.values()) {
      CircuitSnapshot snapshot = world.worst(now);
      if (snapshot == null) {
        continue;
      }
      if (candidate == null
          || snapshot.events() > candidate.events()
          || snapshot.events() == candidate.events() && snapshot.nodes() > candidate.nodes()) {
        candidate = snapshot;
        candidateWorld = world;
      }
    }
    return candidateWorld == null ? null : candidateWorld.throttle(candidate.circuitId(), now, durationMs);
  }

  public int countCircuits() {
    int count = 0;
    for (CircuitWorld world : circuitWorlds.values()) {
      count += world.countCircuits();
    }
    return count;
  }

  public int countBlocks() {
    int count = 0;
    for (CircuitWorld world : circuitWorlds.values()) {
      count += world.countBlocks();
    }
    return count;
  }
}
