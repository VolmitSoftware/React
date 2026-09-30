package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.rendering.MapRendererPipe;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.core.integration.IntegrationCapabilitySupport;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.react.util.project.registry.Registry;
import art.arcane.react.api.sampler.Sampler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

class MapControllerViewLifecycleTest {
  private static final String RENDERER_ID = "view-lifecycle-test";

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
  void dashboardViewsAreLockedSoVanillaSkipsTerrainScans() {
    MapController controller = new MapController();
    World world = Mockito.mock(World.class);
    ViewFixture fresh = new ViewFixture(11, world);
    ViewFixture existing = new ViewFixture(12, world);
    ReactRenderer renderer = renderer();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.createMap(world)).thenReturn(fresh.view);
      controller.start();

      controller.createView(world, renderer);
      controller.updateMapView(existing.view, renderer);

      Mockito.verify(fresh.view).setLocked(true);
      Mockito.verify(existing.view).setLocked(true);
    } finally {
      controller.stop();
    }
  }

  @Test
  void inventoryMapCarriedIntoAnotherWorldKeepsItsMapId() {
    MapController controller = new MapController();
    World home = Mockito.mock(World.class);
    World destination = Mockito.mock(World.class);
    ViewFixture view = new ViewFixture(21, home);
    MapItemFixture item = new MapItemFixture(view.view);
    Player player = Mockito.mock(Player.class);
    PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
    Mockito.when(player.getInventory()).thenReturn(inventory);
    Mockito.when(inventory.getContents()).thenReturn(new ItemStack[]{item.item});
    ReactRenderer renderer = renderer();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(home, destination));
      controller.start();
      controller.registerRenderer(renderer);

      controller.updateMapViews(player, destination, false);

      bukkit.verify(() -> Bukkit.createMap(Mockito.any(World.class)), Mockito.never());
      Assertions.assertSame(view.view, item.mapView.get());
      Assertions.assertSame(renderer, view.onlyPipe().getRenderer());
    } finally {
      controller.stop();
    }
  }

  @Test
  void frameRemintDetachesThePipeFromTheAbandonedView() throws ReflectiveOperationException {
    MapController controller = new MapController();
    World home = Mockito.mock(World.class);
    World frameWorld = Mockito.mock(World.class);
    Mockito.when(frameWorld.getUID()).thenReturn(UUID.randomUUID());
    ViewFixture abandoned = new ViewFixture(31, home);
    ViewFixture reminted = new ViewFixture(32, frameWorld);
    MapItemFixture item = new MapItemFixture(abandoned.view);
    ItemFrame frame = Mockito.mock(ItemFrame.class);
    Mockito.when(frame.getItem()).thenReturn(item.item);
    Mockito.when(frame.getWorld()).thenReturn(frameWorld);
    Mockito.when(frame.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(frame.getLocation()).thenReturn(new Location(frameWorld, 1D, 64D, 1D));
    ReactRenderer renderer = renderer();
    Method refresh = MapController.class.getDeclaredMethod("refreshItemFrame", ItemFrame.class, boolean.class, Supplier.class);
    refresh.setAccessible(true);
    Supplier<Object> noViewers = () -> null;

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.createMap(frameWorld)).thenReturn(reminted.view);
      controller.start();
      controller.registerRenderer(renderer);
      controller.updateMapView(abandoned.view, renderer);
      MapRendererPipe abandonedPipe = abandoned.onlyPipe();

      refresh.invoke(controller, frame, false, noViewers);

      Assertions.assertSame(reminted.view, item.mapView.get());
      Assertions.assertSame(renderer, reminted.onlyPipe().getRenderer());
      Assertions.assertTrue(abandoned.renderers.isEmpty());
      Assertions.assertFalse(abandonedPipe.isActive());
    } finally {
      controller.stop();
    }
  }

  @Test
  void rendererSelectionRemintDetachesThePipeFromAViewWithoutAWorld() {
    MapController controller = new MapController();
    World home = Mockito.mock(World.class);
    ViewFixture abandoned = new ViewFixture(41, home);
    ViewFixture reminted = new ViewFixture(42, home);
    MapItemFixture item = new MapItemFixture(abandoned.view);
    ReactRenderer renderer = renderer();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(home));
      bukkit.when(() -> Bukkit.createMap(home)).thenReturn(reminted.view);
      controller.start();
      controller.registerRenderer(renderer);
      controller.updateMapView(abandoned.view, renderer);
      MapRendererPipe abandonedPipe = abandoned.onlyPipe();
      Mockito.when(abandoned.view.getWorld()).thenReturn(null);

      controller.setRenderer(item.item, renderer);

      Assertions.assertSame(reminted.view, item.mapView.get());
      Assertions.assertSame(renderer, reminted.onlyPipe().getRenderer());
      Assertions.assertTrue(abandoned.renderers.isEmpty());
      Assertions.assertFalse(abandonedPipe.isActive());
    } finally {
      controller.stop();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void integrationRenderersRescanOnlyWhenCapabilityPresenceChanges() throws ReflectiveOperationException {
    MapController controller = new MapController();
    SampleController sampleController = Mockito.mock(SampleController.class);
    Registry<Sampler> samplers = Mockito.mock(Registry.class);
    Mockito.when(samplers.all()).thenReturn(List.of());
    Mockito.when(sampleController.getSamplers()).thenReturn(samplers);
    Map<String, Boolean> present = new HashMap<>();
    present.put("iris", true);
    Method sync = MapController.class.getDeclaredMethod("syncIntegrationRenderers");
    sync.setAccessible(true);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<IntegrationCapabilitySupport> capabilities = Mockito.mockStatic(IntegrationCapabilitySupport.class)) {
      react.when(() -> React.controller(SampleController.class)).thenReturn(sampleController);
      capabilities.when(() -> IntegrationCapabilitySupport.isCapabilityPresent(Mockito.any(), Mockito.anyString()))
          .thenAnswer(invocation -> present.getOrDefault(invocation.getArgument(1, String.class), false));
      controller.start();

      sync.invoke(controller);
      sync.invoke(controller);
      react.verify(() -> React.controller(SampleController.class), Mockito.times(1));

      present.put("adapt", true);
      sync.invoke(controller);
      react.verify(() -> React.controller(SampleController.class), Mockito.times(3));
    } finally {
      controller.stop();
    }
  }

  private static ReactRenderer renderer() {
    ReactRenderer renderer = Mockito.mock(ReactRenderer.class);
    Mockito.when(renderer.getId()).thenReturn(RENDERER_ID);
    return renderer;
  }

  private static final class ViewFixture {
    private final MapView view = Mockito.mock(MapView.class);
    private final List<MapRenderer> renderers = new ArrayList<>();

    private ViewFixture(int id, World world) {
      Mockito.when(view.getId()).thenReturn(id);
      Mockito.when(view.getWorld()).thenReturn(world);
      Mockito.when(view.getRenderers()).thenAnswer(invocation -> new ArrayList<>(renderers));
      Mockito.doAnswer(invocation -> {
        renderers.add(invocation.getArgument(0));
        return null;
      }).when(view).addRenderer(Mockito.any(MapRenderer.class));
      Mockito.when(view.removeRenderer(Mockito.any(MapRenderer.class))).thenAnswer(invocation ->
          renderers.remove(invocation.getArgument(0))
      );
    }

    private MapRendererPipe onlyPipe() {
      Assertions.assertEquals(1, renderers.size());
      return Assertions.assertInstanceOf(MapRendererPipe.class, renderers.get(0));
    }
  }

  private static final class MapItemFixture {
    private final ItemStack item = Mockito.mock(ItemStack.class);
    private final MapMeta meta = Mockito.mock(MapMeta.class);
    private final PersistentDataContainer container = Mockito.mock(PersistentDataContainer.class);
    private final Map<NamespacedKey, Object> values = new HashMap<>();
    private final AtomicReference<MapView> mapView = new AtomicReference<>();

    @SuppressWarnings("unchecked")
    private MapItemFixture(MapView view) {
      mapView.set(view);
      values.put(new NamespacedKey("react", "react"), (byte) 1);
      values.put(new NamespacedKey("react", "react-renderer"), RENDERER_ID);
      Mockito.when(item.getType()).thenReturn(Material.FILLED_MAP);
      Mockito.when(item.getItemMeta()).thenReturn(meta);
      Mockito.when(meta.getPersistentDataContainer()).thenReturn(container);
      Mockito.when(meta.getMapView()).thenAnswer(invocation -> mapView.get());
      Mockito.doAnswer(invocation -> {
        mapView.set(invocation.getArgument(0));
        return null;
      }).when(meta).setMapView(Mockito.any(MapView.class));
      Mockito.when(container.get(Mockito.any(NamespacedKey.class), Mockito.any(PersistentDataType.class)))
          .thenAnswer(invocation -> values.get(invocation.getArgument(0, NamespacedKey.class)));
      Mockito.when(container.getOrDefault(Mockito.any(NamespacedKey.class), Mockito.any(PersistentDataType.class), Mockito.any()))
          .thenAnswer(invocation -> values.getOrDefault(invocation.getArgument(0, NamespacedKey.class), invocation.getArgument(2)));
      Mockito.doAnswer(invocation -> {
        values.put(invocation.getArgument(0, NamespacedKey.class), invocation.getArgument(2));
        return null;
      }).when(container).set(Mockito.any(NamespacedKey.class), Mockito.any(PersistentDataType.class), Mockito.any());
    }
  }
}
