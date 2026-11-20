package art.arcane.react.api.web.heatmap;

import art.arcane.react.core.controller.LoadedChunkCoordinate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Buckets a heatmap's chunk scores over the loaded chunks of a viewport, whatever indexes those chunks. */
public final class HeatmapGridScanner {
    private final HeatmapViewportPlanner viewportPlanner = new HeatmapViewportPlanner();

    @FunctionalInterface
    public interface LoadedChunks {
        List<LoadedChunkCoordinate> inBounds(UUID worldId, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ);
    }

    public HeatmapScan scan(ChunkGridExporter exporter, HeatmapWorldRef world, Integer centerX, Integer centerZ, int radius,
                            LoadedChunks loadedChunks) {
        int centerChunkX = centerX != null ? centerX : world.spawnChunkX();
        int centerChunkZ = centerZ != null ? centerZ : world.spawnChunkZ();
        HeatmapViewportPlanner.HeatmapViewportPlan viewport = viewportPlanner.plan(
            centerChunkX,
            centerChunkZ,
            radius
        );
        long capturedAtMs = System.currentTimeMillis();
        List<LoadedChunkCoordinate> loaded = loadedChunks.inBounds(
            world.worldId(),
            viewport.scanMinimumChunkX(),
            viewport.scanMaximumChunkX(),
            viewport.scanMinimumChunkZ(),
            viewport.scanMaximumChunkZ()
        );
        Map<BucketCoordinate, BucketAccumulator> buckets = new HashMap<>();
        for (LoadedChunkCoordinate coordinate : loaded) {
            double score = exporter.scoreChunk(world, coordinate.chunkX(), coordinate.chunkZ());
            double finiteScore = Double.isFinite(score) && score > 0D ? score : 0D;
            int bucketX = alignedBucketOrigin(coordinate.chunkX(), viewport.cellSizeChunks());
            int bucketZ = alignedBucketOrigin(coordinate.chunkZ(), viewport.cellSizeChunks());
            BucketAccumulator bucket = buckets.computeIfAbsent(
                new BucketCoordinate(bucketX, bucketZ),
                ignored -> new BucketAccumulator()
            );
            bucket.add(finiteScore);
        }
        List<HeatmapCellDto> cells = new ArrayList<>(buckets.size());
        for (Map.Entry<BucketCoordinate, BucketAccumulator> entry : buckets.entrySet()) {
            BucketCoordinate coordinate = entry.getKey();
            BucketAccumulator bucket = entry.getValue();
            cells.add(new HeatmapCellDto(
                coordinate.x(),
                coordinate.z(),
                viewport.cellSizeChunks(),
                bucket.peak(),
                bucket.average(),
                bucket.samples()
            ));
        }
        cells.sort(
            Comparator.comparingInt((HeatmapCellDto cell) -> cell.z)
                .thenComparingInt(cell -> cell.x)
        );
        return new HeatmapScan(world, viewport, capturedAtMs, List.copyOf(cells));
    }

    private int alignedBucketOrigin(int coordinate, int cellSizeChunks) {
        return Math.floorDiv(coordinate, cellSizeChunks) * cellSizeChunks;
    }

    private record BucketCoordinate(int x, int z) {}

    private static final class BucketAccumulator {
        private double peak;
        private double average;
        private int samples;

        private void add(double score) {
            samples++;
            peak = Math.max(peak, score);
            average += (score - average) / samples;
        }

        private double peak() {
            return peak;
        }

        private double average() {
            return average;
        }

        private int samples() {
            return samples;
        }
    }
}
