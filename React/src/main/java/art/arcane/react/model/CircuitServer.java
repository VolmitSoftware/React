package art.arcane.react.model;

import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CircuitServer {
  private final Map<UUID, CircuitWorld> circuitWorlds;

  public CircuitServer() {
    circuitWorlds = new ConcurrentHashMap<>();
  }

  public CircuitObservation event(Block block, long now) {
    World world = block.getWorld();
    UUID worldId = world.getUID();
    CircuitWorld circuitWorld = circuitWorlds.get(worldId);
    if (circuitWorld == null) {
      circuitWorld = circuitWorlds.computeIfAbsent(worldId, ignored -> new CircuitWorld(worldId, world.getName()));
    }
    return circuitWorld.event(block.getX(), block.getY(), block.getZ(), now);
  }

  public void remove(Block block, long now) {
    CircuitWorld circuitWorld = circuitWorlds.get(block.getWorld().getUID());
    if (circuitWorld == null) {
      return;
    }
    circuitWorld.remove(block.getX(), block.getY(), block.getZ(), now);
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
