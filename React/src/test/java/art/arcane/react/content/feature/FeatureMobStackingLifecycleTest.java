package art.arcane.react.content.feature;

import art.arcane.react.platform.GameEntity;
import art.arcane.react.React;
import art.arcane.react.api.feature.FeatureIntegrity;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.Subscription;
import java.lang.reflect.Field;
import java.util.Collection;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
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
  void everyEntityTypeUsesOneRemovableSubscriptionIdentity() throws ReflectiveOperationException {
    FeatureMobStacking feature = new FeatureMobStacking();
    EntityHost entities = Mockito.mock(EntityHost.class);
    Subscription subscription = Mockito.mock(Subscription.class);
    ArgumentCaptor<Consumer<GameEntity>> listeners = ArgumentCaptor.forClass(Consumer.class);
    ArgumentCaptor<Collection<String>> types = ArgumentCaptor.forClass(Collection.class);
    Mockito.when(entities.onTick(types.capture(), listeners.capture())).thenReturn(subscription);
    Field host = FeatureMobStacking.class.getDeclaredField("entities");
    host.setAccessible(true);
    host.set(feature, entities);

    feature.onActivate();
    feature.onDeactivate();

    Mockito.verify(entities, Mockito.times(1)).onTick(Mockito.anyCollection(), Mockito.any());
    Assertions.assertTrue(types.getValue().contains(EntityType.ZOMBIE.name()));
    Set<Consumer<GameEntity>> identities = Collections.newSetFromMap(new IdentityHashMap<>());
    identities.addAll(listeners.getAllValues());
    Assertions.assertEquals(1, identities.size());
    Mockito.verify(subscription).cancel();
  }

  @Test
  void inactiveFeatureRejectsQueuedMergeWorkButKeepsIntegrityListenerContract() {
    FeatureMobStacking feature = new FeatureMobStacking();
    Entity source = Mockito.mock(Entity.class);
    Entity target = Mockito.mock(Entity.class);

    Assertions.assertInstanceOf(FeatureIntegrity.class, feature);
    Assertions.assertFalse(feature.merge(MobStacks.entity(source), MobStacks.entity(target)));
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
    Mockito.when(replacement.isValid()).thenReturn(true);
    Mockito.doReturn(3).when(feature).getStackCount(MobStacks.entity(source));
    Mockito.doNothing().when(feature).setStackCount(Mockito.any(GameEntity.class), Mockito.anyInt());

    MobStacks.events(feature).onEntityDeath(event);

    Mockito.verify(feature).setStackCount(MobStacks.entity(replacement), 2);
    Mockito.verify(source, Mockito.never()).setCustomName(Mockito.any());
  }

  @Test
  void deathReplacementPreservesUserAssignedStackName() {
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
    Mockito.when(source.getCustomName()).thenReturn("Sentinel");
    Mockito.when(source.getPersistentDataContainer()).thenReturn(Mockito.mock(PersistentDataContainer.class));
    Mockito.when(world.spawnEntity(location, EntityType.ZOMBIE)).thenReturn(replacement);
    Mockito.when(replacement.isValid()).thenReturn(true);
    Mockito.doReturn(3).when(feature).getStackCount(MobStacks.entity(source));
    Mockito.doNothing().when(feature).setStackCount(Mockito.any(GameEntity.class), Mockito.anyInt());

    MobStacks.events(feature).onEntityDeath(event);

    Mockito.verify(replacement).setCustomName("Sentinel");
    Mockito.verify(source, Mockito.never()).setCustomName(Mockito.any());
  }
}
