package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.map.MapView;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

class MapControllerFoliaBoundaryTest {
  private React previous;
  private React plugin;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getName()).thenReturn("React");
    Mockito.when(plugin.namespace()).thenReturn("react");
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    Mockito.when(plugin.isEnabled()).thenReturn(true);
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void foliaFramePushResolvesNearbyCandidatesGloballyAndReadsPlayersOnlyOnTheirOwner()
      throws ReflectiveOperationException {
    MapController controller = startedController(false);
    UUID worldId = UUID.randomUUID();
    UUID nearbyId = UUID.randomUUID();
    Set<UUID> farIds = new HashSet<>();
    for (int index = 0; index < 999; index++) {
      farIds.add(UUID.randomUUID());
    }

    World world = world(worldId);
    ItemFrame frame = frame(world, new Location(world, 0D, 64D, 0D));
    MapView view = Mockito.mock(MapView.class);
    Player player = player(world, nearbyId);
    NearbyPlayerIndexController playerIndex = playerIndex(worldId, nearbyId, farIds);
    AtomicReference<Runnable> globalTask = new AtomicReference<>();
    AtomicReference<Runnable> playerTask = new AtomicReference<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<FoliaScheduler> folia = Mockito.mockStatic(FoliaScheduler.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(playerIndex);
      bukkit.when(() -> Bukkit.getPlayer(nearbyId)).thenReturn(player);
      folia.when(() -> FoliaScheduler.runGlobal(Mockito.eq(plugin), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            globalTask.set(invocation.getArgument(1));
            return true;
          });
      scheduling.when(() -> J.runEntity(Mockito.eq(player), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            playerTask.set(invocation.getArgument(1));
            return true;
          });
      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);

      invokeRegionLocalPush(controller, frame, view, 41);

      Assertions.assertNotNull(globalTask.get());
      Mockito.verifyNoInteractions(player);
      globalTask.get().run();

      Assertions.assertNotNull(playerTask.get());
      Mockito.verifyNoInteractions(player);
      bukkit.verify(() -> Bukkit.getPlayer(Mockito.any(UUID.class)), Mockito.times(1));
      playerTask.get().run();

      Mockito.verify(player).sendMap(view);
      Mockito.verify(frame, Mockito.never()).getNearbyEntities(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble());
      Mockito.verify(world, Mockito.never()).getNearbyEntities(
          Mockito.any(Location.class),
          Mockito.anyDouble(),
          Mockito.anyDouble(),
          Mockito.anyDouble()
      );
    } finally {
      controller.stop();
    }
  }

  @Test
  void foliaFramePushFailsClosedBeforeLineOfSightCrossesARegionBoundary()
      throws ReflectiveOperationException {
    MapController controller = startedController(true);
    UUID worldId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();
    World world = world(worldId);
    ItemFrame frame = frame(world, new Location(world, 0D, 64D, 0D));
    MapView view = Mockito.mock(MapView.class);
    Player player = player(world, playerId);
    NearbyPlayerIndexController playerIndex = playerIndex(worldId, playerId, Set.of());
    AtomicReference<Runnable> globalTask = new AtomicReference<>();
    AtomicReference<Runnable> playerTask = new AtomicReference<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<FoliaScheduler> folia = Mockito.mockStatic(FoliaScheduler.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(playerIndex);
      bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
      folia.when(() -> FoliaScheduler.runGlobal(Mockito.eq(plugin), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            globalTask.set(invocation.getArgument(1));
            return true;
          });
      scheduling.when(() -> J.runEntity(Mockito.eq(player), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            playerTask.set(invocation.getArgument(1));
            return true;
          });
      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Location.class))).thenReturn(false);

      invokeRegionLocalPush(controller, frame, view, 42);
      Assertions.assertNotNull(globalTask.get());
      globalTask.get().run();
      Assertions.assertNotNull(playerTask.get());
      playerTask.get().run();

      Mockito.verify(player, Mockito.never()).hasLineOfSight(Mockito.any(Location.class));
      Mockito.verify(player, Mockito.never()).hasLineOfSight(Mockito.any(Entity.class));
      Mockito.verify(player, Mockito.never()).sendMap(Mockito.any(MapView.class));
    } finally {
      controller.stop();
    }
  }

  @Test
  void stoppedMapRuntimeRejectsQueuedGlobalFramePush() throws ReflectiveOperationException {
    MapController controller = startedController(false);
    UUID worldId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();
    World world = world(worldId);
    ItemFrame frame = frame(world, new Location(world, 0D, 64D, 0D));
    MapView view = Mockito.mock(MapView.class);
    Player player = player(world, playerId);
    NearbyPlayerIndexController playerIndex = playerIndex(worldId, playerId, Set.of());
    AtomicReference<Runnable> globalTask = new AtomicReference<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<FoliaScheduler> folia = Mockito.mockStatic(FoliaScheduler.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(playerIndex);
      bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
      folia.when(() -> FoliaScheduler.runGlobal(Mockito.eq(plugin), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            globalTask.set(invocation.getArgument(1));
            return true;
          });

      invokeRegionLocalPush(controller, frame, view, 43);
      Assertions.assertNotNull(globalTask.get());
      controller.stop();
      globalTask.get().run();

      scheduling.verify(() -> J.runEntity(Mockito.any(Player.class), Mockito.any(Runnable.class)), Mockito.never());
      Mockito.verifyNoInteractions(player);
    }
  }

  @Test
  void trackedFrameMaintenanceIsBatchBoundedSingleFlightAndRetryableAfterRetirement()
      throws ReflectiveOperationException {
    MapController controller = startedController(false);
    controller.setFrameMapPushBatchSize(2);
    UUID worldId = UUID.randomUUID();
    World world = world(worldId);
    Map<UUID, Object> trackedFrames = activeFrameMaps(controller);
    ConcurrentLinkedQueue<Object> queue = activeFrameMapQueue(controller);
    Map<UUID, Entity> entities = new HashMap<>();
    Object first = null;

    for (int index = 0; index < 5; index++) {
      UUID frameId = UUID.randomUUID();
      Object tracked = activeFrame(frameId, worldId, index);
      if (first == null) {
        first = tracked;
      }
      trackedFrames.put(frameId, tracked);
      queue.offer(tracked);
      entities.put(frameId, Mockito.mock(Entity.class));
    }

    AtomicReference<Runnable> retirement = new AtomicReference<>();
    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      bukkit.when(() -> Bukkit.getEntity(Mockito.any(UUID.class)))
          .thenAnswer(invocation -> entities.get(invocation.getArgument(0)));
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenReturn(false);
      scheduling.when(() -> J.runEntity(
              Mockito.any(Entity.class),
              Mockito.any(Runnable.class),
              Mockito.eq(0),
              Mockito.any(Runnable.class)
          ))
          .thenAnswer(invocation -> {
            retirement.compareAndSet(null, invocation.getArgument(3));
            return true;
          });

      invokeTrackedFramePush(controller);
      bukkit.verify(() -> Bukkit.getEntity(Mockito.any(UUID.class)), Mockito.times(2));
      scheduling.verify(() -> J.runEntity(
          Mockito.any(Entity.class),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      ), Mockito.times(2));

      Assertions.assertNotNull(first);
      setLong(first, "nextPushDispatchMs", 0L);
      invokeFrameMaintenance(controller, first);
      scheduling.verify(() -> J.runEntity(
          Mockito.any(Entity.class),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      ), Mockito.times(2));

      Assertions.assertNotNull(retirement.get());
      retirement.get().run();
      Assertions.assertFalse(atomicBoolean(first, "pushDispatchInFlight").get());
      Assertions.assertTrue(longField(first, "nextPushDispatchMs") <= System.currentTimeMillis());
    } finally {
      controller.stop();
    }
  }

  private MapController startedController(boolean requireLineOfSight) throws ReflectiveOperationException {
    MapController controller = new MapController();
    controller.start();
    controller.setFrameMapRequireLineOfSight(requireLineOfSight);
    Map<Object, Long> pushState = pushState();
    Field pushStateField = MapController.class.getDeclaredField("frameMapPushMsByViewerKey");
    pushStateField.setAccessible(true);
    pushStateField.set(controller, pushState);
    return controller;
  }

  @SuppressWarnings("unchecked")
  private Map<UUID, Object> activeFrameMaps(MapController controller) throws ReflectiveOperationException {
    Field field = MapController.class.getDeclaredField("activeFrameMaps");
    field.setAccessible(true);
    return (Map<UUID, Object>) field.get(controller);
  }

  @SuppressWarnings("unchecked")
  private ConcurrentLinkedQueue<Object> activeFrameMapQueue(MapController controller)
      throws ReflectiveOperationException {
    Field field = MapController.class.getDeclaredField("activeFrameMapQueue");
    field.setAccessible(true);
    return (ConcurrentLinkedQueue<Object>) field.get(controller);
  }

  private Object activeFrame(UUID frameId, UUID worldId, int mapId) throws ReflectiveOperationException {
    Class<?> trackedType = Class.forName(MapController.class.getName() + "$ActiveFrameMap");
    Constructor<?> constructor = trackedType.getDeclaredConstructor(
        UUID.class,
        UUID.class,
        int.class,
        Location.class,
        long.class
    );
    constructor.setAccessible(true);
    return constructor.newInstance(frameId, worldId, mapId, null, 0L);
  }

  private void invokeTrackedFramePush(MapController controller) throws ReflectiveOperationException {
    Method method = MapController.class.getDeclaredMethod("pushTrackedFrameMaps", Supplier.class);
    method.setAccessible(true);
    method.invoke(controller, (Supplier<Object>) () -> null);
  }

  private void invokeFrameMaintenance(MapController controller, Object tracked)
      throws ReflectiveOperationException {
    Method method = MapController.class.getDeclaredMethod(
        "scheduleTrackedFrameMaintenance",
        tracked.getClass(),
        Supplier.class,
        long.class
    );
    method.setAccessible(true);
    method.invoke(controller, tracked, (Supplier<Object>) () -> null, System.currentTimeMillis());
  }

  private AtomicBoolean atomicBoolean(Object target, String name) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return (AtomicBoolean) field.get(target);
  }

  private long longField(Object target, String name) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.getLong(target);
  }

  private void setLong(Object target, String name, long value) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.setLong(target, value);
  }

  @SuppressWarnings("unchecked")
  private Map<Object, Long> pushState() {
    Map<Object, Long> pushState = Mockito.mock(Map.class);
    Mockito.when(pushState.getOrDefault(Mockito.any(), Mockito.anyLong())).thenReturn(0L);
    return pushState;
  }

  private NearbyPlayerIndexController playerIndex(
      UUID worldId,
      UUID nearbyId,
      Set<UUID> farIds
  ) {
    NearbyPlayerIndexController playerIndex = Mockito.mock(NearbyPlayerIndexController.class);
    Map<Long, Set<UUID>> worldBuckets = new HashMap<>();
    worldBuckets.put(chunkKey(0, 0), Set.of(nearbyId));
    if (!farIds.isEmpty()) {
      worldBuckets.put(chunkKey(100, 100), farIds);
    }
    Mockito.when(playerIndex.getPlayersByWorldChunk()).thenReturn(Map.of(worldId, worldBuckets));
    return playerIndex;
  }

  private Player player(World world, UUID playerId) {
    Player player = Mockito.mock(Player.class);
    PlayerInventory inventory = Mockito.mock(PlayerInventory.class);
    Location location = new Location(world, 1D, 64D, 0D);
    Location eye = new Location(world, 1D, 64D, 0D);
    eye.setDirection(new Vector(-1D, 0D, 0D));
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(player.getWorld()).thenReturn(world);
    Mockito.when(player.getLocation()).thenReturn(location);
    Mockito.when(player.getEyeLocation()).thenReturn(eye);
    Mockito.when(player.getUniqueId()).thenReturn(playerId);
    Mockito.when(player.getInventory()).thenReturn(inventory);
    return player;
  }

  private ItemFrame frame(World world, Location location) {
    ItemFrame frame = Mockito.mock(ItemFrame.class);
    Mockito.when(frame.getWorld()).thenReturn(world);
    Mockito.when(frame.getLocation()).thenReturn(location);
    return frame;
  }

  private World world(UUID worldId) {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(worldId);
    return world;
  }

  private void invokeRegionLocalPush(MapController controller, ItemFrame frame, MapView view, int mapId)
      throws ReflectiveOperationException {
    Method method = MapController.class.getDeclaredMethod(
        "pushMapToNearbyPlayersRegionLocal",
        ItemFrame.class,
        MapView.class,
        int.class
    );
    method.setAccessible(true);
    method.invoke(controller, frame, view, mapId);
  }

  private long chunkKey(int chunkX, int chunkZ) {
    return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
  }
}
