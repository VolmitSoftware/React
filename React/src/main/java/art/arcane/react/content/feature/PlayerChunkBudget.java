package art.arcane.react.content.feature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PlayerChunkBudget {
  private PlayerChunkBudget() {
  }

  static Distances constrain(List<ChunkPosition> positions, Limits limits, Distances ceiling, Budgets budgets, int currentSimulation) {
    int maximumView = Math.max(limits.minimumView(), ceiling.view());
    int maximumSimulation = Math.min(maximumView, Math.max(limits.minimumSimulation(), ceiling.simulation()));
    int simulation = largestRadius(positions, Math.min(limits.minimumSimulation(), maximumSimulation),
        maximumSimulation, Math.max(1L, budgets.ticking()), 0L);
    long ticking = count(positions, Math.min(currentSimulation, simulation));
    int view = largestRadius(positions, limits.minimumView(), maximumView,
        Math.max(1L, budgets.viewOnly()), ticking);
    return new Distances(view, Math.min(view, simulation));
  }

  static long count(List<ChunkPosition> positions, int radius) {
    if (positions.isEmpty()) {
      return 0L;
    }
    int boundedRadius = Math.max(0, Math.min(32, radius));
    Set<ChunkPosition> unique = new HashSet<>(positions);
    List<Edge> edges = new ArrayList<>(unique.size() * 2);
    long[] zCoordinates = new long[unique.size() * 2];
    int index = 0;
    for (ChunkPosition position : unique) {
      long lowerZ = (long) position.z() - boundedRadius;
      long upperZ = (long) position.z() + boundedRadius + 1L;
      edges.add(new Edge((long) position.x() - boundedRadius, lowerZ, upperZ, 1));
      edges.add(new Edge((long) position.x() + boundedRadius + 1L, lowerZ, upperZ, -1));
      zCoordinates[index++] = lowerZ;
      zCoordinates[index++] = upperZ;
    }
    Arrays.sort(zCoordinates);
    int coordinateCount = 0;
    for (long coordinate : zCoordinates) {
      if (coordinateCount == 0 || coordinate != zCoordinates[coordinateCount - 1]) {
        zCoordinates[coordinateCount++] = coordinate;
      }
    }
    Coverage coverage = new Coverage(Arrays.copyOf(zCoordinates, coordinateCount));
    edges.sort(Comparator.comparingLong(Edge::x));
    long area = 0L;
    long previousX = edges.getFirst().x();
    for (Edge edge : edges) {
      area += (edge.x() - previousX) * coverage.length();
      coverage.update(edge.lowerZ(), edge.upperZ(), edge.delta());
      previousX = edge.x();
    }
    return area;
  }

  private static int largestRadius(List<ChunkPosition> positions, int minimum, int maximum, long budget, long excluded) {
    int low = minimum;
    int high = maximum;
    while (low < high) {
      int candidate = low + (high - low + 1) / 2;
      if (count(positions, candidate) - excluded <= budget) {
        low = candidate;
      } else {
        high = candidate - 1;
      }
    }
    return low;
  }

  record ChunkPosition(int x, int z) {
  }

  record Distances(int view, int simulation) {
  }

  record Limits(int minimumView, int minimumSimulation) {
  }

  record Budgets(long ticking, long viewOnly) {
  }

  static final class Recovery {
    private int viewChecks;
    private int simulationChecks;

    Distances next(Distances current, Distances target, boolean healthy, int requiredChecks, int step) {
      int required = Math.max(1, requiredChecks);
      int increment = Math.max(1, Math.min(32, step));
      viewChecks = healthy && target.view() > current.view() ? Math.min(required, viewChecks + 1) : 0;
      simulationChecks = healthy && target.simulation() > current.simulation()
          ? Math.min(required, simulationChecks + 1) : 0;
      int view = target.view() <= current.view() ? target.view()
          : viewChecks >= required ? Math.min(target.view(), current.view() + increment) : current.view();
      int simulation = target.simulation() <= current.simulation() ? target.simulation()
          : simulationChecks >= required ? Math.min(target.simulation(), current.simulation() + increment) : current.simulation();
      if (viewChecks >= required) {
        viewChecks = 0;
      }
      if (simulationChecks >= required) {
        simulationChecks = 0;
      }
      return new Distances(view, Math.min(view, simulation));
    }
  }

  private record Edge(long x, long lowerZ, long upperZ, int delta) {
  }

  private static final class Coverage {
    private final long[] coordinates;
    private final int[] covers;
    private final long[] lengths;

    private Coverage(long[] coordinates) {
      this.coordinates = coordinates;
      covers = new int[coordinates.length * 4];
      lengths = new long[coordinates.length * 4];
    }

    private long length() {
      return lengths[1];
    }

    private void update(long from, long to, int delta) {
      update(1, 0, coordinates.length - 2,
          Arrays.binarySearch(coordinates, from), Arrays.binarySearch(coordinates, to) - 1, delta);
    }

    private void update(int node, int start, int end, int from, int to, int delta) {
      if (from <= start && end <= to) {
        covers[node] += delta;
      } else {
        int middle = (start + end) >>> 1;
        if (from <= middle) {
          update(node * 2, start, middle, from, to, delta);
        }
        if (to > middle) {
          update(node * 2 + 1, middle + 1, end, from, to, delta);
        }
      }
      lengths[node] = covers[node] > 0 ? coordinates[end + 1] - coordinates[start]
          : start == end ? 0L : lengths[node * 2] + lengths[node * 2 + 1];
    }
  }
}
