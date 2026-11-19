package art.arcane.react.model;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class CircuitWorld {
  private static final int[][] NEIGHBORS = {
      {1, 0, 0}, {-1, 0, 0},
      {0, 1, 0}, {0, -1, 0},
      {0, 0, 1}, {0, 0, -1}
  };
  private static final long HORIZONTAL_MASK = (1L << 26) - 1L;
  private static final long VERTICAL_MASK = (1L << 12) - 1L;
  private static final long NO_CIRCUIT = 0L;

  private final UUID worldId;
  private final String world;
  private final Long2ObjectOpenHashMap<Circuit> circuits;
  private final Long2LongOpenHashMap blocks;
  private final long[] neighborCircuits;
  private volatile int blockCount;
  private long nextId;

  public CircuitWorld(UUID worldId, String world) {
    this.worldId = worldId;
    this.world = world;
    circuits = new Long2ObjectOpenHashMap<>();
    blocks = new Long2LongOpenHashMap();
    blocks.defaultReturnValue(NO_CIRCUIT);
    neighborCircuits = new long[NEIGHBORS.length + 1];
    nextId = 1L;
  }

  static long pack(int x, int y, int z) {
    return ((x & HORIZONTAL_MASK) << 38) | ((z & HORIZONTAL_MASK) << 12) | (y & VERTICAL_MASK);
  }

  static int unpackX(long position) {
    return (int) (position >> 38);
  }

  static int unpackY(long position) {
    return (int) ((position << 52) >> 52);
  }

  static int unpackZ(long position) {
    return (int) ((position << 26) >> 38);
  }

  public synchronized CircuitObservation event(int x, int y, int z, long now) {
    long position = pack(x, y, z);
    int neighborCount = collectNeighborCircuits(position, x, y, z);
    Circuit winner = selectWinner(neighborCount);
    if (winner == null) {
      winner = new Circuit(nextId++, now);
      circuits.put(winner.getId(), winner);
    }
    mergeInto(winner, neighborCount);
    long previousId = blocks.put(position, winner.getId());
    if (previousId != NO_CIRCUIT && previousId != winner.getId()) {
      Circuit previous = circuits.get(previousId);
      if (previous != null) {
        previous.remove(position);
        removeIfEmpty(previous);
      }
    }
    winner.add(position);
    winner.recordEvent(now);
    blockCount = blocks.size();
    return new CircuitObservation(winner.getId(), winner.isBlocked(now), winner.getBlockedUntilMs());
  }

  public void remove(int x, int y, int z, long now) {
    if (blockCount == 0) {
      return;
    }
    removePosition(pack(x, y, z), now);
  }

  public synchronized void rollWindow(long now, long inactivityMs) {
    LongArrayList expired = new LongArrayList();
    long inactivity = Math.max(1000L, inactivityMs);
    for (Circuit circuit : circuits.values()) {
      circuit.rollWindow();
      if (now - circuit.getLastEventMs() > inactivity) {
        expired.add(circuit.getId());
      }
    }
    LongIterator iterator = expired.iterator();
    while (iterator.hasNext()) {
      removeCircuit(iterator.nextLong());
    }
    blockCount = blocks.size();
  }

  public synchronized CircuitSnapshot worst(long now) {
    Circuit worst = null;
    for (Circuit circuit : circuits.values()) {
      if (circuit.getEvents() <= 0 || circuit.isBlocked(now)) {
        continue;
      }
      if (worst == null || circuit.getEvents() > worst.getEvents()) {
        worst = circuit;
      }
    }
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
    ObjectIterator<Long2LongMap.Entry> entries = blocks.long2LongEntrySet().fastIterator();
    while (entries.hasNext()) {
      Long2LongMap.Entry entry = entries.next();
      Circuit circuit = circuits.get(entry.getLongValue());
      if (circuit == null || !circuit.positions().contains(entry.getLongKey())) {
        return false;
      }
    }
    for (Circuit circuit : circuits.values()) {
      LongIterator positions = circuit.positions().iterator();
      while (positions.hasNext()) {
        if (blocks.get(positions.nextLong()) != circuit.getId()) {
          return false;
        }
      }
    }
    return true;
  }

  private synchronized void removePosition(long position, long now) {
    long circuitId = blocks.remove(position);
    if (circuitId == NO_CIRCUIT) {
      return;
    }
    Circuit circuit = circuits.get(circuitId);
    if (circuit != null) {
      circuit.remove(position);
      if (circuit.countBlocks() == 0) {
        circuits.remove(circuitId);
      } else {
        splitDisconnected(circuit, now);
      }
    }
    blockCount = blocks.size();
  }

  private int collectNeighborCircuits(long position, int x, int y, int z) {
    int count = addExistingCircuit(blocks.get(position), 0);
    for (int[] offset : NEIGHBORS) {
      count = addExistingCircuit(blocks.get(pack(x + offset[0], y + offset[1], z + offset[2])), count);
    }
    return count;
  }

  private int addExistingCircuit(long id, int count) {
    if (id == NO_CIRCUIT) {
      return count;
    }
    if (!circuits.containsKey(id)) {
      purgeStaleCircuit(id);
      return count;
    }
    for (int index = 0; index < count; index++) {
      if (neighborCircuits[index] == id) {
        return count;
      }
    }
    neighborCircuits[count] = id;
    return count + 1;
  }

  private void purgeStaleCircuit(long id) {
    ObjectIterator<Long2LongMap.Entry> entries = blocks.long2LongEntrySet().fastIterator();
    while (entries.hasNext()) {
      if (entries.next().getLongValue() == id) {
        entries.remove();
      }
    }
  }

  private Circuit selectWinner(int count) {
    Circuit winner = null;
    for (int index = 0; index < count; index++) {
      Circuit candidate = circuits.get(neighborCircuits[index]);
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

  private void mergeInto(Circuit winner, int count) {
    for (int index = 0; index < count; index++) {
      long id = neighborCircuits[index];
      if (id == winner.getId()) {
        continue;
      }
      Circuit losing = circuits.remove(id);
      if (losing == null) {
        continue;
      }
      winner.merge(losing);
      LongIterator positions = losing.positions().iterator();
      while (positions.hasNext()) {
        blocks.put(positions.nextLong(), winner.getId());
      }
    }
  }

  private void splitDisconnected(Circuit circuit, long now) {
    List<LongOpenHashSet> components = connectedComponents(circuit.positions());
    if (components.size() <= 1) {
      return;
    }
    components.sort(Comparator.comparingInt(LongOpenHashSet::size).reversed());
    circuit.positions().clear();
    LongIterator kept = components.getFirst().iterator();
    while (kept.hasNext()) {
      long position = kept.nextLong();
      circuit.add(position);
      blocks.put(position, circuit.getId());
    }
    for (int index = 1; index < components.size(); index++) {
      Circuit split = new Circuit(nextId++, now);
      if (circuit.isBlocked(now)) {
        split.blockUntil(circuit.getBlockedUntilMs());
      }
      LongIterator moved = components.get(index).iterator();
      while (moved.hasNext()) {
        long position = moved.nextLong();
        split.add(position);
        blocks.put(position, split.getId());
      }
      circuits.put(split.getId(), split);
    }
  }

  private List<LongOpenHashSet> connectedComponents(LongOpenHashSet positions) {
    LongOpenHashSet remaining = new LongOpenHashSet(positions);
    List<LongOpenHashSet> components = new ArrayList<>();
    LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    while (!remaining.isEmpty()) {
      long first = remaining.iterator().nextLong();
      remaining.remove(first);
      LongOpenHashSet component = new LongOpenHashSet();
      queue.enqueue(first);
      while (!queue.isEmpty()) {
        long current = queue.dequeueLong();
        component.add(current);
        int x = unpackX(current);
        int y = unpackY(current);
        int z = unpackZ(current);
        for (int[] offset : NEIGHBORS) {
          long neighbor = pack(x + offset[0], y + offset[1], z + offset[2]);
          if (remaining.remove(neighbor)) {
            queue.enqueue(neighbor);
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
    LongIterator positions = circuit.positions().iterator();
    long representative = positions.nextLong();
    int representativeX = unpackX(representative);
    int representativeY = unpackY(representative);
    int representativeZ = unpackZ(representative);
    int minX = representativeX;
    int minY = representativeY;
    int minZ = representativeZ;
    int maxX = representativeX;
    int maxY = representativeY;
    int maxZ = representativeZ;
    while (positions.hasNext()) {
      long position = positions.nextLong();
      int x = unpackX(position);
      int y = unpackY(position);
      int z = unpackZ(position);
      minX = Math.min(minX, x);
      minY = Math.min(minY, y);
      minZ = Math.min(minZ, z);
      maxX = Math.max(maxX, x);
      maxY = Math.max(maxY, y);
      maxZ = Math.max(maxZ, z);
    }
    return new CircuitSnapshot(
        circuit.getId(),
        worldId.toString(),
        world,
        circuit.getEvents(),
        circuit.countBlocks(),
        representativeX,
        representativeY,
        representativeZ,
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
    LongIterator positions = removed.positions().iterator();
    while (positions.hasNext()) {
      blocks.remove(positions.nextLong(), id);
    }
  }

  private void removeIfEmpty(Circuit circuit) {
    if (circuit.countBlocks() == 0) {
      circuits.remove(circuit.getId());
    }
  }
}
