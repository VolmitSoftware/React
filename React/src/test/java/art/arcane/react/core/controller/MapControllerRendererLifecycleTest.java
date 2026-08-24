package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.rendering.MapRendererPipe;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.content.feature.FeatureIrisBiomeChunkSharePieMap;
import art.arcane.react.util.common.scheduling.Ticker;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

class MapControllerRendererLifecycleTest {
  private React previous;
  private React plugin;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    plugin = Mockito.mock(React.class);
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
  void stopDetachesOwnedPipeAndReleasesRenderer() {
    MapController controller = new MapController();
    MapViewFixture fixture = new MapViewFixture();
    ReactRenderer renderer = renderer("lifecycle-test");
    controller.start();

    controller.updateMapView(fixture.view(), renderer);
    MapRendererPipe pipe = fixture.onlyPipe();
    Assertions.assertTrue(pipe.isActive());
    Assertions.assertSame(renderer, pipe.getRenderer());

    controller.stop();

    Assertions.assertFalse(pipe.isActive());
    Assertions.assertNull(pipe.getRenderer());
    Assertions.assertTrue(fixture.renderers().isEmpty());
    Mockito.clearInvocations(renderer);
    pipe.render(fixture.view(), Mockito.mock(MapCanvas.class), Mockito.mock(Player.class));
    Mockito.verifyNoInteractions(renderer);
  }

  @Test
  void startRebindingDetachesThePreviousRuntimePipe() {
    MapController controller = new MapController();
    MapViewFixture fixture = new MapViewFixture();
    ReactRenderer renderer = renderer("lifecycle-test");
    controller.start();
    controller.updateMapView(fixture.view(), renderer);
    MapRendererPipe previousPipe = fixture.onlyPipe();

    controller.start();

    Assertions.assertFalse(previousPipe.isActive());
    Assertions.assertNull(previousPipe.getRenderer());
    Assertions.assertTrue(fixture.renderers().isEmpty());

    controller.updateMapView(fixture.view(), renderer);
    MapRendererPipe currentPipe = fixture.onlyPipe();
    Assertions.assertNotEquals(previousPipe.getOwnerId(), currentPipe.getOwnerId());
    controller.stop();
  }

  @Test
  void oldControllerStopCannotRemoveReloadedControllerPipe() {
    MapController oldController = new MapController();
    MapController newController = new MapController();
    MapViewFixture fixture = new MapViewFixture();
    ReactRenderer renderer = renderer("lifecycle-test");
    oldController.start();
    oldController.updateMapView(fixture.view(), renderer);
    MapRendererPipe oldPipe = fixture.onlyPipe();

    newController.start();
    newController.updateMapView(fixture.view(), renderer);
    MapRendererPipe newPipe = fixture.onlyPipe();
    oldController.stop();

    Assertions.assertFalse(oldPipe.isActive());
    Assertions.assertTrue(newPipe.isActive());
    Assertions.assertSame(newPipe, fixture.onlyPipe());
    newController.stop();
  }

  @Test
  void sameRendererIdFromAnOldRuntimeIsNotAccepted() throws ReflectiveOperationException {
    MapController oldController = new MapController();
    MapController newController = new MapController();
    MapViewFixture fixture = new MapViewFixture();
    ReactRenderer renderer = renderer("lifecycle-test");
    oldController.start();
    oldController.updateMapView(fixture.view(), renderer);
    newController.start();

    Method expectedRenderer = MapController.class.getDeclaredMethod(
        "hasExpectedMapRenderer",
        MapView.class,
        ReactRenderer.class
    );
    expectedRenderer.setAccessible(true);
    boolean accepted = (boolean) expectedRenderer.invoke(newController, fixture.view(), renderer);

    Assertions.assertFalse(accepted);
    oldController.stop();
    newController.stop();
  }

  @Test
  void boundedIrisBiomeRendererIsSelectable() {
    MapController controller = new MapController();
    FeatureIrisBiomeChunkSharePieMap renderer = new FeatureIrisBiomeChunkSharePieMap();
    controller.start();

    controller.registerRenderer(renderer);

    Assertions.assertSame(renderer, controller.getRendererById(FeatureIrisBiomeChunkSharePieMap.ID));
    controller.stop();
  }

  private ReactRenderer renderer(String id) {
    ReactRenderer renderer = Mockito.mock(ReactRenderer.class);
    Mockito.when(renderer.getId()).thenReturn(id);
    return renderer;
  }

  private static final class MapViewFixture {
    private final MapView view;
    private final List<MapRenderer> renderers;

    private MapViewFixture() {
      view = Mockito.mock(MapView.class);
      renderers = new ArrayList<>();
      Mockito.when(view.getRenderers()).thenAnswer(invocation -> new ArrayList<>(renderers));
      Mockito.doAnswer(invocation -> {
        renderers.add(invocation.getArgument(0));
        return null;
      }).when(view).addRenderer(Mockito.any(MapRenderer.class));
      Mockito.when(view.removeRenderer(Mockito.any(MapRenderer.class))).thenAnswer(invocation ->
          renderers.remove(invocation.getArgument(0))
      );
    }

    private MapView view() {
      return view;
    }

    private List<MapRenderer> renderers() {
      return renderers;
    }

    private MapRendererPipe onlyPipe() {
      Assertions.assertEquals(1, renderers.size());
      return Assertions.assertInstanceOf(MapRendererPipe.class, renderers.get(0));
    }
  }
}
