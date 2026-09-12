package art.arcane.react.content.feature;

import art.arcane.react.core.integration.GlossEntityOverlayIntegration;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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

  @Test
  void deathReplacementDoesNotInheritCanonicalizedStackName() {
    FeatureMobStacking feature = Mockito.spy(new FeatureMobStacking(Mockito.mock(GlossEntityOverlayIntegration.class)));
    LivingEntity source = entity(ChatColor.BOLD + "3x " + ChatColor.GRAY + "Zombie");
    LivingEntity replacement = entity(null);
    World world = Mockito.mock(World.class);
    Location location = Mockito.mock(Location.class);
    EntityDeathEvent event = Mockito.mock(EntityDeathEvent.class);
    Mockito.when(source.getPersistentDataContainer().get(STACK_LABEL_KEY, PersistentDataType.STRING)).thenReturn(STACK_NAME);
    Mockito.when(source.getWorld()).thenReturn(world);
    Mockito.when(source.getLocation()).thenReturn(location);
    Mockito.when(world.spawnEntity(location, EntityType.ZOMBIE)).thenReturn(replacement);
    Mockito.when(replacement.isValid()).thenReturn(true);
    Mockito.when(event.getEntity()).thenReturn(source);
    Mockito.doReturn(3).when(feature).getStackCount(source);
    Mockito.doNothing().when(feature).setStackCount(Mockito.any(Entity.class), Mockito.anyInt());

    feature.onEntityDeath(event);

    Mockito.verify(replacement, Mockito.never()).setCustomName(Mockito.anyString());
    Mockito.verify(feature).setStackCount(replacement, 2);
  }

  private LivingEntity entity(String name) {
    LivingEntity entity = Mockito.mock(LivingEntity.class);
    PersistentDataContainer data = Mockito.mock(PersistentDataContainer.class);
    Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(entity.getType()).thenReturn(EntityType.ZOMBIE);
    AtomicReference<String> currentName = new AtomicReference<>(name);
    Mockito.when(entity.getCustomName()).thenAnswer(invocation -> currentName.get());
    Mockito.doAnswer(invocation -> {
      currentName.set(invocation.getArgument(0));
      return null;
    }).when(entity).setCustomName(Mockito.any());
    Mockito.when(entity.getPersistentDataContainer()).thenReturn(data);
    return entity;
  }
}
