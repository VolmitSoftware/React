package art.arcane.react.api.web.heatmap;

public final class HeatmapViewportPlanner {
    public static final int MAX_RADIUS = 1_875_000;
    public static final int TARGET_CELLS_PER_AXIS = 33;
    public static final int MAX_CELLS_PER_AXIS = TARGET_CELLS_PER_AXIS + 1;

    public HeatmapViewportPlan plan(int centerChunkX, int centerChunkZ, int radius) {
        if (radius < 1 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("Heatmap radius must be between 1 and " + MAX_RADIUS);
        }

        long diameter = ((long) radius * 2L) + 1L;
        long minimumCellSize = (diameter + TARGET_CELLS_PER_AXIS - 1L) / TARGET_CELLS_PER_AXIS;
        int cellSizeChunks = nextPowerOfTwo(minimumCellSize);
        long minimumChunkX = (long) centerChunkX - radius;
        long maximumChunkX = (long) centerChunkX + radius;
        long minimumChunkZ = (long) centerChunkZ - radius;
        long maximumChunkZ = (long) centerChunkZ + radius;
        long originChunkX = alignDown(minimumChunkX, cellSizeChunks);
        long originChunkZ = alignDown(minimumChunkZ, cellSizeChunks);
        long lastOriginChunkX = alignDown(maximumChunkX, cellSizeChunks);
        long lastOriginChunkZ = alignDown(maximumChunkZ, cellSizeChunks);
        int width = Math.toIntExact(((lastOriginChunkX - originChunkX) / cellSizeChunks) + 1L);
        int height = Math.toIntExact(((lastOriginChunkZ - originChunkZ) / cellSizeChunks) + 1L);
        long scanMaximumChunkX = lastOriginChunkX + cellSizeChunks - 1L;
        long scanMaximumChunkZ = lastOriginChunkZ + cellSizeChunks - 1L;
        if (width > MAX_CELLS_PER_AXIS || height > MAX_CELLS_PER_AXIS) {
            throw new IllegalStateException("Heatmap viewport exceeds the cell budget");
        }

        return new HeatmapViewportPlan(
            centerChunkX,
            centerChunkZ,
            radius,
            checkedChunkCoordinate(originChunkX),
            checkedChunkCoordinate(originChunkZ),
            width,
            height,
            cellSizeChunks,
            checkedChunkCoordinate(originChunkX),
            checkedChunkCoordinate(scanMaximumChunkX),
            checkedChunkCoordinate(originChunkZ),
            checkedChunkCoordinate(scanMaximumChunkZ)
        );
    }

    private int nextPowerOfTwo(long minimum) {
        if (minimum <= 1L) {
            return 1;
        }
        int value = 1;
        while (value < minimum) {
            value = Math.multiplyExact(value, 2);
        }
        return value;
    }

    private long alignDown(long coordinate, int cellSizeChunks) {
        return Math.floorDiv(coordinate, (long) cellSizeChunks) * cellSizeChunks;
    }

    private int checkedChunkCoordinate(long coordinate) {
        if (coordinate < Integer.MIN_VALUE || coordinate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Heatmap viewport exceeds supported chunk coordinates");
        }
        return (int) coordinate;
    }

    public record HeatmapViewportPlan(
        int centerChunkX,
        int centerChunkZ,
        int radius,
        int originChunkX,
        int originChunkZ,
        int width,
        int height,
        int cellSizeChunks,
        int scanMinimumChunkX,
        int scanMaximumChunkX,
        int scanMinimumChunkZ,
        int scanMaximumChunkZ
    ) {}
}
