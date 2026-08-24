package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.feature.perworld.ReactScopedPressure;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.bridge.NmsBridgeHandle;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

class FeaturePathfinderBudgetLifecycleTest {
  private static final Object ENTITY_HANDLE = new Object();
  private static final Object NAVIGATION = new Object();
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
  void paperDeactivationRestoresNavigationAndRemovesMarker() throws Throwable {
    NavigationState navigation = new NavigationState();
    FeaturePathfinderBudget feature = configuredFeature(navigation, 1L);
    AtomicBoolean marker = new AtomicBoolean(false);
    Mob mob = managedMob(marker);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.hasNearbyPlayer(Mockito.any(), Mockito.anyDouble())).thenReturn(false);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(J::isPrimaryThread).thenReturn(true);

      manage(feature, mob, 1L);
      Assertions.assertTrue(marker.get());
      Assertions.assertEquals(1, navigation.applied.get());
      Assertions.assertEquals(1, trackedClaims(feature).size());
      Object claim = trackedClaims(feature).values().iterator().next();
      Assertions.assertInstanceOf(WeakReference.class, claimReference(claim));

      feature.onDeactivate();

      Assertions.assertFalse(marker.get());
      Assertions.assertEquals(1, navigation.reset.get());
      Assertions.assertTrue(trackedClaims(feature).isEmpty());
    }
  }

  @Test
  void calmTransitionReleasesClaimMissingFromTheNextScan() throws Throwable {
    NavigationState navigation = new NavigationState();
    FeaturePathfinderBudget feature = configuredFeature(navigation, 1L);
    AtomicBoolean marker = new AtomicBoolean(false);
    Mob mob = managedMob(marker);
    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.sample()).thenReturn(100D, 0D);
    ReactScopedPressure.setEnabled(false);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      react.when(() -> React.hasNearbyPlayer(Mockito.any(), Mockito.anyDouble())).thenReturn(false);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(J::isPrimaryThread).thenReturn(true);
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        Runnable operation = invocation.getArgument(0);
        operation.run();
        return null;
      });
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of());

      feature.onTick();
      manage(feature, mob, 1L);
      Assertions.assertTrue(marker.get());
      Assertions.assertEquals(1, trackedClaims(feature).size());

      feature.onTick();

      Assertions.assertFalse(marker.get());
      Assertions.assertEquals(1, navigation.reset.get());
      Assertions.assertTrue(trackedClaims(feature).isEmpty());
      bukkit.verify(Bukkit::getWorlds, Mockito.times(2));
    } finally {
      ReactScopedPressure.setEnabled(false);
    }
  }

  @Test
  void ownerScheduledDeactivationWaitsForCleanupBeforeReturning() throws Throwable {
    NavigationState navigation = new NavigationState();
    FeaturePathfinderBudget feature = configuredFeature(navigation, 1L);
    AtomicBoolean marker = new AtomicBoolean(false);
    Mob mob = managedMob(marker);
    CountDownLatch scheduled = new CountDownLatch(1);
    AtomicReference<Runnable> release = new AtomicReference<>();
    AtomicReference<Throwable> completionFailure = new AtomicReference<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.hasNearbyPlayer(Mockito.any(), Mockito.anyDouble())).thenReturn(false);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      manage(feature, mob, 1L);

      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(mob)).thenReturn(false);
      scheduling.when(() -> J.runEntity(
          Mockito.eq(mob),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        release.set(invocation.getArgument(1));
        scheduled.countDown();
        return true;
      });
      Thread completion = new Thread(() -> {
        try {
          if (!scheduled.await(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Pathfinder cleanup was not scheduled");
          }
          release.get().run();
        } catch (Throwable failure) {
          completionFailure.set(failure);
        }
      });
      completion.start();

      feature.onDeactivate();
      completion.join(2_000L);

      Assertions.assertFalse(completion.isAlive());
      Assertions.assertNull(completionFailure.get());
      Assertions.assertEquals(1, navigation.reset.get());
      Assertions.assertFalse(marker.get());
      Assertions.assertTrue(trackedClaims(feature).isEmpty());
    }
  }

  @Test
  void foliaDeactivationRestoresOnEntityOwner() throws Throwable {
    NavigationState navigation = new NavigationState();
    FeaturePathfinderBudget feature = configuredFeature(navigation, 1L);
    AtomicBoolean marker = new AtomicBoolean(false);
    Mob mob = managedMob(marker);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.hasNearbyPlayer(Mockito.any(), Mockito.anyDouble())).thenReturn(false);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(mob)).thenReturn(true);

      manage(feature, mob, 1L);
      feature.onDeactivate();

      Assertions.assertFalse(marker.get());
      Assertions.assertEquals(1, navigation.reset.get());
      Assertions.assertTrue(trackedClaims(feature).isEmpty());
    }
  }

  @Test
  void retirementFollowedByRejectedSubmissionCompletesExactlyOnce() throws Throwable {
    NavigationState navigation = new NavigationState();
    FeaturePathfinderBudget feature = configuredFeature(navigation, 1L);
    AtomicBoolean marker = new AtomicBoolean(false);
    Mob mob = managedMob(marker);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.hasNearbyPlayer(Mockito.any(), Mockito.anyDouble())).thenReturn(false);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      manage(feature, mob, 1L);

      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(mob)).thenReturn(false);
      scheduling.when(() -> J.runEntity(
          Mockito.eq(mob),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        Runnable retired = invocation.getArgument(3);
        retired.run();
        return false;
      });

      Assertions.assertDoesNotThrow(feature::onDeactivate);

      Assertions.assertTrue(trackedClaims(feature).isEmpty());
      Assertions.assertTrue(marker.get());
    }
  }

  @Test
  void unloadAndLoadReconcileTrackedAndStaleMarkers() throws Throwable {
    NavigationState navigation = new NavigationState();
    FeaturePathfinderBudget feature = configuredFeature(navigation, 1L);
    AtomicBoolean marker = new AtomicBoolean(false);
    Mob mob = managedMob(marker);
    EntitiesUnloadEvent unload = Mockito.mock(EntitiesUnloadEvent.class);
    EntitiesLoadEvent load = Mockito.mock(EntitiesLoadEvent.class);
    Mockito.when(unload.getEntities()).thenReturn(List.<Entity>of(mob));
    Mockito.when(load.getEntities()).thenReturn(List.<Entity>of(mob));

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.hasNearbyPlayer(Mockito.any(), Mockito.anyDouble())).thenReturn(false);
      scheduling.when(J::isFoliaThreading).thenReturn(false);

      manage(feature, mob, 1L);
      feature.on(unload);
      Assertions.assertFalse(marker.get());
      Assertions.assertTrue(trackedClaims(feature).isEmpty());

      marker.set(true);
      feature.on(load);
      Assertions.assertFalse(marker.get());
      Assertions.assertEquals(2, navigation.reset.get());
    }
  }

  @Test
  void foliaCandidateAcquisitionIsBoundedBeforeOwnerReads() throws Exception {
    NavigationState navigation = new NavigationState();
    FeaturePathfinderBudget feature = configuredFeature(navigation, 1L);
    setInt(feature, "maxEntitiesSampledPerCycle", 5);
    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.sample()).thenReturn(100D);
    List<Mob> indexed = new ArrayList<>();
    List<Mob> candidates = new ArrayList<>();
    List<Runnable> retired = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      Mob mob = Mockito.mock(Mob.class);
      Mockito.when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
      indexed.add(mob);
      indexMob(feature, mob);
    }

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runEntity(
          Mockito.any(Mob.class),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        candidates.add(invocation.getArgument(0));
        retired.add(invocation.getArgument(3));
        return true;
      });

      feature.onTick();

      Assertions.assertEquals(5, candidates.size());
      for (Mob mob : indexed) {
        Mockito.verify(mob, Mockito.never()).isDead();
        Mockito.verify(mob, Mockito.never()).getLocation();
      }
      retired.forEach(Runnable::run);
    }
  }

  @Test
  void duplicateRetirementCannotFinishPathfinderCycleEarly() throws Exception {
    NavigationState navigation = new NavigationState();
    FeaturePathfinderBudget feature = configuredFeature(navigation, 1L);
    setInt(feature, "maxEntitiesSampledPerCycle", 2);
    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.sample()).thenReturn(100D);
    List<Runnable> retired = new ArrayList<>();
    Mob first = Mockito.mock(Mob.class);
    Mob second = Mockito.mock(Mob.class);
    Mockito.when(first.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(second.getUniqueId()).thenReturn(UUID.randomUUID());
    indexMob(feature, first);
    indexMob(feature, second);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runEntity(
          Mockito.any(Mob.class),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        retired.add(invocation.getArgument(3));
        return true;
      });

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
  void deactivationWaitsForOwnerMutationBeforeReleaseSnapshot() throws Throwable {
    NavigationState navigation = new NavigationState();
    FeaturePathfinderBudget feature = configuredFeature(navigation, 1L);
    setInt(feature, "maxEntitiesSampledPerCycle", 1);
    AtomicBoolean marker = new AtomicBoolean(false);
    Mob mob = managedMob(marker);
    indexMob(feature, mob);
    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.sample()).thenReturn(100D);
    AtomicReference<Runnable> operation = new AtomicReference<>();
    CountDownLatch applyEntered = new CountDownLatch(1);
    CountDownLatch allowApply = new CountDownLatch(1);
    navigation.pauseApply(applyEntered, allowApply);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      react.when(() -> React.hasNearbyPlayer(Mockito.any(), Mockito.anyDouble())).thenReturn(false);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runEntity(
          Mockito.eq(mob),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        operation.set(invocation.getArgument(1));
        return true;
      });

      feature.onTick();
      Assertions.assertNotNull(operation.get());
      Thread owner = new Thread(operation.get());
      owner.start();
      Assertions.assertTrue(applyEntered.await(2, TimeUnit.SECONDS));

      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(J::isPrimaryThread).thenReturn(true);
      Thread release = new Thread(() -> {
        try {
          TimeUnit.MILLISECONDS.sleep(100L);
          allowApply.countDown();
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
        }
      });
      release.start();

      long started = System.nanoTime();
      feature.onDeactivate();
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      owner.join(2_000L);
      release.join(2_000L);

      Assertions.assertTrue(elapsedMillis >= 75L);
      Assertions.assertFalse(owner.isAlive());
      Assertions.assertEquals(1, navigation.applied.get());
      Assertions.assertEquals(1, navigation.reset.get());
      Assertions.assertFalse(marker.get());
      Assertions.assertTrue(trackedClaims(feature).isEmpty());
    }
  }

  private FeaturePathfinderBudget configuredFeature(NavigationState navigation, long generation) throws Exception {
    FeaturePathfinderBudget feature = new FeaturePathfinderBudget();
    setField(feature, "bridgeGetNavigation", bridge(navigation.navigationHandle()));
    setField(feature, "bridgeSetMaxVisited", bridge(navigation.applyHandle()));
    setField(feature, "bridgeResetMaxVisited", bridge(navigation.resetHandle()));
    setBoolean(feature, "bridgesAvailable", true);
    setBoolean(feature, "active", true);
    setDouble(feature, "lastTickMs", 100D);
    lifecycle(feature).set(generation);
    return feature;
  }

  private NmsBridgeHandle bridge(MethodHandle methodHandle) {
    NmsBridgeHandle bridge = Mockito.mock(NmsBridgeHandle.class);
    Mockito.when(bridge.available()).thenReturn(true);
    Mockito.when(bridge.methodHandle()).thenReturn(methodHandle);
    return bridge;
  }

  private Mob managedMob(AtomicBoolean marker) {
    PersistentDataContainer container = Mockito.mock(PersistentDataContainer.class);
    Mockito.when(container.getOrDefault(
        Mockito.any(NamespacedKey.class),
        Mockito.eq(PersistentDataType.BYTE),
        Mockito.anyByte()
    )).thenAnswer(invocation -> marker.get() ? (byte) 1 : (byte) 0);
    Mockito.doAnswer(invocation -> {
      marker.set(true);
      return null;
    }).when(container).set(
        Mockito.any(NamespacedKey.class),
        Mockito.eq(PersistentDataType.BYTE),
        Mockito.anyByte()
    );
    Mockito.doAnswer(invocation -> {
      marker.set(false);
      return null;
    }).when(container).remove(Mockito.any(NamespacedKey.class));

    Mob mob = Mockito.mock(Mob.class, Mockito.withSettings().extraInterfaces(PathfinderMobHandle.class));
    Mockito.when(((PathfinderMobHandle) mob).getHandle()).thenReturn(ENTITY_HANDLE);
    Mockito.when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(mob.getPersistentDataContainer()).thenReturn(container);
    return mob;
  }

  private void manage(FeaturePathfinderBudget feature, Mob mob, long generation) throws Throwable {
    Method method = FeaturePathfinderBudget.class.getDeclaredMethod("manageEntity", Entity.class, long.class);
    method.setAccessible(true);
    method.invoke(feature, mob, generation);
  }

  private void indexMob(FeaturePathfinderBudget feature, Mob mob) throws Exception {
    Method method = FeaturePathfinderBudget.class.getDeclaredMethod("indexMob", Entity.class);
    method.setAccessible(true);
    method.invoke(feature, mob);
  }

  private AtomicLong lifecycle(FeaturePathfinderBudget feature) throws Exception {
    Field field = FeaturePathfinderBudget.class.getDeclaredField("lifecycleGeneration");
    field.setAccessible(true);
    return (AtomicLong) field.get(feature);
  }

  private AtomicBoolean scanQueued(FeaturePathfinderBudget feature) throws Exception {
    Field field = FeaturePathfinderBudget.class.getDeclaredField("pathfinderScanQueued");
    field.setAccessible(true);
    return (AtomicBoolean) field.get(feature);
  }

  @SuppressWarnings("unchecked")
  private Map<UUID, Object> trackedClaims(FeaturePathfinderBudget feature) throws Exception {
    Field field = FeaturePathfinderBudget.class.getDeclaredField("budgetedMobs");
    field.setAccessible(true);
    return (ConcurrentHashMap<UUID, Object>) field.get(feature);
  }

  private WeakReference<?> claimReference(Object claim) throws Exception {
    Field field = claim.getClass().getDeclaredField("mobReference");
    field.setAccessible(true);
    return (WeakReference<?>) field.get(claim);
  }

  private void setField(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private void setBoolean(Object target, String name, boolean value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.setBoolean(target, value);
  }

  private void setDouble(Object target, String name, double value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.setDouble(target, value);
  }

  private void setInt(Object target, String name, int value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.setInt(target, value);
  }

  private static final class NavigationState {
    private final AtomicInteger applied = new AtomicInteger(0);
    private final AtomicInteger reset = new AtomicInteger(0);
    private volatile CountDownLatch applyEntered;
    private volatile CountDownLatch allowApply;

    private Object navigation(Object handle) {
      return handle == ENTITY_HANDLE ? NAVIGATION : null;
    }

    private void apply(Object navigation, float multiplier) {
      if (navigation == NAVIGATION && multiplier > 0F) {
        CountDownLatch entered = applyEntered;
        CountDownLatch allowed = allowApply;
        if (entered != null && allowed != null) {
          entered.countDown();
          try {
            if (!allowed.await(2, TimeUnit.SECONDS)) {
              throw new IllegalStateException("Pathfinder mutation barrier was not released");
            }
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pathfinder mutation barrier was interrupted", exception);
          }
        }
        applied.incrementAndGet();
      }
    }

    private void reset(Object navigation) {
      if (navigation == NAVIGATION) {
        reset.incrementAndGet();
      }
    }

    private MethodHandle navigationHandle() throws NoSuchMethodException, IllegalAccessException {
      return MethodHandles.lookup()
          .findVirtual(NavigationState.class, "navigation", MethodType.methodType(Object.class, Object.class))
          .bindTo(this);
    }

    private MethodHandle applyHandle() throws NoSuchMethodException, IllegalAccessException {
      return MethodHandles.lookup()
          .findVirtual(NavigationState.class, "apply", MethodType.methodType(void.class, Object.class, float.class))
          .bindTo(this);
    }

    private MethodHandle resetHandle() throws NoSuchMethodException, IllegalAccessException {
      return MethodHandles.lookup()
          .findVirtual(NavigationState.class, "reset", MethodType.methodType(void.class, Object.class))
          .bindTo(this);
    }

    private void pauseApply(CountDownLatch entered, CountDownLatch allowed) {
      applyEntered = entered;
      allowApply = allowed;
    }
  }
}
