package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.rendering.MapRendererPipe;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.core.controller.ObserverController.LoadedChunkCoordinate;
import art.arcane.react.util.common.scheduling.Ticker;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class FeatureChunkHeatmapCoordinateOwnershipTest {
  private React previous;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getName()).thenReturn("React");
    Mockito.when(plugin.namespace()).thenReturn("react");
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void renderConsumesCrossRegionChunkCoordinatesWithoutRequestingChunkObjects() {
    RecordingHeatmap heatmap = new RecordingHeatmap();
    MapController mapController = Mockito.mock(MapController.class);
    ObserverController observer = Mockito.mock(ObserverController.class);
    World world = Mockito.mock(World.class);
    Player viewer = Mockito.mock(Player.class);
    MapView view = Mockito.mock(MapView.class);
    MapCanvas canvas = Mockito.mock(MapCanvas.class);
    UUID worldId = UUID.randomUUID();
    Location anchor = new Location(world, 8D, 64D, 8D);
    List<LoadedChunkCoordinate> coordinates = List.of(
        new LoadedChunkCoordinate(0, 0),
        new LoadedChunkCoordinate(9, 0),
        new LoadedChunkCoordinate(-9, 0)
    );

    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.getViewDistance()).thenReturn(10);
    Mockito.when(viewer.getWorld()).thenReturn(world);
    Mockito.when(viewer.getLocation()).thenReturn(anchor);
    Mockito.when(view.getWorld()).thenReturn(world);
    Mockito.when(mapController.shouldRenderForPlayer(view, viewer)).thenReturn(true);
    Mockito.when(mapController.getRendererById(RecordingHeatmap.ID)).thenReturn(heatmap);
    Mockito.when(mapController.resolveMapAnchor(view, viewer)).thenReturn(anchor);
    Mockito.when(mapController.redrawIntervalMsFor(view)).thenReturn(0L);
    Mockito.when(observer.heatmapWorld(worldId)).thenReturn(Optional.of(
        new HeatmapWorldRef(worldId, "minecraft:overworld", "world", 0, 0)
    ));
    Mockito.when(observer.loadedChunkCoordinatesInRadius(
        Mockito.eq(worldId),
        Mockito.anyInt(),
        Mockito.anyInt(),
        Mockito.anyInt()
    )).thenReturn(coordinates);
    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(MapController.class)).thenReturn(mapController);
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);

      new MapRendererPipe(heatmap, 1L).render(view, canvas, viewer);

      Assertions.assertEquals(coordinates, heatmap.scoredCoordinates);
    }
  }

  @Test
  void scanCacheDoesNotCrossFeatureInstanceReloadBoundary() {
    RecordingHeatmap first = new RecordingHeatmap();
    RecordingHeatmap reloaded = new RecordingHeatmap();
    MapController mapController = Mockito.mock(MapController.class);
    ObserverController observer = Mockito.mock(ObserverController.class);
    World world = Mockito.mock(World.class);
    Player viewer = Mockito.mock(Player.class);
    MapView view = Mockito.mock(MapView.class);
    MapCanvas canvas = Mockito.mock(MapCanvas.class);
    UUID worldId = UUID.randomUUID();
    Location anchor = new Location(world, 8D, 64D, 8D);
    LoadedChunkCoordinate coordinate = new LoadedChunkCoordinate(0, 0);

    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.getViewDistance()).thenReturn(10);
    Mockito.when(viewer.getWorld()).thenReturn(world);
    Mockito.when(viewer.getLocation()).thenReturn(anchor);
    Mockito.when(view.getWorld()).thenReturn(world);
    Mockito.when(mapController.shouldRenderForPlayer(view, viewer)).thenReturn(true);
    Mockito.when(mapController.getRendererById(RecordingHeatmap.ID)).thenReturn(first, reloaded);
    Mockito.when(mapController.resolveMapAnchor(view, viewer)).thenReturn(anchor);
    Mockito.when(mapController.redrawIntervalMsFor(view)).thenReturn(0L);
    Mockito.when(observer.heatmapWorld(worldId)).thenReturn(Optional.of(
        new HeatmapWorldRef(worldId, "minecraft:overworld", "world", 0, 0)
    ));
    Mockito.when(observer.loadedChunkCoordinatesInRadius(
        Mockito.eq(worldId),
        Mockito.anyInt(),
        Mockito.anyInt(),
        Mockito.anyInt()
    )).thenReturn(List.of(coordinate));

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(MapController.class)).thenReturn(mapController);
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);

      new MapRendererPipe(first, 1L).render(view, canvas, viewer);
      new MapRendererPipe(reloaded, 2L).render(view, canvas, viewer);

      Assertions.assertEquals(List.of(coordinate), first.scoredCoordinates);
      Assertions.assertEquals(List.of(coordinate), reloaded.scoredCoordinates);
    }
  }

  private static final class RecordingHeatmap extends FeatureChunkHeatmapBase {
    private static final String ID = "coordinate-ownership-test";
    private final List<LoadedChunkCoordinate> scoredCoordinates = new ArrayList<>();

    private RecordingHeatmap() {
      super(ID);
    }

    @Override
    protected double chunkScore(HeatmapWorldRef world, int chunkX, int chunkZ) {
      scoredCoordinates.add(new LoadedChunkCoordinate(chunkX, chunkZ));
      return 1D;
    }
  }
}
