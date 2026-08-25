package art.arcane.react.content.feature;

import art.arcane.react.api.rendering.Region;

import java.util.Objects;

final class ChunkHeatmapLayout {
  private static final int MCA_REGION_SIZE = 32;

  private final Region grid;
  private final int centerChunkX;
  private final int centerChunkZ;
  private final int minChunkX;
  private final int maxChunkX;
  private final int minChunkZ;
  private final int maxChunkZ;
  private final int columns;
  private final int rows;
  private final int cellSize;

  private ChunkHeatmapLayout(
      Region grid,
      int centerChunkX,
      int centerChunkZ,
      int columns,
      int rows,
      int cellSize
  ) {
    this.grid = grid;
    this.centerChunkX = centerChunkX;
    this.centerChunkZ = centerChunkZ;
    this.columns = columns;
    this.rows = rows;
    this.cellSize = cellSize;
    minChunkX = centerChunkX - (columns / 2);
    maxChunkX = minChunkX + columns - 1;
    minChunkZ = centerChunkZ - (rows / 2);
    maxChunkZ = minChunkZ + rows - 1;
  }

  static ChunkHeatmapLayout create(
      Region body,
      int centerChunkX,
      int centerChunkZ,
      int requestedCellSize,
      int radiusLimit,
      int leftAxisWidth,
      int topAxisHeight
  ) {
    Region resolvedBody = Objects.requireNonNull(body);
    int left = Math.max(0, Math.min(leftAxisWidth, Math.max(0, resolvedBody.width() - 1)));
    int top = Math.max(0, Math.min(topAxisHeight, Math.max(0, resolvedBody.height() - 1)));
    int availableWidth = Math.max(1, resolvedBody.width() - left);
    int availableHeight = Math.max(1, resolvedBody.height() - top);
    int cellSize = Math.max(1, Math.min(
        requestedCellSize,
        Math.min(availableWidth, availableHeight)
    ));
    int columns = fittedCellCount(availableWidth, cellSize, radiusLimit);
    int rows = fittedCellCount(availableHeight, cellSize, radiusLimit);
    int gridWidth = columns * cellSize;
    int gridHeight = rows * cellSize;
    int gridX = resolvedBody.x() + left + ((availableWidth - gridWidth) / 2);
    int gridY = resolvedBody.y() + top + ((availableHeight - gridHeight) / 2);
    return new ChunkHeatmapLayout(
        new Region(gridX, gridY, gridWidth, gridHeight),
        centerChunkX,
        centerChunkZ,
        columns,
        rows,
        cellSize
    );
  }

  Region grid() {
    return grid;
  }

  int centerChunkX() {
    return centerChunkX;
  }

  int centerChunkZ() {
    return centerChunkZ;
  }

  int minChunkX() {
    return minChunkX;
  }

  int maxChunkX() {
    return maxChunkX;
  }

  int minChunkZ() {
    return minChunkZ;
  }

  int maxChunkZ() {
    return maxChunkZ;
  }

  int columns() {
    return columns;
  }

  int rows() {
    return rows;
  }

  int cellSize() {
    return cellSize;
  }

  int cellX(int chunkX) {
    return grid.x() + ((chunkX - minChunkX) * cellSize);
  }

  int cellY(int chunkZ) {
    return grid.y() + ((chunkZ - minChunkZ) * cellSize);
  }

  Region cell(int chunkX, int chunkZ) {
    if (!contains(chunkX, chunkZ)) {
      return Region.EMPTY;
    }
    return new Region(cellX(chunkX), cellY(chunkZ), cellSize, cellSize);
  }

  int indexOf(int chunkX, int chunkZ) {
    if (!contains(chunkX, chunkZ)) {
      return -1;
    }
    return ((chunkZ - minChunkZ) * columns) + (chunkX - minChunkX);
  }

  boolean contains(int chunkX, int chunkZ) {
    return chunkX >= minChunkX
        && chunkX <= maxChunkX
        && chunkZ >= minChunkZ
        && chunkZ <= maxChunkZ;
  }

  int radiusChunks() {
    return Math.max(columns / 2, rows / 2);
  }

  static boolean startsMcaRegion(int chunkCoordinate) {
    return Math.floorMod(chunkCoordinate, MCA_REGION_SIZE) == 0;
  }

  static boolean endsMcaRegion(int chunkCoordinate) {
    return Math.floorMod(chunkCoordinate, MCA_REGION_SIZE) == MCA_REGION_SIZE - 1;
  }

  private static int fittedCellCount(int availablePixels, int cellSize, int radiusLimit) {
    int count = Math.max(1, availablePixels / cellSize);
    if ((count & 1) == 0) {
      count--;
    }
    if (radiusLimit <= 0) {
      return count;
    }
    long limited = ((long) radiusLimit * 2L) + 1L;
    return (int) Math.min(count, Math.min(Integer.MAX_VALUE, limited));
  }
}
