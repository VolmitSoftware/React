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

  public enum MegamapMode {
    MAGNIFY,
    ADAPTIVE
  }

  public enum MegamapDetail {
    COMPACT,
    EXPANDED,
    RICH,
    MAXIMAL;

    public static MegamapDetail forGrid(int gridWidth, int gridHeight) {
      int tiles = Math.max(1, gridWidth) * Math.max(1, gridHeight);
      if (tiles >= 9) {
        return MAXIMAL;
      }
      if (tiles >= 4) {
        return RICH;
      }
      if (tiles >= 2) {
        return EXPANDED;
      }
      return COMPACT;
    }

    public boolean atLeast(MegamapDetail other) {
      return other == null || ordinal() >= other.ordinal();
    }
  }

  public enum DefectReason {
    HOLE,
    DUPLICATE_MAP_ID,
    ROTATED,
    MIXED_RENDERER
  }

  public record MegamapCapability(
      MegamapMode mode,
      int minGridWidth,
      int minGridHeight,
      int maxGridWidth,
      int maxGridHeight
  ) {
    private static final MegamapCapability MAGNIFY_ONLY =
        new MegamapCapability(MegamapMode.MAGNIFY, 1, 1, 1, 1);

    public MegamapCapability {
      Objects.requireNonNull(mode, "megamap mode");
      minGridWidth = Math.max(1, minGridWidth);
      minGridHeight = Math.max(1, minGridHeight);
      maxGridWidth = Math.max(minGridWidth, maxGridWidth);
      maxGridHeight = Math.max(minGridHeight, maxGridHeight);
    }

    public static MegamapCapability magnify() {
      return MAGNIFY_ONLY;
    }

    public static MegamapCapability adaptive(int maxGridWidth, int maxGridHeight) {
      return new MegamapCapability(MegamapMode.ADAPTIVE, 1, 1, maxGridWidth, maxGridHeight);
    }

    public static MegamapCapability adaptive(
        int minGridWidth,
        int minGridHeight,
        int maxGridWidth,
        int maxGridHeight
    ) {
      return new MegamapCapability(MegamapMode.ADAPTIVE, minGridWidth, minGridHeight, maxGridWidth, maxGridHeight);
    }

    public boolean adaptive() {
      return mode == MegamapMode.ADAPTIVE;
    }

    public boolean adaptsTo(int gridWidth, int gridHeight) {
      if (mode != MegamapMode.ADAPTIVE) {
        return false;
      }

      return gridWidth >= minGridWidth
          && gridHeight >= minGridHeight
          && gridWidth <= maxGridWidth
          && gridHeight <= maxGridHeight;
    }

    public int contentColumns(int gridWidth) {
      return Math.max(1, Math.min(gridWidth, maxGridWidth));
    }

    public int contentRows(int gridHeight) {
      return Math.max(1, Math.min(gridHeight, maxGridHeight));
    }

    public MegamapDetail detailFor(int gridWidth, int gridHeight) {
      return adaptsTo(gridWidth, gridHeight) ? MegamapDetail.forGrid(gridWidth, gridHeight) : MegamapDetail.COMPACT;
    }
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
    public int tiles() {
      return Math.max(1, gridWidth) * Math.max(1, gridHeight);
    }
  }

  public record MegamapDefect(int mapId, DefectReason reason) {
  }

  public record MegamapViewport(
      int width,
      int height,
      int offsetX,
      int offsetY,
      int scale,
      int textScale,
      int contentX,
      int contentY,
      int contentSpan,
      boolean adaptive,
      boolean capped
  ) {
    public boolean fullyCovers(int canvasSize) {
      return contentX <= 0 && contentY <= 0
          && contentX + contentSpan >= canvasSize
          && contentY + contentSpan >= canvasSize;
    }

    public boolean coversAnything(int canvasSize) {
      return adaptive
          || (contentX < canvasSize && contentX + contentSpan > 0
          && contentY < canvasSize && contentY + contentSpan > 0);
    }
  }

  public static MegamapViewport viewportFor(
      MegamapTile tile,
      MegamapCapability capability,
      int tileBudget,
      int canvasSize
  ) {
    int gridWidth = Math.max(1, tile.gridWidth());
    int gridHeight = Math.max(1, tile.gridHeight());
    boolean capped = tile.tiles() > Math.max(1, tileBudget);
    if (capability != null && capability.adaptsTo(gridWidth, gridHeight) && !capped) {
      return new MegamapViewport(
          gridWidth * canvasSize,
          gridHeight * canvasSize,
          tile.tileX() * canvasSize,
          tile.tileY() * canvasSize,
          1,
          Math.min(gridWidth, gridHeight),
          0,
          0,
          Math.max(gridWidth, gridHeight) * canvasSize,
          true,
          false
      );
    }

    int scale = Math.min(gridWidth, gridHeight);
    int span = canvasSize * scale;
    int contentX = (((canvasSize * gridWidth) - span) / 2) - (tile.tileX() * canvasSize);
    int contentY = (((canvasSize * gridHeight) - span) / 2) - (tile.tileY() * canvasSize);
    return new MegamapViewport(
        canvasSize,
        canvasSize,
        -contentX,
        -contentY,
        scale,
        1,
        contentX,
        contentY,
        span,
        false,
        capped
    );
  }

  public record MegamapSolution(Map<Integer, MegamapTile> tiles, Map<Integer, MegamapDefect> defects) {
    public static final MegamapSolution EMPTY = new MegamapSolution(Map.of(), Map.of());

    public MegamapTile tileFor(int mapId) {
      return tiles.get(mapId);
    }

    public MegamapDefect defectFor(int mapId) {
      return defects.get(mapId);
    }

    public boolean isEmpty() {
      return tiles.isEmpty() && defects.isEmpty();
    }
  }

  public static Map<Integer, MegamapTile> solve(Collection<FrameCell> cells) {
    return analyze(cells).tiles();
  }

  public static MegamapSolution analyze(Collection<FrameCell> cells) {
    if (cells == null || cells.isEmpty()) {
      return MegamapSolution.EMPTY;
    }

    Map<GroupKey, Map<Long, FrameCell>> groups = new HashMap<>();
    Map<PlaneKey, Map<Long, FrameCell>> planes = new HashMap<>();
    List<FrameCell> rotated = new ArrayList<>();
    for (FrameCell cell : cells) {
      if (cell == null || cell.worldId() == null) {
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

      if (!cell.rotationAligned()) {
        rotated.add(cell);
        continue;
      }

      long key = cellKey(mapping.u(), mapping.v());
      planes.computeIfAbsent(new PlaneKey(cell.worldId(), cell.facing(), mapping.plane()), ignored -> new HashMap<>())
          .put(key, cell);
      groups.computeIfAbsent(new GroupKey(cell.worldId(), cell.facing(), mapping.plane(), rendererId), ignored -> new HashMap<>())
          .put(key, cell);
    }

    Map<Integer, MegamapTile> tiles = new HashMap<>();
    Map<Integer, MegamapDefect> defects = new HashMap<>();
    for (Map<Long, FrameCell> group : groups.values()) {
      assignGroupTiles(group, tiles, defects);
    }

    recordRotationDefects(rotated, planes, defects);
    recordMixedRendererDefects(planes, tiles, defects);

    if (tiles.isEmpty() && defects.isEmpty()) {
      return MegamapSolution.EMPTY;
    }

    return new MegamapSolution(Map.copyOf(tiles), Map.copyOf(defects));
  }

  private static void assignGroupTiles(
      Map<Long, FrameCell> group,
      Map<Integer, MegamapTile> tiles,
      Map<Integer, MegamapDefect> defects
  ) {
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
      if (duplicateMapIds) {
        recordComponentDefect(group, component, defects, DefectReason.DUPLICATE_MAP_ID);
        continue;
      }

      if (component.size() != gridWidth * gridHeight) {
        recordComponentDefect(group, component, defects, DefectReason.HOLE);
        continue;
      }

      for (long packed : component) {
        FrameCell cell = group.get(packed);
        MegamapTile tile = new MegamapTile(
            gridWidth,
            gridHeight,
            unpackU(packed) - minU,
            unpackV(packed) - minV
        );
        MegamapDefect existing = defects.get(cell.mapId());
        if (existing != null && existing.reason() == DefectReason.DUPLICATE_MAP_ID) {
          continue;
        }

        MegamapTile previous = tiles.put(cell.mapId(), tile);
        if (previous != null && !previous.equals(tile)) {
          tiles.remove(cell.mapId());
          defects.put(cell.mapId(), new MegamapDefect(cell.mapId(), DefectReason.DUPLICATE_MAP_ID));
        }
      }
    }
  }

  private static void recordComponentDefect(
      Map<Long, FrameCell> group,
      List<Long> component,
      Map<Integer, MegamapDefect> defects,
      DefectReason reason
  ) {
    for (long packed : component) {
      FrameCell cell = group.get(packed);
      if (cell == null) {
        continue;
      }

      defects.putIfAbsent(cell.mapId(), new MegamapDefect(cell.mapId(), reason));
    }
  }

  private static void recordRotationDefects(
      List<FrameCell> rotated,
      Map<PlaneKey, Map<Long, FrameCell>> planes,
      Map<Integer, MegamapDefect> defects
  ) {
    for (FrameCell cell : rotated) {
      PlaneMapping mapping = PlaneMapping.of(cell);
      if (mapping == null) {
        continue;
      }

      Map<Long, FrameCell> plane = planes.get(new PlaneKey(cell.worldId(), cell.facing(), mapping.plane()));
      if (plane == null || !hasNeighbor(plane, mapping.u(), mapping.v())) {
        continue;
      }

      defects.putIfAbsent(cell.mapId(), new MegamapDefect(cell.mapId(), DefectReason.ROTATED));
    }
  }

  private static void recordMixedRendererDefects(
      Map<PlaneKey, Map<Long, FrameCell>> planes,
      Map<Integer, MegamapTile> tiles,
      Map<Integer, MegamapDefect> defects
  ) {
    for (Map<Long, FrameCell> plane : planes.values()) {
      for (Map.Entry<Long, FrameCell> entry : plane.entrySet()) {
        FrameCell cell = entry.getValue();
        if (tiles.containsKey(cell.mapId()) || defects.containsKey(cell.mapId())) {
          continue;
        }

        long packed = entry.getKey();
        if (!hasNeighborWithOtherRenderer(plane, unpackU(packed), unpackV(packed), normalize(cell.rendererId()))) {
          continue;
        }

        defects.put(cell.mapId(), new MegamapDefect(cell.mapId(), DefectReason.MIXED_RENDERER));
      }
    }
  }

  private static boolean hasNeighbor(Map<Long, FrameCell> plane, int u, int v) {
    return plane.containsKey(cellKey(u + 1, v))
        || plane.containsKey(cellKey(u - 1, v))
        || plane.containsKey(cellKey(u, v + 1))
        || plane.containsKey(cellKey(u, v - 1));
  }

  private static boolean hasNeighborWithOtherRenderer(Map<Long, FrameCell> plane, int u, int v, String rendererId) {
    long[] neighbors = {
        cellKey(u + 1, v),
        cellKey(u - 1, v),
        cellKey(u, v + 1),
        cellKey(u, v - 1)
    };
    for (long neighbor : neighbors) {
      FrameCell cell = plane.get(neighbor);
      if (cell != null && !normalize(cell.rendererId()).equals(rendererId)) {
        return true;
      }
    }
    return false;
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

  private record PlaneKey(UUID worldId, BlockFace facing, int plane) {
  }

  private record PlaneMapping(int u, int v, int plane) {
    private static PlaneMapping of(FrameCell cell) {
      if (cell.facing() == null) {
        return null;
      }

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
