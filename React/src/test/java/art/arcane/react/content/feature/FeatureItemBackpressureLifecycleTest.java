package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

class FeatureItemBackpressureLifecycleTest {
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
  void queuedPaperScanCannotRemoveAfterRestart() {
    FeatureItemBackpressure feature = new FeatureItemBackpressure();
    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.sample()).thenReturn(100D);
    World world = Mockito.mock(World.class);
    Item item = removableItem();
    List<Runnable> queued = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<WorldEntitySnapshots> snapshots = Mockito.mockStatic(WorldEntitySnapshots.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      react.when(() -> React.hasNearbyPlayer(Mockito.any(), Mockito.anyDouble())).thenReturn(false);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        queued.add(invocation.getArgument(0));
        return null;
      });
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      snapshots.when(() -> WorldEntitySnapshots.next(world, 220)).thenReturn(List.<Entity>of(item));

      feature.onActivate();
      feature.onTick();
      Assertions.assertEquals(1, queued.size());
      feature.onDeactivate();
      feature.onActivate();
      queued.getFirst().run();

      Mockito.verify(item, Mockito.never()).remove();
    }
  }

  @Test
  void foliaCandidateAcquisitionIsBoundedBeforeOwnerReads() throws Exception {
    FeatureItemBackpressure feature = new FeatureItemBackpressure();
    setInt(feature, "maxItemsScannedPerWorld", 5);
    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.sample()).thenReturn(100D);
    EntityController controller = Mockito.mock(EntityController.class);
    AtomicReference<Consumer<Entity>> listener = new AtomicReference<>();
    List<Item> candidates = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      Mockito.doAnswer(invocation -> {
        listener.set(invocation.getArgument(1));
        return null;
      }).when(controller).registerEntityTickListener(
          Mockito.eq(EntityType.ITEM),
          Mockito.<Consumer<Entity>>any()
      );
      scheduling.when(() -> J.runEntity(
          Mockito.any(Item.class),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        candidates.add(invocation.getArgument(0));
        return true;
      });

      feature.onActivate();
      Assertions.assertNotNull(listener.get());
      List<Item> indexed = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
        Item item = Mockito.mock(Item.class);
        Mockito.when(item.getUniqueId()).thenReturn(UUID.randomUUID());
        indexed.add(item);
        listener.get().accept(item);
      }
      feature.onTick();

      Assertions.assertEquals(5, candidates.size());
      for (Item item : indexed) {
        Mockito.verify(item, Mockito.never()).isDead();
        Mockito.verify(item, Mockito.never()).getTicksLived();
      }
    }
  }

  @Test
  void duplicateRetirementCannotFinishFoliaCycleEarly() throws Exception {
    FeatureItemBackpressure feature = new FeatureItemBackpressure();
    setInt(feature, "maxItemsScannedPerWorld", 2);
    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.sample()).thenReturn(100D);
    EntityController controller = Mockito.mock(EntityController.class);
    AtomicReference<Consumer<Entity>> listener = new AtomicReference<>();
    List<Runnable> retired = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      Mockito.doAnswer(invocation -> {
        listener.set(invocation.getArgument(1));
        return null;
      }).when(controller).registerEntityTickListener(
          Mockito.eq(EntityType.ITEM),
          Mockito.<Consumer<Entity>>any()
      );
      scheduling.when(() -> J.runEntity(
          Mockito.any(Item.class),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        retired.add(invocation.getArgument(3));
        return true;
      });

      feature.onActivate();
      listener.get().accept(indexedItem());
      listener.get().accept(indexedItem());
      feature.onTick();
      Assertions.assertEquals(2, retired.size());
      retired.getFirst().run();
      retired.getFirst().run();
      feature.onTick();
      Assertions.assertEquals(2, retired.size());
      Assertions.assertTrue(scanQueued(feature).get());

      retired.get(1).run();
      Assertions.assertFalse(scanQueued(feature).get());
    }
  }

  @Test
  void deactivationWaitsForRemovalAlreadyAtMutationBoundary() throws Exception {
    FeatureItemBackpressure feature = new FeatureItemBackpressure();
    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      feature.onActivate();
    }
    long generation = lifecycle(feature).get();
    Item item = Mockito.mock(Item.class);
    CountDownLatch enteredRemoval = new CountDownLatch(1);
    CountDownLatch allowRemoval = new CountDownLatch(1);
    AtomicBoolean removalFinished = new AtomicBoolean(false);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Mockito.doAnswer(invocation -> {
      enteredRemoval.countDown();
      if (!allowRemoval.await(2, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Removal barrier was not released");
      }
      removalFinished.set(true);
      return null;
    }).when(item).remove();

    Thread removal = new Thread(() -> {
      try {
        removeOwnedItem(feature, item, generation);
      } catch (Throwable throwable) {
        failure.set(throwable);
      }
    });
    removal.start();
    Assertions.assertTrue(enteredRemoval.await(2, TimeUnit.SECONDS));

    Thread deactivation = new Thread(() -> {
      try {
        feature.onDeactivate();
      } catch (Throwable throwable) {
        failure.set(throwable);
      }
    });
    deactivation.start();
    deactivation.join(100L);
    Assertions.assertTrue(deactivation.isAlive());

    allowRemoval.countDown();
    removal.join(2_000L);
    deactivation.join(2_000L);

    Assertions.assertFalse(removal.isAlive());
    Assertions.assertFalse(deactivation.isAlive());
    Assertions.assertTrue(removalFinished.get());
    Assertions.assertNull(failure.get());
  }

  private Item removableItem() {
    Item item = Mockito.mock(Item.class);
    ItemStack itemStack = Mockito.mock(ItemStack.class);
    Mockito.when(item.getTicksLived()).thenReturn(1000);
    Mockito.when(item.getItemStack()).thenReturn(itemStack);
    Mockito.when(itemStack.getType()).thenReturn(Material.COBBLESTONE);
    return item;
  }

  private Item indexedItem() {
    Item item = Mockito.mock(Item.class);
    Mockito.when(item.getUniqueId()).thenReturn(UUID.randomUUID());
    return item;
  }

  private boolean removeOwnedItem(FeatureItemBackpressure feature, Item item, long generation) throws Exception {
    Method method = FeatureItemBackpressure.class.getDeclaredMethod("removeOwnedItem", Item.class, long.class);
    method.setAccessible(true);
    return (boolean) method.invoke(feature, item, generation);
  }

  private AtomicLong lifecycle(FeatureItemBackpressure feature) throws ReflectiveOperationException {
    Field field = FeatureItemBackpressure.class.getDeclaredField("lifecycleGeneration");
    field.setAccessible(true);
    return (AtomicLong) field.get(feature);
  }

  private AtomicBoolean scanQueued(FeatureItemBackpressure feature) throws ReflectiveOperationException {
    Field field = FeatureItemBackpressure.class.getDeclaredField("itemScanQueued");
    field.setAccessible(true);
    return (AtomicBoolean) field.get(feature);
  }

  private void setInt(Object target, String fieldName, int value) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.setInt(target, value);
  }
}
