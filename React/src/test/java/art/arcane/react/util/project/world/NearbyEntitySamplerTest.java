package art.arcane.react.util.project.world;

import art.arcane.react.util.common.scheduling.J;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class NearbyEntitySamplerTest {
  @Test
  void sharesOneEnumerationWithIndependentConsumerRotation() {
    Player player = player();
    Entity first = entity(1D);
    Entity second = entity(2D);
    Mockito.when(player.getNearbyEntities(48D, 32D, 48D)).thenReturn(List.of(first, second));
    NearbyEntitySampler sampler = new NearbyEntitySampler();
    try (MockedStatic<J> scheduling = owned()) {
      Assertions.assertEquals(List.of(first), sampler.sample(player, request("entity", 48D, 32D, 1)));
      Assertions.assertEquals(List.of(second), sampler.sample(player, request("entity", 48D, 32D, 1)));
      Assertions.assertEquals(List.of(first), sampler.sample(player, request("sleep", 48D, 32D, 1)));
      Mockito.verify(player).getNearbyEntities(48D, 32D, 48D);
      Mockito.verify(first, Mockito.never()).getBoundingBox();
    }
  }

  @Test
  void refreshesAtTheNextRegionTickAndAfterPlayerMovement() {
    Player player = player();
    Entity entity = entity(1D);
    Mockito.when(player.getNearbyEntities(48D, 32D, 48D)).thenReturn(List.of(entity));
    NearbyEntitySampler sampler = new NearbyEntitySampler();
    try (MockedStatic<J> scheduling = owned()) {
      sampler.sample(player, request("entity", 48D, 32D, 1));
      Mockito.when(player.getTicksLived()).thenReturn(11);
      sampler.sample(player, request("entity", 48D, 32D, 1));
      Mockito.when(player.getBoundingBox()).thenAnswer(ignored -> new BoundingBox(10D, 64D, 0D, 11D, 66D, 1D));
      sampler.sample(player, request("entity", 48D, 32D, 1));
      Mockito.verify(player, Mockito.times(3)).getNearbyEntities(48D, 32D, 48D);
    }
  }

  @Test
  void reusesLargerNeighborhoodWithoutIncludingOutsideEntities() {
    Player player = player();
    Entity far = entity(60D);
    Entity near = entity(1D);
    Mockito.when(player.getNearbyEntities(72D, 48D, 72D)).thenReturn(List.of(far, near));
    NearbyEntitySampler sampler = new NearbyEntitySampler();
    try (MockedStatic<J> scheduling = owned()) {
      sampler.sample(player, request("vehicle", 72D, 48D, 2));
      Assertions.assertEquals(List.of(near), sampler.sample(player, request("entity", 48D, 32D, 1)));
      Mockito.verify(player, Mockito.never()).getNearbyEntities(48D, 32D, 48D);
    }
  }

  @Test
  void usesExactNativeBoundsWhenCandidatesCrossRegionOwnership() {
    Player player = player();
    Entity foreign = entity(60D);
    Entity near = entity(1D);
    Mockito.when(player.getNearbyEntities(72D, 48D, 72D)).thenReturn(List.of(foreign, near));
    Mockito.when(player.getNearbyEntities(48D, 32D, 48D)).thenReturn(List.of(near));
    NearbyEntitySampler sampler = new NearbyEntitySampler();
    try (MockedStatic<J> scheduling = owned()) {
      scheduling.when(() -> J.isOwnedByCurrentRegion(foreign)).thenReturn(false);
      sampler.sample(player, request("vehicle", 72D, 48D, 2));
      Assertions.assertEquals(List.of(near), sampler.sample(player, request("entity", 48D, 32D, 1)));
      Mockito.verify(player).getNearbyEntities(48D, 32D, 48D);
      Mockito.verify(foreign, Mockito.never()).getBoundingBox();
    }
  }

  @Test
  void boundedFilteringContinuesFromItsCursorWithoutLosingLaterCandidates() {
    Player player = player();
    List<Entity> entities = new ArrayList<>();
    for (int index = 0; index < 80; index++) {
      entities.add(entity(60D));
    }
    Entity near = entity(1D);
    entities.add(near);
    Mockito.when(player.getNearbyEntities(72D, 48D, 72D)).thenReturn(entities);
    NearbyEntitySampler sampler = new NearbyEntitySampler();
    try (MockedStatic<J> scheduling = owned()) {
      sampler.sample(player, request("vehicle", 72D, 48D, 1));
      Assertions.assertTrue(sampler.sample(player, request("entity", 48D, 32D, 1)).isEmpty());
      Assertions.assertEquals(List.of(near), sampler.sample(player, request("entity", 48D, 32D, 1)));
      Mockito.verify(player).getNearbyEntities(72D, 48D, 72D);
    }
  }

  @Test
  void evictionDropsCachedEntitiesAndOwnershipIsRequiredBeforeReadingPlayerState() {
    Player player = player();
    Entity nearby = entity(1D);
    Mockito.when(player.getNearbyEntities(48D, 32D, 48D)).thenReturn(List.of(nearby));
    NearbyEntitySampler sampler = new NearbyEntitySampler();
    try (MockedStatic<J> scheduling = owned()) {
      sampler.sample(player, request("entity", 48D, 32D, 1));
      sampler.forget(player.getUniqueId());
      sampler.sample(player, request("entity", 48D, 32D, 1));
      sampler.clear();
      sampler.sample(player, request("entity", 48D, 32D, 1));
      Mockito.verify(player, Mockito.times(3)).getNearbyEntities(48D, 32D, 48D);
      Mockito.clearInvocations(player);
      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(false);
      Assertions.assertTrue(sampler.sample(player, request("entity", 48D, 32D, 1)).isEmpty());
      Mockito.verifyNoInteractions(player);
    }
  }

  private MockedStatic<J> owned() {
    MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
    scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenReturn(true);
    return scheduling;
  }

  private Player player() {
    Player player = Mockito.mock(Player.class);
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    Mockito.when(player.getWorld()).thenReturn(world);
    Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(player.getTicksLived()).thenReturn(10);
    Mockito.when(player.getBoundingBox()).thenAnswer(ignored -> new BoundingBox(0D, 64D, 0D, 1D, 66D, 1D));
    return player;
  }

  private Entity entity(double x) {
    Entity entity = Mockito.mock(Entity.class);
    Mockito.when(entity.getBoundingBox()).thenAnswer(ignored -> new BoundingBox(x, 64D, 0D, x + 1D, 66D, 1D));
    return entity;
  }

  private NearbyEntitySampler.Request request(String consumer, double horizontal, double vertical, int maximum) {
    return new NearbyEntitySampler.Request(consumer, horizontal, vertical, maximum);
  }
}
