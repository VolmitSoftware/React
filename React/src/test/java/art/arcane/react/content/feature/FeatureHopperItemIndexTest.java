package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.testutil.Fakes;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FeatureHopperItemIndexTest {

  @Test
  void removedItemsLeaveTheIndex() throws Exception {
    FeatureHopperItemIndex feature = new FeatureHopperItemIndex();
    feature.onActivate();
    World world = Fakes.world("index-remove");
    Item burned = item(new UUID(1L, 1L), new Location(world, 0.5D, 64D, 0.5D));
    Item kept = item(new UUID(1L, 2L), new Location(world, 8.5D, 64D, 8.5D));
    feature.on(new ItemSpawnEvent(burned));
    feature.on(new ItemSpawnEvent(kept));
    Zombie zombie = Mockito.mock(Zombie.class);
    Mockito.when(zombie.getUniqueId()).thenReturn(new UUID(1L, 2L));

    feature.on(new EntityRemoveEvent(burned, EntityRemoveEvent.Cause.ENTER_BLOCK));
    feature.on(new EntityRemoveEvent(zombie, EntityRemoveEvent.Cause.DEATH));

    assertEquals(1, feature.getItemIndex().size());
    assertEquals(1, trackedItems(feature).size());
    feature.onDeactivate();
  }

  @Test
  void thousandMergedItemsLeaveTrackingBounded() throws Exception {
    FeatureHopperItemIndex feature = new FeatureHopperItemIndex();
    feature.onActivate();
    World world = Fakes.world("index-merge");
    Location farmDrop = new Location(world, 4.5D, 64D, 4.5D);
    List<Item> items = new ArrayList<>(1000);
    for (int index = 0; index < 1000; index++) {
      Item item = item(new UUID(2L, index), farmDrop);
      items.add(item);
      feature.on(new ItemSpawnEvent(item));
    }
    assertEquals(1000, trackedItems(feature).size());

    for (int index = 1; index < items.size(); index++) {
      feature.on(new EntityRemoveEvent(items.get(index), EntityRemoveEvent.Cause.MERGE));
    }

    assertEquals(1, feature.getItemIndex().size());
    assertEquals(1, trackedItems(feature).size());
    feature.onDeactivate();
  }

  @Test
  void trackedItemsDoNotPinCollectedItems() throws Exception {
    FeatureHopperItemIndex feature = new FeatureHopperItemIndex();
    feature.onActivate();
    World world = Fakes.world("index-weak");
    WeakReference<Item> probe = trackUnreferencedItem(feature, world);

    for (int attempt = 0; attempt < 50 && probe.get() != null; attempt++) {
      System.gc();
      Thread.sleep(20L);
    }
    assertNull(probe.get());

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      runSyncJobsImmediately(scheduling);
      feature.onTick();
    }

    assertEquals(0, trackedItems(feature).size());
    assertEquals(0, feature.getItemIndex().size());
    feature.onDeactivate();
  }

  @Test
  void repeatedChunkReconcileFailuresReportOncePerWindow() {
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Chunk chunk = Mockito.mock(Chunk.class);
    Mockito.when(world.isChunkLoaded(0, 0)).thenReturn(true);
    Mockito.when(world.getChunkAt(0, 0, false)).thenReturn(chunk);
    Mockito.when(chunk.getEntities()).thenThrow(new IllegalStateException("chunk entity view unavailable"));
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.nextLoadedChunkCoordinateBatch(64))
        .thenReturn(List.of(new ObserverController.LoadedChunkTarget(worldId, 0, 0)));
    AtomicInteger reports = new AtomicInteger();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      runSyncJobsImmediately(scheduling);
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      react.when(() -> React.reportError(Mockito.any(Throwable.class))).thenAnswer(invocation -> reports.incrementAndGet());
      react.when(() -> React.reportError(Mockito.anyString(), Mockito.any(Throwable.class)))
          .thenAnswer(invocation -> reports.incrementAndGet());
      FeatureHopperItemIndex feature = new FeatureHopperItemIndex();
      feature.onActivate();

      for (int pass = 0; pass < 5; pass++) {
        feature.onTick();
      }

      Mockito.verify(chunk, Mockito.times(5)).getEntities();
      assertEquals(1, reports.get());
      feature.onDeactivate();
    }
  }

  @Test
  void distinctReconcileFailuresEachReportTheirFirstStackTrace() {
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Chunk transientChunk = Mockito.mock(Chunk.class);
    Chunk brokenChunk = Mockito.mock(Chunk.class);
    Mockito.when(world.isChunkLoaded(Mockito.anyInt(), Mockito.eq(0))).thenReturn(true);
    Mockito.when(world.getChunkAt(0, 0, false)).thenReturn(transientChunk);
    Mockito.when(world.getChunkAt(1, 0, false)).thenReturn(brokenChunk);
    IllegalStateException transientFailure = new IllegalStateException("chunk entity view unavailable");
    NullPointerException brokenFailure = new NullPointerException("entity list missing");
    Mockito.when(transientChunk.getEntities()).thenThrow(transientFailure);
    Mockito.when(brokenChunk.getEntities()).thenThrow(brokenFailure);
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.nextLoadedChunkCoordinateBatch(64)).thenReturn(List.of(
        new ObserverController.LoadedChunkTarget(worldId, 0, 0),
        new ObserverController.LoadedChunkTarget(worldId, 1, 0)));
    List<Throwable> reported = new ArrayList<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      runSyncJobsImmediately(scheduling);
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      react.when(() -> React.reportError(Mockito.any(Throwable.class)))
          .thenAnswer(invocation -> reported.add(invocation.getArgument(0)));
      react.when(() -> React.reportError(Mockito.anyString(), Mockito.any(Throwable.class)))
          .thenAnswer(invocation -> reported.add(invocation.getArgument(1)));
      FeatureHopperItemIndex feature = new FeatureHopperItemIndex();
      feature.onActivate();

      for (int pass = 0; pass < 5; pass++) {
        feature.onTick();
      }

      Mockito.verify(transientChunk, Mockito.times(5)).getEntities();
      Mockito.verify(brokenChunk, Mockito.times(5)).getEntities();
      assertEquals(List.of(transientFailure, brokenFailure), reported);
      feature.onDeactivate();
    }
  }

  private static WeakReference<Item> trackUnreferencedItem(FeatureHopperItemIndex feature, World world) {
    Item item = item(new UUID(3L, 1L), new Location(world, 1.5D, 64D, 1.5D));
    feature.on(new ItemSpawnEvent(item));
    return new WeakReference<>(item);
  }

  private static Map<?, ?> trackedItems(FeatureHopperItemIndex feature) throws Exception {
    Field field = FeatureHopperItemIndex.class.getDeclaredField("trackedItems");
    field.setAccessible(true);
    return (Map<?, ?>) field.get(feature);
  }

  private static Item item(UUID itemId, Location location) {
    return (Item) Proxy.newProxyInstance(
        Item.class.getClassLoader(),
        new Class<?>[]{Item.class},
        (proxy, method, arguments) -> switch (method.getName()) {
          case "getUniqueId" -> itemId;
          case "isValid" -> true;
          case "isDead" -> false;
          case "getLocation" -> location.clone();
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == arguments[0];
          case "toString" -> "ItemFixture[" + itemId + "]";
          default -> defaultValue(method.getReturnType());
        }
    );
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == double.class) {
      return 0D;
    }
    if (type == float.class) {
      return 0F;
    }
    if (type == short.class) {
      return (short) 0;
    }
    if (type == byte.class) {
      return (byte) 0;
    }
    return '\0';
  }

  private static void runSyncJobsImmediately(MockedStatic<J> scheduling) {
    scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    });
  }
}
