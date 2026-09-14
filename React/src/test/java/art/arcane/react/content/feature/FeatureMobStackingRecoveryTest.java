package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.integration.GlossEntityOverlayIntegration;
import art.arcane.react.model.ReactEntity;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class FeatureMobStackingRecoveryTest {
  private final Map<Entity, Integer> counts = new IdentityHashMap<>();
  private final List<LivingEntity> replacements = new ArrayList<>();
  private React previousPlugin;
  private React plugin;
  private World world;
  private FeatureMobStacking feature;
  private MockedStatic<ReactEntity> state;

  @BeforeEach
  void setUp() {
    previousPlugin = React.instance;
    plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getName()).thenReturn("React");
    Mockito.when(plugin.namespace()).thenReturn("react");
    Mockito.when(plugin.isEnabled()).thenReturn(true);
    Mockito.when(plugin.isReady()).thenReturn(true);
    React.instance = plugin;
    world = Mockito.mock(World.class);
    feature = new FeatureMobStacking(Mockito.mock(GlossEntityOverlayIntegration.class));
    feature.setEnabled(false);
    state = Mockito.mockStatic(ReactEntity.class);
    state.when(() -> ReactEntity.getStackCount(Mockito.any(Entity.class)))
        .thenAnswer(invocation -> counts.getOrDefault(invocation.getArgument(0), 1));
    state.when(() -> ReactEntity.setStackCount(Mockito.any(Entity.class), Mockito.anyInt()))
        .thenAnswer(invocation -> {
          counts.put(invocation.getArgument(0), invocation.getArgument(1));
          return null;
        });
    Mockito.when(world.spawnEntity(Mockito.any(Location.class), Mockito.eq(EntityType.ZOMBIE)))
        .thenAnswer(invocation -> {
          LivingEntity replacement = mob();
          replacements.add(replacement);
          return replacement;
        });
  }

  @AfterEach
  void tearDown() {
    state.close();
    React.instance = previousPlugin;
  }

  @Test
  void disabledFeatureRestoresCountAndCurrentHealthWithoutStackLabels() {
    LivingEntity source = stack(5);
    feature.updateEntityCustomName(source);

    Assertions.assertEquals(4, feature.restoreStack(source, 16, 0L));

    Assertions.assertEquals(1, counts.get(source));
    Assertions.assertNull(source.getCustomName());
    Assertions.assertEquals(4, replacements.size());
    for (LivingEntity replacement : replacements) {
      Assertions.assertEquals(1, counts.get(replacement));
      Assertions.assertNull(replacement.getCustomName());
      Mockito.verify(replacement).setHealth(6D);
      Mockito.verify(replacement, Mockito.atLeastOnce()).removeMetadata("UniqueMobStack", plugin);
    }
    Assertions.assertEquals(5, counts.values().stream().mapToInt(Integer::intValue).sum());
  }

  @Test
  void largeStackContinuesFromItsRemainingCountWithinSpawnBudget() {
    LivingEntity source = stack(30);

    Assertions.assertEquals(16, feature.restoreStack(source, 16, 0L));
    Assertions.assertEquals(14, counts.get(source));
    Assertions.assertEquals(13, feature.restoreStack(source, 16, 0L));

    Assertions.assertEquals(1, counts.get(source));
    Assertions.assertEquals(29, replacements.size());
    Assertions.assertEquals(30, counts.values().stream().mapToInt(Integer::intValue).sum());
  }

  @Test
  void cancelledSpawnPreservesTheUnrestoredRemainder() {
    LivingEntity source = stack(5);
    LivingEntity successful = mob();
    LivingEntity rejected = mob();
    Mockito.when(rejected.isValid()).thenReturn(false);
    Mockito.when(world.spawnEntity(Mockito.any(Location.class), Mockito.eq(EntityType.ZOMBIE)))
        .thenReturn(successful, rejected);

    try (MockedStatic<React> logging = Mockito.mockStatic(React.class)) {
      Assertions.assertEquals(1, feature.restoreStack(source, 16, 0L));
    }

    Assertions.assertEquals(4, counts.get(source));
    Assertions.assertEquals(1, counts.get(successful));
    Assertions.assertFalse(counts.containsKey(rejected));
    Mockito.verify(rejected).remove();
  }

  @Test
  void failedStateCopyRemovesTheReplacementAndPreservesCount() {
    LivingEntity source = stack(3);
    LivingEntity replacement = mob();
    RuntimeException failure = new IllegalStateException("copy failed");
    Mockito.doThrow(failure).when(replacement).setAI(Mockito.anyBoolean());
    Mockito.when(world.spawnEntity(Mockito.any(Location.class), Mockito.eq(EntityType.ZOMBIE)))
        .thenReturn(replacement);

    Assertions.assertSame(failure, Assertions.assertThrows(RuntimeException.class,
        () -> feature.restoreStack(source, 16, 0L)));

    Assertions.assertEquals(3, counts.get(source));
    Mockito.verify(replacement).remove();
  }

  @Test
  void failedCountWriteRemovesTheReplacement() {
    LivingEntity source = stack(2);
    state.when(() -> ReactEntity.setStackCount(source, 1))
        .thenThrow(new IllegalStateException("write failed"));
    counts.put(source, 2);

    Assertions.assertThrows(IllegalStateException.class, () -> feature.restoreStack(source, 16, 0L));

    Assertions.assertEquals(2, counts.get(source));
    Mockito.verify(replacements.getFirst()).remove();
  }

  @Test
  void reenableDuringSpawnDoesNotDuplicateTheStack() {
    LivingEntity source = stack(3);
    LivingEntity replacement = mob();
    Mockito.when(world.spawnEntity(Mockito.any(Location.class), Mockito.eq(EntityType.ZOMBIE)))
        .thenAnswer(invocation -> {
          feature.setEnabled(true);
          return replacement;
        });

    Assertions.assertEquals(0, feature.restoreStack(source, 16, 0L));

    Assertions.assertEquals(3, counts.get(source));
    Mockito.verify(replacement).remove();
  }

  @Test
  void enabledFeatureShutdownAndStaleGenerationNeverSpawn() {
    LivingEntity source = stack(3);
    feature.setEnabled(true);
    Assertions.assertEquals(0, feature.restoreStack(source, 16, 0L));
    feature.setEnabled(false);
    Mockito.when(plugin.isReady()).thenReturn(false);
    Assertions.assertEquals(0, feature.restoreStack(source, 16, 0L));
    Mockito.when(plugin.isReady()).thenReturn(true);
    Assertions.assertEquals(0, feature.restoreStack(source, 16, 1L));
    Assertions.assertEquals(0, feature.restoreStack(source, 0, 0L));

    Assertions.assertTrue(replacements.isEmpty());
    Assertions.assertEquals(3, counts.get(source));
  }

  @Test
  void restoredMobsKeepUserAssignedNames() {
    LivingEntity source = stack(3);
    source.setCustomName("Sentinel");

    Assertions.assertEquals(2, feature.restoreStack(source, 16, 0L));

    Assertions.assertEquals("Sentinel", source.getCustomName());
    for (LivingEntity replacement : replacements) {
      Assertions.assertEquals("Sentinel", replacement.getCustomName());
    }
  }

  private LivingEntity stack(int count) {
    LivingEntity source = mob();
    counts.put(source, count);
    return source;
  }

  private LivingEntity mob() {
    LivingEntity entity = Mockito.mock(LivingEntity.class);
    Mockito.when(entity.getType()).thenReturn(EntityType.ZOMBIE);
    Mockito.when(entity.getWorld()).thenReturn(world);
    Mockito.when(entity.getLocation()).thenReturn(new Location(world, 0D, 64D, 0D));
    Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(entity.isValid()).thenReturn(true);
    Mockito.when(entity.getMaxHealth()).thenReturn(20D);
    Mockito.when(entity.getHealth()).thenReturn(6D);
    AtomicReference<String> name = new AtomicReference<>();
    Mockito.when(entity.getCustomName()).thenAnswer(invocation -> name.get());
    Mockito.doAnswer(invocation -> {
      name.set(invocation.getArgument(0));
      return null;
    }).when(entity).setCustomName(Mockito.any());
    PersistentDataContainer data = Mockito.mock(PersistentDataContainer.class);
    Map<NamespacedKey, String> strings = new HashMap<>();
    Mockito.when(data.get(Mockito.any(NamespacedKey.class), Mockito.eq(PersistentDataType.STRING)))
        .thenAnswer(invocation -> strings.get(invocation.getArgument(0)));
    Mockito.when(data.has(Mockito.any(NamespacedKey.class)))
        .thenAnswer(invocation -> strings.containsKey(invocation.getArgument(0)));
    Mockito.doAnswer(invocation -> {
      strings.put(invocation.getArgument(0), invocation.getArgument(2));
      return null;
    }).when(data).set(Mockito.any(NamespacedKey.class), Mockito.eq(PersistentDataType.STRING), Mockito.anyString());
    Mockito.doAnswer(invocation -> {
      strings.remove(invocation.getArgument(0));
      return null;
    }).when(data).remove(Mockito.any(NamespacedKey.class));
    Mockito.when(entity.getPersistentDataContainer()).thenReturn(data);
    return entity;
  }
}
