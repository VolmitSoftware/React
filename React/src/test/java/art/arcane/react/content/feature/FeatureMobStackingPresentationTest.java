package art.arcane.react.content.feature;

import art.arcane.react.core.integration.GlossEntityOverlayIntegration;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.UUID;

class FeatureMobStackingPresentationTest {
  private static final NamespacedKey STACK_LABEL_KEY = new NamespacedKey("react", "mob-stack-label");
  private static final String STACK_NAME = ChatColor.BOLD + "3x " + ChatColor.RESET + ChatColor.GRAY + "Zombie";

  @Test
  void glossReceivesCountAndRemovesOnlyReactLabel() {
    GlossEntityOverlayIntegration integration = Mockito.mock(GlossEntityOverlayIntegration.class);
    FeatureMobStacking feature = new FeatureMobStacking(integration);
    LivingEntity entity = entity(STACK_NAME);
    Mockito.when(integration.refresh(entity, 3)).thenReturn(true);

    feature.refreshStackPresentation(entity, 3);

    Mockito.verify(integration).refresh(entity, 3);
    Mockito.verify(entity).setCustomName(null);
    Mockito.verify(entity.getPersistentDataContainer()).remove(STACK_LABEL_KEY);
  }

  @Test
  void glossKeepsUserRenameEvenWhenOldReactLabelIsRecorded() {
    GlossEntityOverlayIntegration integration = Mockito.mock(GlossEntityOverlayIntegration.class);
    FeatureMobStacking feature = new FeatureMobStacking(integration);
    LivingEntity entity = entity("Sentinel");
    Mockito.when(entity.getPersistentDataContainer().get(STACK_LABEL_KEY, PersistentDataType.STRING)).thenReturn(STACK_NAME);
    Mockito.when(integration.refresh(entity, 3)).thenReturn(true);

    feature.refreshStackPresentation(entity, 3);

    Mockito.verify(entity, Mockito.never()).setCustomName(Mockito.any());
  }

  @Test
  void absentOrDisabledGlossUsesNativeStackLabel() {
    FeatureMobStacking feature = new FeatureMobStacking(Mockito.mock(GlossEntityOverlayIntegration.class));
    LivingEntity entity = entity(null);

    feature.refreshStackPresentation(entity, 3);

    Mockito.verify(entity).setCustomName(STACK_NAME);
    Mockito.verify(entity.getPersistentDataContainer()).set(STACK_LABEL_KEY, PersistentDataType.STRING, STACK_NAME);
  }

  @Test
  void standaloneStackNeverOverwritesUserName() {
    FeatureMobStacking feature = new FeatureMobStacking(Mockito.mock(GlossEntityOverlayIntegration.class));
    LivingEntity entity = entity("Sentinel");

    feature.refreshStackPresentation(entity, 3);

    Mockito.verify(entity, Mockito.never()).setCustomName(Mockito.any());
  }

  @Test
  void disablingNativeNamesStillPublishesCountToGloss() throws ReflectiveOperationException {
    GlossEntityOverlayIntegration integration = Mockito.mock(GlossEntityOverlayIntegration.class);
    FeatureMobStacking feature = new FeatureMobStacking(integration);
    LivingEntity entity = entity(STACK_NAME);
    Field customNames = FeatureMobStacking.class.getDeclaredField("customNames");
    customNames.setAccessible(true);
    customNames.setBoolean(feature, false);

    feature.refreshStackPresentation(entity, 3);

    Mockito.verify(integration).refresh(entity, 3);
    Mockito.verify(entity).setCustomName(null);
  }

  @Test
  void shrinkingToOneRemovesRecordedNativeLabel() {
    FeatureMobStacking feature = new FeatureMobStacking(Mockito.mock(GlossEntityOverlayIntegration.class));
    LivingEntity entity = entity(STACK_NAME);
    Mockito.when(entity.getPersistentDataContainer().get(STACK_LABEL_KEY, PersistentDataType.STRING)).thenReturn(STACK_NAME);

    feature.refreshStackPresentation(entity, 1);

    Mockito.verify(entity).setCustomName(null);
  }

  private LivingEntity entity(String name) {
    LivingEntity entity = Mockito.mock(LivingEntity.class);
    PersistentDataContainer data = Mockito.mock(PersistentDataContainer.class);
    Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(entity.getType()).thenReturn(EntityType.ZOMBIE);
    Mockito.when(entity.getCustomName()).thenReturn(name);
    Mockito.when(entity.getPersistentDataContainer()).thenReturn(data);
    return entity;
  }
}
