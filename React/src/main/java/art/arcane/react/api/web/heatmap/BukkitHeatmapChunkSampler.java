package art.arcane.react.api.web.heatmap;

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import java.util.Optional;
import java.util.function.Supplier;

public class BukkitHeatmapChunkSampler implements HeatmapChunkSampler {
    private final Supplier<ObserverController> observerSupplier;
    private final HeatmapGridScanner scanner = new HeatmapGridScanner();

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
        return scanner.scan(exporter, resolved.get(), centerX, centerZ, radius, observer::loadedChunkCoordinatesInBounds);
    }
}
