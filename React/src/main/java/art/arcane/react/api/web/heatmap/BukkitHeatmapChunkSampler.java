package art.arcane.react.api.web.heatmap;

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class BukkitHeatmapChunkSampler implements HeatmapChunkSampler {
    private final Supplier<ObserverController> observerSupplier;
    private final HeatmapViewportPlanner viewportPlanner;

    public BukkitHeatmapChunkSampler() {
        this(() -> React.controller(ObserverController.class));
    }

    public BukkitHeatmapChunkSampler(Supplier<ObserverController> observerSupplier) {
        this.observerSupplier = observerSupplier;
        viewportPlanner = new HeatmapViewportPlanner();
    }

    @Override
    public HeatmapScan scan(ChunkGridExporter exporter, String requestedWorld, Integer centerX, Integer centerZ, int radius) {
        ObserverController observer = observerSupplier.get();
        if (observer == null) {
            return null;
        }
        Optional<HeatmapWorldRef> resolved = observer.heatmapWorld(requestedWorld);
        if (resolved.isEmpty()) {
            return null;
        }
        HeatmapWorldRef world = resolved.get();
        int centerChunkX = centerX != null ? centerX : world.spawnChunkX();
        int centerChunkZ = centerZ != null ? centerZ : world.spawnChunkZ();
        HeatmapViewportPlanner.HeatmapViewportPlan viewport = viewportPlanner.plan(
            centerChunkX,
            centerChunkZ,
            radius
        );
        long capturedAtMs = System.currentTimeMillis();
        List<ObserverController.LoadedChunkCoordinate> loaded = observer.loadedChunkCoordinatesInBounds(
            world.worldId(),
            viewport.scanMinimumChunkX(),
            viewport.scanMaximumChunkX(),
            viewport.scanMinimumChunkZ(),
            viewport.scanMaximumChunkZ()
        );
        Map<BucketCoordinate, BucketAccumulator> buckets = new HashMap<>();
        for (ObserverController.LoadedChunkCoordinate coordinate : loaded) {
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
