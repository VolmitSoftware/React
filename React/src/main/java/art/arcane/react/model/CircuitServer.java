package art.arcane.react.model;

import art.arcane.volmlib.util.math.BlockPosition;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CircuitServer {
  private final Map<String, CircuitWorld> circuitWorlds;

  public CircuitServer() {
    circuitWorlds = new ConcurrentHashMap<>();
  }

  public CircuitObservation event(Block block, long now) {
    World world = block.getWorld();
    String worldId = world.getUID().toString();
    CircuitWorld circuitWorld = circuitWorlds.computeIfAbsent(
        worldId,
        ignored -> new CircuitWorld(worldId, world.getName())
    );
    return circuitWorld.event(new BlockPosition(block), now);
  }

  public void remove(Block block, long now) {
    CircuitWorld circuitWorld = circuitWorlds.get(block.getWorld().getUID().toString());
    if (circuitWorld == null) {
      return;
    }
    circuitWorld.remove(new BlockPosition(block), now);
  }

  public void rollWindow(long now, long inactivityMs) {
    for (Map.Entry<String, CircuitWorld> entry : circuitWorlds.entrySet()) {
      CircuitWorld circuitWorld = entry.getValue();
      circuitWorld.rollWindow(now, inactivityMs);
      if (circuitWorld.countCircuits() == 0) {
        circuitWorlds.remove(entry.getKey(), circuitWorld);
      }
    }
  }

  public CircuitSnapshot throttleWorst(long now, long durationMs) {
    CircuitSnapshot candidate = circuitWorlds.values().stream()
        .map(world -> world.worst(now))
        .filter(snapshot -> snapshot != null)
        .max(Comparator
            .comparingInt(CircuitSnapshot::events)
            .thenComparingInt(CircuitSnapshot::nodes))
        .orElse(null);
    if (candidate == null) {
      return null;
    }
    CircuitWorld world = circuitWorlds.get(candidate.worldId());
    return world == null ? null : world.throttle(candidate.circuitId(), now, durationMs);
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
