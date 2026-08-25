package art.arcane.react.model;

import art.arcane.volmlib.util.math.BlockPosition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CircuitWorld {
  private static final int[][] NEIGHBORS = {
      {1, 0, 0}, {-1, 0, 0},
      {0, 1, 0}, {0, -1, 0},
      {0, 0, 1}, {0, 0, -1}
  };

  private final String worldId;
  private final String world;
  private final Map<Long, Circuit> circuits;
  private final Map<BlockPosition, Long> blocks;
  private long nextId;

  public CircuitWorld(String worldId, String world) {
    this.worldId = worldId;
    this.world = world;
    circuits = new HashMap<>();
    blocks = new HashMap<>();
    nextId = 1L;
  }

  public synchronized CircuitObservation event(BlockPosition position, long now) {
    Set<Long> neighbors = neighborCircuitIds(position);
    Circuit winner = selectWinner(neighbors);
    if (winner == null) {
      winner = new Circuit(nextId++, now);
      circuits.put(winner.getId(), winner);
    }
    mergeInto(winner, neighbors);
    Long previousId = blocks.put(position, winner.getId());
    if (previousId != null && previousId != winner.getId()) {
      Circuit previous = circuits.get(previousId);
      if (previous != null) {
        previous.remove(position);
        removeIfEmpty(previous);
      }
    }
    winner.add(position);
    winner.recordEvent(now);
    return new CircuitObservation(winner.getId(), winner.isBlocked(now), winner.getBlockedUntilMs());
  }

  public synchronized void remove(BlockPosition position, long now) {
    Long circuitId = blocks.remove(position);
    if (circuitId == null) {
      return;
    }
    Circuit circuit = circuits.get(circuitId);
    if (circuit == null) {
      return;
    }
    circuit.remove(position);
    if (circuit.countBlocks() == 0) {
      circuits.remove(circuitId);
      return;
    }
    splitDisconnected(circuit, now);
  }

  public synchronized void rollWindow(long now, long inactivityMs) {
    List<Long> expired = new ArrayList<>();
    for (Circuit circuit : circuits.values()) {
      circuit.rollWindow();
      if (now - circuit.getLastEventMs() > Math.max(1000L, inactivityMs)) {
        expired.add(circuit.getId());
      }
    }
    for (Long id : expired) {
      removeCircuit(id);
    }
  }

  public synchronized CircuitSnapshot worst(long now) {
    Circuit worst = circuits.values().stream()
        .filter(circuit -> circuit.getEvents() > 0)
        .filter(circuit -> !circuit.isBlocked(now))
        .max(Comparator.comparingInt(Circuit::getEvents))
        .orElse(null);
    return snapshot(worst);
  }

  public synchronized CircuitSnapshot throttle(long circuitId, long now, long durationMs) {
    Circuit circuit = circuits.get(circuitId);
    if (circuit == null || circuit.isBlocked(now)) {
      return null;
    }
    circuit.blockUntil(now + Math.max(1L, durationMs));
    return snapshot(circuit);
  }

  public synchronized int countCircuits() {
    return circuits.size();
  }

  public synchronized int countBlocks() {
    return blocks.size();
  }

  public synchronized boolean isConsistent() {
    for (Map.Entry<BlockPosition, Long> entry : blocks.entrySet()) {
      Circuit circuit = circuits.get(entry.getValue());
      if (circuit == null || !circuit.positions().contains(entry.getKey())) {
        return false;
      }
    }
    for (Circuit circuit : circuits.values()) {
      for (BlockPosition position : circuit.positions()) {
        if (!Long.valueOf(circuit.getId()).equals(blocks.get(position))) {
          return false;
        }
      }
    }
    return true;
  }

  private Set<Long> neighborCircuitIds(BlockPosition position) {
    Set<Long> ids = new HashSet<>();
    addExistingId(ids, blocks.get(position));
    for (int[] offset : NEIGHBORS) {
      addExistingId(ids, blocks.get(position.add(offset[0], offset[1], offset[2])));
    }
    return ids;
  }

  private void addExistingId(Set<Long> ids, Long id) {
    if (id == null) {
      return;
    }
    if (circuits.containsKey(id)) {
      ids.add(id);
      return;
    }
    blocks.entrySet().removeIf(entry -> id.equals(entry.getValue()));
  }

  private Circuit selectWinner(Set<Long> ids) {
    Circuit winner = null;
    for (Long id : ids) {
      Circuit candidate = circuits.get(id);
      if (candidate == null) {
        continue;
      }
      if (winner == null
          || candidate.countBlocks() > winner.countBlocks()
          || candidate.countBlocks() == winner.countBlocks() && candidate.getId() < winner.getId()) {
        winner = candidate;
      }
    }
    return winner;
  }

  private void mergeInto(Circuit winner, Set<Long> ids) {
    for (Long id : ids) {
      if (id == winner.getId()) {
        continue;
      }
      Circuit losing = circuits.remove(id);
      if (losing == null) {
        continue;
      }
      winner.merge(losing);
      for (BlockPosition position : losing.positions()) {
        blocks.put(position, winner.getId());
      }
    }
  }

  private void splitDisconnected(Circuit circuit, long now) {
    List<Set<BlockPosition>> components = connectedComponents(circuit.positions());
    if (components.size() <= 1) {
      return;
    }
    components.sort(Comparator.comparingInt(Set<BlockPosition>::size).reversed());
    circuit.positions().clear();
    for (BlockPosition position : components.getFirst()) {
      circuit.add(position);
      blocks.put(position, circuit.getId());
    }
    for (int index = 1; index < components.size(); index++) {
      Circuit split = new Circuit(nextId++, now);
      if (circuit.isBlocked(now)) {
        split.blockUntil(circuit.getBlockedUntilMs());
      }
      for (BlockPosition position : components.get(index)) {
        split.add(position);
        blocks.put(position, split.getId());
      }
      circuits.put(split.getId(), split);
    }
  }

  private List<Set<BlockPosition>> connectedComponents(Set<BlockPosition> positions) {
    Set<BlockPosition> remaining = new HashSet<>(positions);
    List<Set<BlockPosition>> components = new ArrayList<>();
    while (!remaining.isEmpty()) {
      BlockPosition first = remaining.iterator().next();
      remaining.remove(first);
      Set<BlockPosition> component = new HashSet<>();
      ArrayDeque<BlockPosition> queue = new ArrayDeque<>();
      queue.add(first);
      while (!queue.isEmpty()) {
        BlockPosition current = queue.removeFirst();
        component.add(current);
        for (int[] offset : NEIGHBORS) {
          BlockPosition neighbor = current.add(offset[0], offset[1], offset[2]);
          if (remaining.remove(neighbor)) {
            queue.addLast(neighbor);
          }
        }
      }
      components.add(component);
    }
    return components;
  }

  private CircuitSnapshot snapshot(Circuit circuit) {
    if (circuit == null || circuit.positions().isEmpty()) {
      return null;
    }
    BlockPosition representative = circuit.positions().iterator().next();
    int minX = representative.getX();
    int minY = representative.getY();
    int minZ = representative.getZ();
    int maxX = minX;
    int maxY = minY;
    int maxZ = minZ;
    for (BlockPosition position : circuit.positions()) {
      minX = Math.min(minX, position.getX());
      minY = Math.min(minY, position.getY());
      minZ = Math.min(minZ, position.getZ());
      maxX = Math.max(maxX, position.getX());
      maxY = Math.max(maxY, position.getY());
      maxZ = Math.max(maxZ, position.getZ());
    }
    return new CircuitSnapshot(
        circuit.getId(),
        worldId,
        world,
        circuit.getEvents(),
        circuit.countBlocks(),
        representative.getX(),
        representative.getY(),
        representative.getZ(),
        minX,
        minY,
        minZ,
        maxX,
        maxY,
        maxZ,
        circuit.getBlockedUntilMs()
    );
  }

  private void removeCircuit(long id) {
    Circuit removed = circuits.remove(id);
    if (removed == null) {
      return;
    }
    for (BlockPosition position : removed.positions()) {
      blocks.remove(position, id);
    }
  }

  private void removeIfEmpty(Circuit circuit) {
    if (circuit.countBlocks() == 0) {
      circuits.remove(circuit.getId());
    }
  }
}
