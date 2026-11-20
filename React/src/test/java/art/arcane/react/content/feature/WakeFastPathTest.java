package art.arcane.react.content.feature;

import art.arcane.react.platform.bukkit.BukkitGameEntity;
import art.arcane.react.React;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.platform.bukkit.BukkitGameEntity;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

class WakeFastPathTest {
  static {
    if (React.instance == null) {
      React react = Mockito.mock(React.class);
      Mockito.when(react.getName()).thenReturn("react");
      Mockito.when(react.namespace()).thenReturn("react");
      React.instance = react;
    }
  }

  @Test
  void adaptiveSleepDamageWakeSkipsTheLifecycleLockForAnUnmanagedMob() throws Exception {
    FeatureAdaptiveEntitySleep feature = new FeatureAdaptiveEntitySleep();
    feature.onActivate();
    Mob mob = awakeMob();
    ReentrantReadWriteLock lock = (ReentrantReadWriteLock) field(feature, "lifecycleLock");

    boolean completed = runWhileHeld(
        release -> {
          lock.writeLock().lock();
          try {
            release.run();
          } finally {
            lock.writeLock().unlock();
          }
        },
        () -> feature.onDamage(new BukkitGameEntity(mob))
    );

    Assertions.assertTrue(completed, "damage wake waited on the lifecycle lock");
    feature.onDeactivate();
  }

  @Test
  void adaptiveSleepTargetWakeSkipsTheLifecycleLockForUnmanagedMobs() throws Exception {
    FeatureAdaptiveEntitySleep feature = new FeatureAdaptiveEntitySleep();
    feature.onActivate();
    Mob attacker = awakeMob();
    Mob target = awakeMob();
    ReentrantReadWriteLock lock = (ReentrantReadWriteLock) field(feature, "lifecycleLock");

    boolean completed = runWhileHeld(
        release -> {
          lock.writeLock().lock();
          try {
            release.run();
          } finally {
            lock.writeLock().unlock();
          }
        },
        () -> feature.onTarget(new BukkitGameEntity(attacker), new BukkitGameEntity(target))
    );

    Assertions.assertTrue(completed, "target wake waited on the lifecycle lock");
    feature.onDeactivate();
  }

  @Test
  void adaptiveSleepStillWakesAPausedMob() {
    FeatureAdaptiveEntitySleep feature = new FeatureAdaptiveEntitySleep();
    Mob mob = awakeMob();

    try (MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      managed.when(() -> ReactEntity.isPausedBy(new BukkitGameEntity(mob), ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP)).thenReturn(true);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      feature.onActivate();

      feature.onDamage(new BukkitGameEntity(mob));

      managed.verify(() -> ReactEntity.releasePause(new BukkitGameEntity(mob), ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP));
      feature.onDeactivate();
    }
  }

  @Test
  void dynamicActivationDamageWakeSkipsTheLifecycleMonitorForAnUnpausedMob() throws Exception {
    FeatureDynamicActivationRange feature = new FeatureDynamicActivationRange();
    feature.onActivate();
    Mob mob = awakeMob();
    Object monitor = field(feature, "lifecycleLock");

    boolean completed = runWhileHeld(
        release -> {
          synchronized (monitor) {
            release.run();
          }
        },
        () -> feature.onDamage(new BukkitGameEntity(mob))
    );

    Assertions.assertTrue(completed, "damage wake waited on the lifecycle monitor");
  }

  @Test
  void dynamicActivationStillWakesAPausedMob() {
    FeatureDynamicActivationRange feature = new FeatureDynamicActivationRange();
    Mob mob = awakeMob();

    try (MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      managed.when(() -> ReactEntity.isPausedBy(new BukkitGameEntity(mob), ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE)).thenReturn(true);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      feature.onActivate();

      feature.onDamage(new BukkitGameEntity(mob));

      managed.verify(() -> ReactEntity.releasePause(new BukkitGameEntity(mob), ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE));
    }
  }

  private static boolean runWhileHeld(Consumer<Runnable> holdLock, Runnable handler) throws InterruptedException {
    CountDownLatch held = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Thread holder = new Thread(() -> holdLock.accept(() -> {
      held.countDown();
      try {
        release.await(10L, TimeUnit.SECONDS);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }));
    holder.start();
    Assertions.assertTrue(held.await(5L, TimeUnit.SECONDS));
    Thread worker = new Thread(() -> {
      try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
        scheduling.when(J::isFoliaThreading).thenReturn(false);
        handler.run();
      } catch (RuntimeException ignored) {
      }
    });
    try {
      worker.start();
      worker.join(2_000L);
      return !worker.isAlive();
    } finally {
      release.countDown();
      holder.join(5_000L);
      worker.join(5_000L);
    }
  }

  private static Mob awakeMob() {
    Mob mob = Mockito.mock(Mob.class);
    Mockito.when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(mob.isAware()).thenReturn(true);
    return mob;
  }

  private static Object field(Object owner, String name) throws ReflectiveOperationException {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(owner);
  }
}
