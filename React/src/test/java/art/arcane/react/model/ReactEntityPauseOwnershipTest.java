package art.arcane.react.model;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

class ReactEntityPauseOwnershipTest {
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

  @BeforeEach
  void clearManagedStateBeforeTest() throws ReflectiveOperationException {
    managedStates("pauseByEntity").clear();
    managedStates("dozeByEntity").clear();
  }

  @AfterEach
  void clearManagedStateAfterTest() throws ReflectiveOperationException {
    managedStates("pauseByEntity").clear();
    managedStates("dozeByEntity").clear();
  }

  @Test
  void aiRemainsPausedUntilEveryOwnerReleasesIt() {
    AtomicBoolean ai = new AtomicBoolean(true);
    AtomicReference<Byte> marker = new AtomicReference<>();
    LivingEntity entity = livingEntity(ai, marker);

    ReactEntity.requestPause(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
    ReactEntity.requestPause(entity, ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE);
    ReactEntity.releasePause(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);

    Assertions.assertFalse(ai.get());
    Assertions.assertTrue(ReactEntity.isPausedBy(entity, ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE));

    ReactEntity.releasePause(entity, ReactEntity.PauseOwner.DYNAMIC_ACTIVATION_RANGE);

    Assertions.assertTrue(ai.get());
    Assertions.assertNull(marker.get());
  }

  @Test
  void externallyDisabledAiIsNeverClaimedOrEnabled() {
    AtomicBoolean ai = new AtomicBoolean(false);
    AtomicReference<Byte> marker = new AtomicReference<>();
    LivingEntity entity = livingEntity(ai, marker);

    ReactEntity.requestPause(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
    ReactEntity.releasePause(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);

    Assertions.assertFalse(ai.get());
    Assertions.assertNull(marker.get());
  }

  @Test
  void dutyCycleRestoresOnlyAwarenessOwnedByReact() {
    AtomicBoolean aware = new AtomicBoolean(true);
    AtomicReference<Byte> marker = new AtomicReference<>();
    PersistentDataContainer container = container(marker);
    Mob mob = Mockito.mock(Mob.class);
    Mockito.when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(mob.getPersistentDataContainer()).thenReturn(container);
    Mockito.when(mob.isAware()).thenAnswer(ignored -> aware.get());
    Mockito.doAnswer(invocation -> {
      aware.set(invocation.getArgument(0));
      return null;
    }).when(mob).setAware(Mockito.anyBoolean());

    ReactEntity.requestDoze(mob);
    Assertions.assertFalse(aware.get());

    ReactEntity.releaseDoze(mob);
    Assertions.assertTrue(aware.get());
    Assertions.assertNull(marker.get());
  }

  @Test
  void delayedOwnerReleaseCannotRemoveAReacquiredClaim() {
    AtomicBoolean ai = new AtomicBoolean(true);
    AtomicReference<Byte> marker = new AtomicReference<>();
    LivingEntity entity = livingEntity(ai, marker);
    ArrayList<Runnable> queued = new ArrayList<>();

    ReactEntity.requestPause(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      queueOwnedCleanup(scheduling, entity, queued, true);

      ReactEntity.releasePauseOwner(ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
      Assertions.assertEquals(1, queued.size());

      ReactEntity.requestPause(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
      queued.getFirst().run();
    }

    Assertions.assertFalse(ai.get());
    Assertions.assertEquals((byte) 1, marker.get());
    Assertions.assertTrue(ReactEntity.isPausedBy(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP));

    ReactEntity.releasePause(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
    Assertions.assertTrue(ai.get());
  }

  @Test
  void delayedStopCleanupCannotRemoveRestartedPauseOrDozeClaims() {
    AtomicBoolean ai = new AtomicBoolean(true);
    AtomicBoolean aware = new AtomicBoolean(true);
    Map<NamespacedKey, Byte> markers = new ConcurrentHashMap<>();
    Mob mob = managedMob(UUID.randomUUID(), ai, aware, markers);
    ArrayList<Runnable> queued = new ArrayList<>();

    ReactEntity.requestPause(mob, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
    ReactEntity.requestDoze(mob);
    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      queueOwnedCleanup(scheduling, mob, queued, true);

      ReactEntity.releaseAllManagedState();
      Assertions.assertEquals(2, queued.size());

      ReactEntity.requestPause(mob, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
      ReactEntity.requestDoze(mob);
      queued.forEach(Runnable::run);
    }

    Assertions.assertFalse(ai.get());
    Assertions.assertFalse(aware.get());
    Assertions.assertTrue(ReactEntity.isPausedBy(mob, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP));
    Assertions.assertTrue(ReactEntity.isDozing(mob));

    ReactEntity.releasePause(mob, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
    ReactEntity.releaseDoze(mob);
    Assertions.assertTrue(ai.get());
    Assertions.assertTrue(aware.get());
  }

  @Test
  void sweeperRemovesClearedWeakManagedStateAndReconciliationReleasesMarkers()
      throws ReflectiveOperationException {
    UUID entityId = UUID.randomUUID();
    AtomicBoolean ai = new AtomicBoolean(true);
    AtomicBoolean aware = new AtomicBoolean(true);
    Map<NamespacedKey, Byte> markers = new ConcurrentHashMap<>();
    Mob mob = managedMob(entityId, ai, aware, markers);

    ReactEntity.requestPause(mob, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
    ReactEntity.requestDoze(mob);
    Map<UUID, Object> pauses = managedStates("pauseByEntity");
    Map<UUID, Object> dozes = managedStates("dozeByEntity");
    clearEntityReference(pauses.get(entityId));
    clearEntityReference(dozes.get(entityId));

    ReactEntity.sweepScratch();

    Assertions.assertFalse(pauses.containsKey(entityId));
    Assertions.assertFalse(dozes.containsKey(entityId));
    ReactEntity.reconcileManagedState(mob);
    Assertions.assertTrue(ai.get());
    Assertions.assertTrue(aware.get());
    Assertions.assertFalse(ReactEntity.isPaused(mob));
    Assertions.assertFalse(ReactEntity.isDozing(mob));
  }

  @Test
  void failedOwnedSchedulingDiscardsOnlyTheCapturedClaim() {
    AtomicBoolean ai = new AtomicBoolean(true);
    AtomicReference<Byte> marker = new AtomicReference<>();
    LivingEntity entity = livingEntity(ai, marker);
    ArrayList<Runnable> queued = new ArrayList<>();

    ReactEntity.requestPause(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      queueOwnedCleanup(scheduling, entity, queued, false);
      ReactEntity.releaseAllManagedState();
    }

    Assertions.assertTrue(queued.isEmpty());
    Assertions.assertFalse(ReactEntity.isPausedBy(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP));
    Assertions.assertFalse(ai.get());
    ReactEntity.reconcileManagedState(entity);
    Assertions.assertTrue(ai.get());
    Assertions.assertNull(marker.get());
  }

  @Test
  void managedCleanupSubmissionsAreBoundedPerDrain() {
    ArrayList<LivingEntity> entities = new ArrayList<>();
    for (int i = 0; i < 300; i++) {
      LivingEntity entity = livingEntity(new AtomicBoolean(true), new AtomicReference<>());
      entities.add(entity);
      ReactEntity.requestPause(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);
    }
    ArrayList<Runnable> operations = new ArrayList<>();

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenReturn(false);
      scheduling.when(() -> J.runEntity(
          Mockito.any(Entity.class),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        operations.add(invocation.getArgument(1));
        return true;
      });

      ReactEntity.releasePauseOwner(ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);

      Assertions.assertEquals(256, operations.size());
      new ArrayList<>(operations).forEach(Runnable::run);
      Assertions.assertEquals(300, operations.size());
      new ArrayList<>(operations.subList(256, 300)).forEach(Runnable::run);

      Assertions.assertTrue(ReactEntity.awaitManagedCleanup(0L));
    }
  }

  @Test
  void retirementFollowedByRejectedSubmissionCompletesExactlyOnce() {
    AtomicBoolean ai = new AtomicBoolean(true);
    AtomicReference<Byte> marker = new AtomicReference<>();
    LivingEntity entity = livingEntity(ai, marker);
    ReactEntity.requestPause(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(entity)).thenReturn(false);
      scheduling.when(() -> J.runEntity(
          Mockito.eq(entity),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        Runnable retired = invocation.getArgument(3);
        retired.run();
        return false;
      });

      ReactEntity.releasePauseOwner(ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP);

      Assertions.assertTrue(ReactEntity.awaitManagedCleanup(0L));
    }

    Assertions.assertFalse(ReactEntity.isPausedBy(entity, ReactEntity.PauseOwner.ADAPTIVE_ENTITY_SLEEP));
    Assertions.assertFalse(ai.get());
    ReactEntity.reconcileManagedState(entity);
    Assertions.assertTrue(ai.get());
  }

  private LivingEntity livingEntity(AtomicBoolean ai, AtomicReference<Byte> marker) {
    PersistentDataContainer container = container(marker);
    LivingEntity entity = Mockito.mock(LivingEntity.class);
    Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(entity.getPersistentDataContainer()).thenReturn(container);
    Mockito.when(entity.hasAI()).thenAnswer(ignored -> ai.get());
    Mockito.doAnswer(invocation -> {
      ai.set(invocation.getArgument(0));
      return null;
    }).when(entity).setAI(Mockito.anyBoolean());
    return entity;
  }

  private PersistentDataContainer container(AtomicReference<Byte> marker) {
    PersistentDataContainer container = Mockito.mock(PersistentDataContainer.class);
    Mockito.when(container.get(Mockito.any(NamespacedKey.class), Mockito.eq(PersistentDataType.BYTE)))
        .thenAnswer(ignored -> marker.get());
    Mockito.doAnswer(invocation -> {
      marker.set(invocation.getArgument(2));
      return null;
    }).when(container).set(
        Mockito.any(NamespacedKey.class),
        Mockito.eq(PersistentDataType.BYTE),
        Mockito.anyByte()
    );
    Mockito.doAnswer(invocation -> {
      marker.set(null);
      return null;
    }).when(container).remove(Mockito.any(NamespacedKey.class));
    return container;
  }

  private Mob managedMob(
      UUID entityId,
      AtomicBoolean ai,
      AtomicBoolean aware,
      Map<NamespacedKey, Byte> markers
  ) {
    PersistentDataContainer container = container(markers);
    Mob mob = Mockito.mock(Mob.class);
    Mockito.when(mob.getUniqueId()).thenReturn(entityId);
    Mockito.when(mob.getPersistentDataContainer()).thenReturn(container);
    Mockito.when(mob.hasAI()).thenAnswer(ignored -> ai.get());
    Mockito.doAnswer(invocation -> {
      ai.set(invocation.getArgument(0));
      return null;
    }).when(mob).setAI(Mockito.anyBoolean());
    Mockito.when(mob.isAware()).thenAnswer(ignored -> aware.get());
    Mockito.doAnswer(invocation -> {
      aware.set(invocation.getArgument(0));
      return null;
    }).when(mob).setAware(Mockito.anyBoolean());
    return mob;
  }

  private PersistentDataContainer container(Map<NamespacedKey, Byte> markers) {
    PersistentDataContainer container = Mockito.mock(PersistentDataContainer.class);
    Mockito.when(container.get(Mockito.any(NamespacedKey.class), Mockito.eq(PersistentDataType.BYTE)))
        .thenAnswer(invocation -> markers.get(invocation.getArgument(0)));
    Mockito.doAnswer(invocation -> {
      markers.put(invocation.getArgument(0), invocation.getArgument(2));
      return null;
    }).when(container).set(
        Mockito.any(NamespacedKey.class),
        Mockito.eq(PersistentDataType.BYTE),
        Mockito.anyByte()
    );
    Mockito.doAnswer(invocation -> {
      markers.remove(invocation.getArgument(0));
      return null;
    }).when(container).remove(Mockito.any(NamespacedKey.class));
    return container;
  }

  private void queueOwnedCleanup(
      MockedStatic<J> scheduling,
      LivingEntity entity,
      ArrayList<Runnable> queued,
      boolean accepted
  ) {
    scheduling.when(J::isFoliaThreading).thenReturn(true);
    scheduling.when(() -> J.isOwnedByCurrentRegion(entity)).thenReturn(false);
    scheduling.when(() -> J.runEntity(
        Mockito.eq(entity),
        Mockito.any(Runnable.class),
        Mockito.eq(0),
        Mockito.any(Runnable.class)
    )).thenAnswer(invocation -> {
      if (accepted) {
        queued.add(invocation.getArgument(1));
      }
      return accepted;
    });
  }

  @SuppressWarnings("unchecked")
  private Map<UUID, Object> managedStates(String fieldName) throws ReflectiveOperationException {
    Field field = ReactEntity.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return (Map<UUID, Object>) field.get(null);
  }

  private void clearEntityReference(Object state) throws ReflectiveOperationException {
    Method entity = state.getClass().getDeclaredMethod("entity");
    entity.setAccessible(true);
    WeakReference<?> reference = (WeakReference<?>) entity.invoke(state);
    reference.clear();
  }
}
