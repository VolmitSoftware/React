package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.integration.GlossEntityOverlayIntegration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

class FeatureMobStackingMergeOrderTest {
  static {
    if (React.instance == null) {
      React react = Mockito.mock(React.class);
      Mockito.when(react.getName()).thenReturn("react");
      Mockito.when(react.namespace()).thenReturn("react");
      React.instance = react;
    }
  }

  @Test
  void stackLimitRejectsBeforeReadingEquipmentEffectsOrAttributes() {
    FeatureMobStacking feature = feature();
    LivingEntity source = zombie(1);
    LivingEntity target = zombie(2);
    Mockito.doReturn(6).when(feature).getStackCount(source);
    Mockito.doReturn(6).when(feature).getStackCount(target);

    Assertions.assertFalse(feature.canMerge(source, target));

    assertMergeStateUntouched(source);
    assertMergeStateUntouched(target);
  }

  @Test
  void doNotStackMetadataRejectsBeforeReadingMergeState() {
    FeatureMobStacking feature = feature();
    LivingEntity source = zombie(1);
    LivingEntity target = zombie(2);
    Mockito.doReturn(1).when(feature).getStackCount(Mockito.any());
    Mockito.when(target.hasMetadata("DoNotStack")).thenReturn(true);

    Assertions.assertFalse(feature.canMerge(source, target));

    assertMergeStateUntouched(source);
    assertMergeStateUntouched(target);
  }

  @Test
  void spawnerOnlyFilterRejectsBeforeReadingMergeState() throws ReflectiveOperationException {
    FeatureMobStacking feature = feature();
    Field onlySpawnerMobs = FeatureMobStacking.class.getDeclaredField("onlySpawnerMobs");
    onlySpawnerMobs.setAccessible(true);
    onlySpawnerMobs.setBoolean(feature, true);
    LivingEntity source = zombie(1);
    LivingEntity target = zombie(2);
    Mockito.doReturn(1).when(feature).getStackCount(Mockito.any());

    Assertions.assertFalse(feature.canMerge(source, target));

    assertMergeStateUntouched(source);
    assertMergeStateUntouched(target);
  }

  private static void assertMergeStateUntouched(LivingEntity entity) {
    Mockito.verify(entity, Mockito.never()).getEquipment();
    Mockito.verify(entity, Mockito.never()).getActivePotionEffects();
    Mockito.verify(entity, Mockito.never()).getCustomName();
    Mockito.verify(entity, Mockito.never()).getAttribute(Mockito.any());
    Mockito.verify(entity, Mockito.never()).getHealth();
  }

  private static FeatureMobStacking feature() {
    FeatureMobStacking feature = Mockito.spy(new FeatureMobStacking(Mockito.mock(GlossEntityOverlayIntegration.class)));
    Mockito.doReturn(true).when(feature).isStackableType(EntityType.ZOMBIE);
    return feature;
  }

  private static LivingEntity zombie(int entityId) {
    LivingEntity entity = Mockito.mock(LivingEntity.class);
    Mockito.when(entity.getEntityId()).thenReturn(entityId);
    Mockito.when(entity.getType()).thenReturn(EntityType.ZOMBIE);
    return entity;
  }
}
