package art.arcane.react.api.entity;

import art.arcane.react.React;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

class EntityPriorityFoliaBoundaryTest {
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

  @Test
  void distanceUpdateUsesOnlyImmutablePlayerIndexData() {
    Entity entity = Mockito.mock(Entity.class);
    Location location = Mockito.mock(Location.class);
    NearbyPlayerIndexController playerIndex = Mockito.mock(NearbyPlayerIndexController.class);
    Mockito.when(entity.getLocation()).thenReturn(location);
    Mockito.when(entity.getWorld()).thenThrow(new AssertionError("world player scan is not owner-safe"));
    Mockito.when(playerIndex.nearestDistanceSquared(location, 64D)).thenReturn(256D);

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(entity)).thenReturn(true);
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(playerIndex);

      new EntityPriority().updateDistanceToPlayer(entity);

      Mockito.verify(entity, Mockito.never()).getWorld();
      Mockito.verify(playerIndex).nearestDistanceSquared(location, 64D);
      managed.verify(() -> ReactEntity.setNearestPlayer(entity, 1.590625D));
    }
  }

  @Test
  void crowdUpdateRejectsForeignTargetsBeforeReadingTheirState() {
    Entity source = Mockito.mock(Entity.class);
    Entity foreignTarget = Mockito.mock(Entity.class);
    UUID sourceId = UUID.randomUUID();
    Mockito.when(source.getUniqueId()).thenReturn(sourceId);
    Mockito.when(source.getNearbyEntities(8D, 8D, 8D)).thenReturn(List.of(foreignTarget));
    Mockito.when(foreignTarget.getUniqueId()).thenThrow(new AssertionError("foreign UUID read"));
    EntityPriority priority = Mockito.spy(new EntityPriority());
    Mockito.doReturn(100D).when(priority).getPriority(source);

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<ReactEntity> managed = Mockito.mockStatic(ReactEntity.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenAnswer(
          invocation -> invocation.getArgument(0) == source
      );

      priority.updateCrowd(source);

      Mockito.verify(foreignTarget, Mockito.never()).getUniqueId();
      Mockito.verify(foreignTarget, Mockito.never()).getType();
      managed.verify(() -> ReactEntity.setCrowding(source, 1D));
    }
  }

  @Test
  void foreignSourceIsScheduledBeforeAnyEntityStateRead() {
    Entity entity = Mockito.mock(Entity.class);
    Mockito.when(entity.getLocation()).thenThrow(new AssertionError("source location read off-owner"));

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(entity)).thenReturn(false);
      scheduling.when(() -> J.runEntity(Mockito.eq(entity), Mockito.any(Runnable.class))).thenReturn(true);

      new EntityPriority().updateDistanceToPlayer(entity);

      Mockito.verify(entity, Mockito.never()).getLocation();
      scheduling.verify(() -> J.runEntity(Mockito.eq(entity), Mockito.any(Runnable.class)));
    }
  }
}
