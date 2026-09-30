package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.monitor.NativeWorldAccess;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class FeaturePathfinderBudgetBridgeCacheTest {
  private static React previous;

  @BeforeAll
  static void setUpPlugin() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getName()).thenReturn("React");
    Mockito.when(plugin.namespace()).thenReturn("react");
    React.instance = plugin;
  }

  @AfterAll
  static void restorePlugin() {
    React.instance = previous;
  }

  @Test
  void unavailableBridgesAreResolvedOnceAcrossChunkLoadsAndUnloads() {
    FeaturePathfinderBudget feature = new FeaturePathfinderBudget();
    List<Entity> mobs = List.of(mob(), mob(), mob());
    EntitiesLoadEvent load = Mockito.mock(EntitiesLoadEvent.class);
    EntitiesUnloadEvent unload = Mockito.mock(EntitiesUnloadEvent.class);
    Mockito.when(load.getEntities()).thenReturn(mobs);
    Mockito.when(unload.getEntities()).thenReturn(mobs);

    try (MockedStatic<NativeAdapters> adapters = Mockito.mockStatic(NativeAdapters.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      adapters.when(() -> NativeAdapters.find(NativeWorldAccess.class)).thenReturn(Optional.empty());
      scheduling.when(J::isFoliaThreading).thenReturn(false);

      feature.on(load);
      feature.on(unload);
      feature.on(load);
      feature.on(unload);

      adapters.verify(() -> NativeAdapters.find(NativeWorldAccess.class), Mockito.times(1));
    }
  }

  @Test
  void resolvedBridgesAreReadWithoutTheFeatureMonitor() throws Exception {
    FeaturePathfinderBudget feature = new FeaturePathfinderBudget();
    Field access = FeaturePathfinderBudget.class.getDeclaredField("nativeAccess");
    access.setAccessible(true);
    access.set(feature, Mockito.mock(NativeWorldAccess.class));
    EntitiesLoadEvent load = Mockito.mock(EntitiesLoadEvent.class);
    List<Entity> loaded = List.of(mob());
    Mockito.when(load.getEntities()).thenReturn(loaded);
    CountDownLatch held = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Thread holder = new Thread(() -> {
      synchronized (feature) {
        held.countDown();
        try {
          release.await(10L, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    });
    holder.start();
    Assertions.assertTrue(held.await(5L, TimeUnit.SECONDS));

    Thread worker = new Thread(() -> {
      try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
        scheduling.when(J::isFoliaThreading).thenReturn(false);
        feature.on(load);
      }
    });
    try {
      worker.start();
      worker.join(Duration.ofSeconds(2L).toMillis());
      Assertions.assertFalse(worker.isAlive(), "chunk load waited on the feature monitor");
    } finally {
      release.countDown();
      holder.join(5_000L);
      worker.join(5_000L);
    }
  }

  private static Mob mob() {
    PersistentDataContainer container = Mockito.mock(PersistentDataContainer.class);
    Mockito.when(container.getOrDefault(
        Mockito.any(NamespacedKey.class),
        Mockito.eq(PersistentDataType.BYTE),
        Mockito.anyByte()
    )).thenReturn((byte) 0);
    Mob mob = Mockito.mock(Mob.class);
    Mockito.when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(mob.getPersistentDataContainer()).thenReturn(container);
    return mob;
  }
}
