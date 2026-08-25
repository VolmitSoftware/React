package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.rendering.MapRendererPipe;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.core.controller.ObserverController;
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

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

class FeaturePlayerImpactOverlayAnchorTest {
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
  void framedOverlayQueriesPlayersAroundTheFrameAnchorInsteadOfTheViewer() {
    FeaturePlayerImpactOverlay overlay = new FeaturePlayerImpactOverlay();
    MapController mapController = Mockito.mock(MapController.class);
    ObserverController observer = Mockito.mock(ObserverController.class);
    NearbyPlayerIndexController playerIndex = Mockito.mock(NearbyPlayerIndexController.class);
    World world = Mockito.mock(World.class);
    Player viewer = Mockito.mock(Player.class);
    MapView view = Mockito.mock(MapView.class);
    MapCanvas canvas = Mockito.mock(MapCanvas.class);
    Location viewerLocation = new Location(world, 0D, 64D, 0D);
    Location frameAnchor = Mockito.spy(new Location(world, 1_600D, 72D, 3_200D));
    UUID worldId = UUID.randomUUID();

    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.getViewDistance()).thenReturn(10);
    Mockito.when(viewer.getWorld()).thenReturn(world);
    Mockito.when(viewer.getLocation()).thenReturn(viewerLocation);
    Mockito.when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(view.getWorld()).thenReturn(world);
    Mockito.when(mapController.shouldRenderForPlayer(view, viewer)).thenReturn(true);
    Mockito.when(mapController.getRendererById(FeaturePlayerImpactOverlay.ID)).thenReturn(overlay);
    Mockito.when(mapController.resolveMapAnchor(view, viewer)).thenReturn(frameAnchor);
    Mockito.when(mapController.hasFrameAnchor(view)).thenReturn(true);
    Mockito.when(mapController.redrawIntervalMsFor(view)).thenReturn(0L);
    Mockito.when(observer.loadedChunkCoordinatesInBounds(
        Mockito.eq(world.getUID()),
        Mockito.anyInt(),
        Mockito.anyInt(),
        Mockito.anyInt(),
        Mockito.anyInt()
    )).thenReturn(List.of());
    Mockito.when(playerIndex.playerSnapshotsInColumn(
        Mockito.eq(worldId),
        Mockito.anyDouble(),
        Mockito.anyDouble(),
        Mockito.anyDouble()
    )).thenReturn(List.of());

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(MapController.class)).thenReturn(mapController);
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(playerIndex);

      new MapRendererPipe(overlay, 1L).render(view, canvas, viewer);

      double anchorX = frameAnchor.getX();
      double anchorZ = frameAnchor.getZ();
      Mockito.verify(playerIndex).playerSnapshotsInColumn(
          Mockito.eq(worldId),
          Mockito.eq(anchorX),
          Mockito.eq(anchorZ),
          Mockito.doubleThat(radius -> radius > 0D)
      );
      Mockito.verify(frameAnchor, Mockito.never()).getChunk();
      Mockito.verify(world, Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt());
    }
  }

  @Test
  void projectionThreadLocalDoesNotRetainBukkitWorldHandles() throws ReflectiveOperationException {
    Field projectionField = FeatureChunkHeatmapBase.class.getDeclaredField("PROJECTION");
    projectionField.setAccessible(true);
    ThreadLocal<?> projectionState = (ThreadLocal<?>) projectionField.get(null);
    Object projection = projectionState.get();

    try {
      for (Field field : projection.getClass().getDeclaredFields()) {
        Assertions.assertFalse(
            World.class.isAssignableFrom(field.getType()),
            () -> "Projection retains Bukkit world through " + field.getName()
        );
      }
    } finally {
      projectionState.remove();
    }
  }
}
