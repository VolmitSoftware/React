package art.arcane.react.content.feature;

import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.MobStackHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class FeatureMobStackingCoreTest {
  private final Map<GameEntity, Integer> counts = new HashMap<>();
  private FakeMobStackHost host;
  private FeatureMobStacking feature;
  private MockedStatic<ReactEntity> stacks;
  private MockedStatic<ReactPlatforms> platforms;

  @BeforeEach
  void setUp() {
    host = new FakeMobStackHost();
    feature = new FeatureMobStacking(host);
    stacks = Mockito.mockStatic(ReactEntity.class);
    stacks.when(() -> ReactEntity.getStackCount(Mockito.any(GameEntity.class)))
        .thenAnswer(invocation -> counts.getOrDefault(invocation.<GameEntity>getArgument(0), 1));
    stacks.when(() -> ReactEntity.setStackCount(Mockito.any(GameEntity.class), Mockito.anyInt()))
        .thenAnswer(invocation -> {
          counts.put(invocation.getArgument(0), invocation.getArgument(1));
          return null;
        });
    platforms = Mockito.mockStatic(ReactPlatforms.class);
    ReactPlatform platform = Mockito.mock(ReactPlatform.class);
    platforms.when(ReactPlatforms::get).thenReturn(platform);
    platforms.when(ReactPlatforms::getOrNull).thenReturn(platform);
  }

  @AfterEach
  void tearDown() {
    platforms.close();
    stacks.close();
  }

  @Test
  void mergeAddsTheSourceCountDiscardsItAndPlaysTheCollectAnimation() throws ReflectiveOperationException {
    activate();
    GameEntity source = host.add("ZOMBIE", 0D);
    GameEntity target = host.add("ZOMBIE", 1D);
    counts.put(source, 2);
    counts.put(target, 3);

    Assertions.assertTrue(feature.merge(source, target));

    Assertions.assertEquals(5, counts.get(target));
    Assertions.assertEquals(1, host.discarded.size());
    Assertions.assertSame(source, host.discarded.get(0));
    Assertions.assertEquals(1, host.collected.size());
    Assertions.assertTrue(host.mob(target).name.contains("5x"));
  }

  @Test
  void mergeRefusesOverfullStacksMarkedMobsAndOtherTypes() throws ReflectiveOperationException {
    activate();
    GameEntity source = host.add("ZOMBIE", 0D);
    GameEntity target = host.add("ZOMBIE", 0D);
    counts.put(source, 6);
    counts.put(target, 5);
    Assertions.assertFalse(feature.merge(source, target));

    counts.put(source, 1);
    host.setFlag(target, MobStackHost.DO_NOT_STACK, true);
    Assertions.assertFalse(feature.merge(source, target));

    Assertions.assertFalse(feature.merge(host.add("ZOMBIE", 0D), host.add("CREEPER", 0D)));
    Assertions.assertTrue(host.discarded.isEmpty());
  }

  @Test
  void inactiveFeatureNeverMerges() {
    Assertions.assertFalse(feature.merge(host.add("ZOMBIE", 0D), host.add("ZOMBIE", 0D)));
  }

  @Test
  void deathOfAStackSpawnsTheRemainderAndDropsTheDeadUnitsLabel() {
    GameEntity source = host.add("ZOMBIE", 0D);
    counts.put(source, 4);
    feature.refreshStackPresentation(source, 4);

    feature.onDeath(source);

    Assertions.assertEquals(1, host.spawned.size());
    Assertions.assertEquals(3, counts.get(host.spawned.get(0)));
    Assertions.assertNull(host.mob(source).name);
    Assertions.assertNull(host.mob(source).label);
  }

  @Test
  void deathOfASingleMobSpawnsNothing() {
    feature.onDeath(host.add("ZOMBIE", 0D));

    Assertions.assertTrue(host.spawned.isEmpty());
  }

  @Test
  void sneakInteractSplitsTheStackInHalfAndMarksBothUnique() {
    GameEntity clicked = host.add("ZOMBIE", 0D);
    counts.put(clicked, 7);

    feature.onSneakInteract(clicked);

    GameEntity split = host.spawned.get(0);
    Assertions.assertEquals(3, counts.get(split));
    Assertions.assertEquals(4, counts.get(clicked));
    Assertions.assertTrue(host.hasFlag(split, MobStackHost.UNIQUE_STACK));
    Assertions.assertTrue(host.hasFlag(clicked, MobStackHost.DO_NOT_STACK));
  }

  @Test
  void stateChangeSplitsTheRemainderOffAndIsVetoedWhenTheSpawnFails() {
    GameEntity creeper = host.add("CREEPER", 0D);
    counts.put(creeper, 3);

    Assertions.assertTrue(feature.beforeStateChange(creeper));
    Assertions.assertEquals(1, counts.get(creeper));
    Assertions.assertEquals(2, counts.get(host.spawned.get(0)));

    counts.put(creeper, 3);
    host.rejectSpawns = true;
    Assertions.assertFalse(feature.beforeStateChange(creeper));
    Assertions.assertEquals(3, counts.get(creeper));

    host.rejectSpawns = false;
    host.failCopies = true;
    Assertions.assertFalse(feature.beforeStateChange(creeper));
    Assertions.assertEquals(3, counts.get(creeper));
  }

  @Test
  void explodedCreeperStackLeavesItsRemainderBehind() {
    GameEntity creeper = host.add("CREEPER", 0D);
    counts.put(creeper, 5);

    feature.onRemove(creeper, false);
    Assertions.assertTrue(host.spawned.isEmpty());

    feature.onRemove(creeper, true);
    Assertions.assertEquals(4, counts.get(host.spawned.get(0)));
  }

  @Test
  void replacementsAreUntamedAndTamedStacksSplitOffThePet() {
    GameEntity wolf = host.add("WOLF", 0D);
    host.mob(wolf).tamed = true;
    host.mob(wolf).owner = UUID.randomUUID();
    counts.put(wolf, 3);

    feature.onTick(wolf);

    GameEntity remainder = host.spawned.get(0);
    Assertions.assertEquals(2, counts.get(remainder));
    Assertions.assertEquals(1, counts.get(wolf));
    Assertions.assertFalse(host.isTamedPet(remainder));
  }

  @Test
  void userNamesAreCopiedToReplacementsButStackNamesAreNot() {
    GameEntity named = host.add("ZOMBIE", 0D);
    counts.put(named, 2);
    host.mob(named).name = "Sentinel";
    feature.onDeath(named);
    Assertions.assertEquals("Sentinel", host.mob(host.spawned.get(0)).name);

    GameEntity stacked = host.add("ZOMBIE", 0D);
    counts.put(stacked, 3);
    feature.refreshStackPresentation(stacked, 3);
    feature.onDeath(stacked);
    Assertions.assertNotEquals(host.mob(stacked).name, host.mob(host.spawned.get(1)).name);
    Assertions.assertTrue(host.mob(host.spawned.get(1)).name.startsWith("§l2x"));
  }

  @Test
  void recoverySplitsADisabledStackIntoSingleMobs() {
    GameEntity source = host.add("ZOMBIE", 0D);
    counts.put(source, 4);
    feature.refreshStackPresentation(source, 4);
    feature.setEnabled(false);

    try (MockedStatic<ReactComponents> components = Mockito.mockStatic(ReactComponents.class)) {
      components.when(ReactComponents::isRunning).thenReturn(true);
      Assertions.assertEquals(2, feature.restoreStack(source, 2, 0L));
      Assertions.assertEquals(2, counts.get(source));
      Assertions.assertEquals(1, feature.restoreStack(source, 16, 0L));
    }

    Assertions.assertEquals(1, counts.get(source));
    Assertions.assertEquals(3, host.spawned.size());
    for (GameEntity spawned : host.spawned) {
      Assertions.assertEquals(1, counts.get(spawned));
      Assertions.assertNull(host.mob(spawned).label);
    }
    Assertions.assertNull(host.mob(source).name);
  }

  @Test
  void recoveryStopsWhenTheFeatureIsEnabledAgain() {
    GameEntity source = host.add("ZOMBIE", 0D);
    counts.put(source, 4);

    try (MockedStatic<ReactComponents> components = Mockito.mockStatic(ReactComponents.class)) {
      components.when(ReactComponents::isRunning).thenReturn(true);
      Assertions.assertEquals(0, feature.restoreStack(source, 16, 0L));
    }

    Assertions.assertEquals(4, counts.get(source));
  }

  @Test
  void nativeNamesMatchAcrossRedundantFormattingCodes() {
    String name = FeatureMobStacking.nativeStackName(FeatureMobStacking.baseStackName("MAGMA_CUBE"), 3, "");

    Assertions.assertEquals("§l3x §r§7Magma Cube", name);
    Assertions.assertTrue(FeatureMobStacking.sameFormattedName("§l3x §7Magma Cube", name));
    Assertions.assertFalse(FeatureMobStacking.sameFormattedName("Sentinel", name));
    Assertions.assertFalse(FeatureMobStacking.sameFormattedName("Sentinel", null));
  }

  private void activate() throws ReflectiveOperationException {
    Field active = FeatureMobStacking.class.getDeclaredField("active");
    active.setAccessible(true);
    active.setBoolean(feature, true);
  }
}
