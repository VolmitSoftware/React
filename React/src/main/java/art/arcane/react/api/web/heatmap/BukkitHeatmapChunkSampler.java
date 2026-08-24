package art.arcane.react.api.web.heatmap;

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class BukkitHeatmapChunkSampler implements HeatmapChunkSampler {
    private final Supplier<ObserverController> observerSupplier;

    public BukkitHeatmapChunkSampler() {
        this(() -> React.controller(ObserverController.class));
    }

    public BukkitHeatmapChunkSampler(Supplier<ObserverController> observerSupplier) {
        this.observerSupplier = observerSupplier;
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
        List<ObserverController.LoadedChunkCoordinate> loaded = observer.loadedChunkCoordinatesInRadius(
            world.worldId(),
            centerChunkX,
            centerChunkZ,
            radius
        );
        List<HeatmapCellDto> cells = new ArrayList<>(loaded.size());
        for (ObserverController.LoadedChunkCoordinate coordinate : loaded) {
            double score = exporter.scoreChunk(world, coordinate.chunkX(), coordinate.chunkZ());
            if (score > 0D) {
                cells.add(new HeatmapCellDto(coordinate.chunkX(), coordinate.chunkZ(), score));
            }
        }
        return new HeatmapScan(world.worldKey(), centerChunkX, centerChunkZ, cells);
    }
}
