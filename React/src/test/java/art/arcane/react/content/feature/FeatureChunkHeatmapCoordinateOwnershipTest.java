package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.rendering.ReactRenderContext;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.platform.MapRenderTarget;
import org.bukkit.Bukkit;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.core.controller.ObserverController.LoadedChunkCoordinate;
import art.arcane.react.util.common.scheduling.Ticker;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
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

  private static void render(ReactRenderer renderer, MapRenderTarget target) {
    ReactRenderContext context = ReactRenderContext.builder()
        .target(target)
        .width(ReactRenderer.CANVAS_SIZE)
        .height(ReactRenderer.CANVAS_SIZE)
        .build();
    ReactRenderContext.push(context);
    try {
      renderer.render();
    } finally {
      ReactRenderContext.pop();
    }
  }

  @Test
  void renderConsumesCrossRegionChunkCoordinatesWithoutRequestingChunkObjects() {
    RecordingHeatmap heatmap = new RecordingHeatmap();
    MapController mapController = Mockito.mock(MapController.class);
    ObserverController observer = Mockito.mock(ObserverController.class);
    World world = Mockito.mock(World.class);
    Player viewer = Mockito.mock(Player.class);
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
    Mockito.when(mapController.resolveMapAnchor(Mockito.any(MapRenderTarget.class))).thenReturn(anchor);
    Mockito.when(observer.heatmapWorld(worldId)).thenReturn(Optional.of(
        new HeatmapWorldRef(
            worldId,
            "minecraft:overworld",
            "world",
            0,
            0,
            0D,
            0D,
            60_000_000D
        )
    ));
    Mockito.when(observer.loadedChunkCoordinatesInBounds(
        Mockito.eq(worldId),
        Mockito.anyInt(),
        Mockito.anyInt(),
        Mockito.anyInt(),
        Mockito.anyInt()
    )).thenReturn(coordinates);
    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getPlayer(Mockito.any(UUID.class))).thenReturn(viewer);
      bukkit.when(() -> Bukkit.getWorld(Mockito.any(UUID.class))).thenReturn(world);
      react.when(() -> React.controller(MapController.class)).thenReturn(mapController);
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);

      render(heatmap, new MapRenderTarget(7, world.getUID(), UUID.randomUUID()));

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
    UUID worldId = UUID.randomUUID();
    Location anchor = new Location(world, 8D, 64D, 8D);
    LoadedChunkCoordinate coordinate = new LoadedChunkCoordinate(0, 0);

    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.getViewDistance()).thenReturn(10);
    Mockito.when(viewer.getWorld()).thenReturn(world);
    Mockito.when(viewer.getLocation()).thenReturn(anchor);
    Mockito.when(mapController.resolveMapAnchor(Mockito.any(MapRenderTarget.class))).thenReturn(anchor);
    Mockito.when(observer.heatmapWorld(worldId)).thenReturn(Optional.of(
        new HeatmapWorldRef(
            worldId,
            "minecraft:overworld",
            "world",
            0,
            0,
            0D,
            0D,
            60_000_000D
        )
    ));
    Mockito.when(observer.loadedChunkCoordinatesInBounds(
        Mockito.eq(worldId),
        Mockito.anyInt(),
        Mockito.anyInt(),
        Mockito.anyInt(),
        Mockito.anyInt()
    )).thenReturn(List.of(coordinate));

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getPlayer(Mockito.any(UUID.class))).thenReturn(viewer);
      bukkit.when(() -> Bukkit.getWorld(Mockito.any(UUID.class))).thenReturn(world);
      react.when(() -> React.controller(MapController.class)).thenReturn(mapController);
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);

      render(first, new MapRenderTarget(7, world.getUID(), UUID.randomUUID()));
      render(reloaded, new MapRenderTarget(7, world.getUID(), UUID.randomUUID()));

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
