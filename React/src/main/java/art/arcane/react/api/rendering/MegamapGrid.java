/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.api.rendering;

import org.bukkit.block.BlockFace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class MegamapGrid {
  private MegamapGrid() {
  }

  public record FrameCell(
      int mapId,
      UUID worldId,
      BlockFace facing,
      int blockX,
      int blockY,
      int blockZ,
      String rendererId,
      boolean rotationAligned
  ) {
  }

  public record MegamapTile(int gridWidth, int gridHeight, int tileX, int tileY) {
  }

  public static Map<Integer, MegamapTile> solve(Collection<FrameCell> cells) {
    Map<Integer, MegamapTile> tiles = new HashMap<>();
    if (cells == null || cells.isEmpty()) {
      return tiles;
    }

    Map<GroupKey, Map<Long, FrameCell>> groups = new HashMap<>();
    for (FrameCell cell : cells) {
      if (cell == null || !cell.rotationAligned() || cell.worldId() == null) {
        continue;
      }

      String rendererId = normalize(cell.rendererId());
      if (rendererId.isBlank()) {
        continue;
      }

      PlaneMapping mapping = PlaneMapping.of(cell);
      if (mapping == null) {
        continue;
      }

      GroupKey key = new GroupKey(cell.worldId(), cell.facing(), mapping.plane(), rendererId);
      groups.computeIfAbsent(key, ignored -> new HashMap<>()).put(cellKey(mapping.u(), mapping.v()), cell);
    }

    for (Map<Long, FrameCell> group : groups.values()) {
      assignGroupTiles(group, tiles);
    }

    return tiles;
  }

  private static void assignGroupTiles(Map<Long, FrameCell> group, Map<Integer, MegamapTile> tiles) {
    Set<Long> visited = new HashSet<>();
    for (Long start : group.keySet()) {
      if (!visited.add(start)) {
        continue;
      }

      List<Long> component = new ArrayList<>();
      Deque<Long> queue = new ArrayDeque<>();
      queue.add(start);
      component.add(start);
      while (!queue.isEmpty()) {
        long current = queue.poll();
        int u = unpackU(current);
        int v = unpackV(current);
        long[] neighbors = {
            cellKey(u + 1, v),
            cellKey(u - 1, v),
            cellKey(u, v + 1),
            cellKey(u, v - 1)
        };
        for (long neighbor : neighbors) {
          if (group.containsKey(neighbor) && visited.add(neighbor)) {
            queue.add(neighbor);
            component.add(neighbor);
          }
        }
      }

      if (component.size() < 2) {
        continue;
      }

      int minU = Integer.MAX_VALUE;
      int maxU = Integer.MIN_VALUE;
      int minV = Integer.MAX_VALUE;
      int maxV = Integer.MIN_VALUE;
      Set<Integer> mapIds = new HashSet<>();
      boolean duplicateMapIds = false;
      for (long packed : component) {
        int u = unpackU(packed);
        int v = unpackV(packed);
        minU = Math.min(minU, u);
        maxU = Math.max(maxU, u);
        minV = Math.min(minV, v);
        maxV = Math.max(maxV, v);
        if (!mapIds.add(group.get(packed).mapId())) {
          duplicateMapIds = true;
        }
      }

      int gridWidth = maxU - minU + 1;
      int gridHeight = maxV - minV + 1;
      if (duplicateMapIds || component.size() != gridWidth * gridHeight) {
        continue;
      }

      for (long packed : component) {
        FrameCell cell = group.get(packed);
        tiles.put(cell.mapId(), new MegamapTile(
            gridWidth,
            gridHeight,
            unpackU(packed) - minU,
            unpackV(packed) - minV
        ));
      }
    }
  }

  private static String normalize(String rendererId) {
    return Objects.toString(rendererId, "").toLowerCase(Locale.ROOT).trim();
  }

  private static long cellKey(int u, int v) {
    return (((long) u) << 32) ^ (v & 0xffffffffL);
  }

  private static int unpackU(long key) {
    return (int) (key >> 32);
  }

  private static int unpackV(long key) {
    return (int) key;
  }

  private record GroupKey(UUID worldId, BlockFace facing, int plane, String rendererId) {
  }

  private record PlaneMapping(int u, int v, int plane) {
    private static PlaneMapping of(FrameCell cell) {
      return switch (cell.facing()) {
        case NORTH -> new PlaneMapping(-cell.blockX(), -cell.blockY(), cell.blockZ());
        case SOUTH -> new PlaneMapping(cell.blockX(), -cell.blockY(), cell.blockZ());
        case EAST -> new PlaneMapping(-cell.blockZ(), -cell.blockY(), cell.blockX());
        case WEST -> new PlaneMapping(cell.blockZ(), -cell.blockY(), cell.blockX());
        case UP -> new PlaneMapping(cell.blockX(), cell.blockZ(), cell.blockY());
        case DOWN -> new PlaneMapping(cell.blockX(), -cell.blockZ(), cell.blockY());
        default -> null;
      };
    }
  }
}
