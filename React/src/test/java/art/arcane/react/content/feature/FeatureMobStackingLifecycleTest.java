package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.FeatureIntegrityListener;
import art.arcane.react.core.controller.EntityController;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

class FeatureMobStackingLifecycleTest {
  @Test
  void everyEntityTypeUsesOneRemovableSubscriptionIdentity() {
    FeatureMobStacking feature = new FeatureMobStacking();
    EntityController controller = Mockito.mock(EntityController.class);
    ArgumentCaptor<Consumer<Entity>> listeners = ArgumentCaptor.forClass(Consumer.class);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      feature.onActivate();
      feature.onDeactivate();
    }

    Mockito.verify(controller, Mockito.atLeastOnce())
        .registerEntityTickListener(Mockito.any(EntityType.class), listeners.capture());
    List<Consumer<Entity>> registrations = listeners.getAllValues();
    Set<Consumer<Entity>> identities = Collections.newSetFromMap(new IdentityHashMap<>());
    identities.addAll(registrations);
    Assertions.assertEquals(1, identities.size());
    Mockito.verify(controller).unregisterEntityTickListener(Mockito.same(registrations.getFirst()));
  }

  @Test
  void inactiveFeatureRejectsQueuedMergeWorkButKeepsIntegrityListenerContract() {
    FeatureMobStacking feature = new FeatureMobStacking();
    Entity source = Mockito.mock(Entity.class);
    Entity target = Mockito.mock(Entity.class);

    Assertions.assertInstanceOf(FeatureIntegrityListener.class, feature);
    Assertions.assertFalse(feature.merge(source, target));
    Mockito.verifyNoInteractions(source, target);
  }

  @Test
  void inactiveFeaturePreservesTheRemainderOfAnExistingStackOnDeath() {
    FeatureMobStacking feature = Mockito.spy(new FeatureMobStacking());
    EntityDeathEvent event = Mockito.mock(EntityDeathEvent.class);
    LivingEntity source = Mockito.mock(LivingEntity.class);
    LivingEntity replacement = Mockito.mock(LivingEntity.class);
    World world = Mockito.mock(World.class);
    Location location = Mockito.mock(Location.class);
    Mockito.when(event.getEntity()).thenReturn(source);
    Mockito.when(source.getWorld()).thenReturn(world);
    Mockito.when(source.getLocation()).thenReturn(location);
    Mockito.when(source.getType()).thenReturn(EntityType.ZOMBIE);
    Mockito.when(world.spawnEntity(location, EntityType.ZOMBIE)).thenReturn(replacement);
    Mockito.doReturn(3).when(feature).getStackCount(source);
    Mockito.doNothing().when(feature).setStackCount(Mockito.any(Entity.class), Mockito.anyInt());

    feature.onEntityDeath(event);

    Mockito.verify(feature).setStackCount(replacement, 2);
    Mockito.verify(source).setCustomName(null);
  }
}
