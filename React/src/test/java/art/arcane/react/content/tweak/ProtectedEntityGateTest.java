package art.arcane.react.content.tweak;

import art.arcane.react.React;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactOperations;
import art.arcane.react.api.protect.internal.ProtectionBinding;
import art.arcane.react.api.protect.internal.ProtectionInstaller;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class ProtectedEntityGateTest {

  // ReactEntity's clinit builds NamespacedKeys from React.instance; install a mock before any
  // test here touches ReactEntity so this class does not depend on execution order
  static {
    if (React.instance == null) {
      React react = Mockito.mock(React.class);
      Mockito.when(react.getName()).thenReturn("react");
      Mockito.when(react.namespace()).thenReturn("react");
      React.instance = react;
    }
  }

  @AfterEach
  void unbind() {
    ProtectionInstaller.install(null);
  }

  private static void bindProtecting(int mask) {
    ProtectionInstaller.install(new ProtectionBinding() {
      @Override
      public int operationsFor(Entity entity) {
        return mask;
      }

      @Override
      public boolean isProtected(Entity entity, ReactOperation operation) {
        return ReactOperations.covers(mask, operation);
      }

      @Override
      public String ownerOf(Entity entity) {
        return "test";
      }

      @Override
      public boolean protect(Entity entity, String ownerName, int operations) {
        return false;
      }

      @Override
      public boolean release(Entity entity, String ownerName) {
        return false;
      }

      @Override
      public boolean invalidate(Entity entity) {
        return false;
      }

      @Override
      public boolean spawnProtected(EntityType type, String worldName,
                                    org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        return false;
      }

      @Override
      public boolean guardAllows(Entity entity, ReactOperation operation) {
        return true;
      }

      @Override
      public int hydrate(Entity entity) {
        return mask;
      }

      @Override
      public int sweep(long nowMs, long retentionMs) {
        return 0;
      }
    });
  }

  @Test
  void fastIncinerationSkipsAnEntityProtectedFromDespawn() {
    bindProtecting(ReactOperations.of(ReactOperation.DESPAWN));
    TweakFastEntityIncineration tweak = new TweakFastEntityIncineration();
    EntityDamageEvent event = Mockito.mock(EntityDamageEvent.class);
    Monster monster = Mockito.mock(Monster.class);
    Location location = Mockito.mock(Location.class);
    Mockito.when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FIRE_TICK);
    Mockito.when(event.getEntity()).thenReturn(monster);
    Mockito.when(monster.getLocation()).thenReturn(location);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.hasNearbyPlayer(location, 32D)).thenReturn(false);

      tweak.on(event);

      scheduling.verifyNoInteractions();
      react.verify(() -> React.hasNearbyPlayer(Mockito.any(Location.class), Mockito.anyDouble()),
          Mockito.never());
    }
  }

  @Test
  void fastIncinerationStillFiresForAnEntityProtectedFromSomethingElse() {
    bindProtecting(ReactOperations.of(ReactOperation.STACK));
    TweakFastEntityIncineration tweak = new TweakFastEntityIncineration();
    EntityDamageEvent event = Mockito.mock(EntityDamageEvent.class);
    Monster monster = Mockito.mock(Monster.class);
    Location location = Mockito.mock(Location.class);
    Mockito.when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FIRE_TICK);
    Mockito.when(event.getEntity()).thenReturn(monster);
    Mockito.when(monster.getLocation()).thenReturn(location);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.hasNearbyPlayer(location, 32D)).thenReturn(false);
      scheduling.when(() -> J.runEntity(Mockito.eq(monster), Mockito.any(Runnable.class), Mockito.anyInt()))
          .thenReturn(true);

      tweak.on(event);

      scheduling.verify(() -> J.runEntity(Mockito.eq(monster), Mockito.any(Runnable.class), Mockito.anyInt()));
    }
  }

  @Test
  void crowdPreventionSkipsAnEntityProtectedFromPurge() {
    bindProtecting(ReactOperations.of(ReactOperation.PURGE));
    TweakEntityCrowdPrevention tweak = new TweakEntityCrowdPrevention();
    Entity cow = Mockito.mock(Entity.class);

    try (MockedStatic<ReactEntity> entities = Mockito.mockStatic(ReactEntity.class)) {
      tweak.onCrowdCheck(cow);

      entities.verify(() -> ReactEntity.getCrowding(cow), Mockito.never());
    }
  }

  @Test
  void crowdPreventionSkipsPlayerNamedEntityByDefault() {
    bindProtecting(ReactOperations.NONE);
    TweakEntityCrowdPrevention tweak = new TweakEntityCrowdPrevention();
    Entity cow = Mockito.mock(Entity.class);
    Mockito.when(cow.getCustomName()).thenReturn("Betsy");

    try (MockedStatic<ReactEntity> entities = Mockito.mockStatic(ReactEntity.class)) {
      tweak.onCrowdCheck(cow);

      entities.verify(() -> ReactEntity.getCrowding(cow), Mockito.never());
    }
  }

  @Test
  void crowdPreventionStillRunsForAnUnprotectedEntity() {
    bindProtecting(ReactOperations.NONE);
    TweakEntityCrowdPrevention tweak = new TweakEntityCrowdPrevention();
    Entity cow = Mockito.mock(Entity.class);

    try (MockedStatic<ReactEntity> entities = Mockito.mockStatic(ReactEntity.class)) {
      entities.when(() -> ReactEntity.getCrowding(cow)).thenReturn(1D);

      tweak.onCrowdCheck(cow);

      entities.verify(() -> ReactEntity.getCrowding(cow));
    }
  }

  @Test
  void bubblerSkipsAnEntityProtectedFromDespawn() {
    bindProtecting(ReactOperations.of(ReactOperation.DESPAWN));
    TweakEntityBubbler tweak = new TweakEntityBubbler();
    Entity fish = Mockito.mock(Entity.class);

    tweak.onCrowdCheck(fish);

    Mockito.verify(fish, Mockito.never()).getLocation();
  }

  @Test
  void gatesFallOpenWhenReactProtectionIsUnbound() {
    ProtectionInstaller.install(null);
    TweakEntityCrowdPrevention tweak = new TweakEntityCrowdPrevention();
    Entity cow = Mockito.mock(Entity.class);

    try (MockedStatic<ReactEntity> entities = Mockito.mockStatic(ReactEntity.class)) {
      entities.when(() -> ReactEntity.getCrowding(cow)).thenReturn(1D);

      tweak.onCrowdCheck(cow);

      entities.verify(() -> ReactEntity.getCrowding(cow));
    }
  }
}
