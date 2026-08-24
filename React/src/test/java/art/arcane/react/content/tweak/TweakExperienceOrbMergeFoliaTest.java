package art.arcane.react.content.tweak;

import art.arcane.react.util.common.scheduling.J;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class TweakExperienceOrbMergeFoliaTest {
  @Test
  void localOverflowRemainsOnTheSourceAndPreservesTotalExperience() throws Exception {
    TweakExperienceOrbMerge tweak = new TweakExperienceOrbMerge();
    setInt(tweak, "maxExperiencePerOrb", 10);
    ExperienceOrb collector = Mockito.mock(ExperienceOrb.class);
    ExperienceOrb source = Mockito.mock(ExperienceOrb.class);
    EntitySpawnEvent event = Mockito.mock(EntitySpawnEvent.class);
    AtomicInteger collectorExperience = experience(collector, 8);
    AtomicInteger sourceExperience = experience(source, 7);
    Mockito.when(event.getEntity()).thenReturn(collector);
    Mockito.when(collector.isDead()).thenReturn(false);
    Mockito.when(collector.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(collector.getNearbyEntities(2.75D, 2.75D, 2.75D)).thenReturn(List.of(source));
    Mockito.when(source.isDead()).thenReturn(false);
    Mockito.when(source.getUniqueId()).thenReturn(UUID.randomUUID());

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      tweak.on(event);

      Assertions.assertEquals(10, collectorExperience.get());
      Assertions.assertEquals(5, sourceExperience.get());
      Assertions.assertEquals(15, collectorExperience.get() + sourceExperience.get());
      Mockito.verify(source, Mockito.never()).remove();
    }
  }

  @Test
  void foreignOverflowSkipsRejectableTransferAndPreservesTotalExperience() throws Exception {
    TweakExperienceOrbMerge tweak = new TweakExperienceOrbMerge();
    setInt(tweak, "maxExperiencePerOrb", 10);
    ExperienceOrb collector = Mockito.mock(ExperienceOrb.class);
    ExperienceOrb foreign = Mockito.mock(ExperienceOrb.class);
    EntitySpawnEvent event = Mockito.mock(EntitySpawnEvent.class);
    AtomicInteger collectorExperience = experience(collector, 8);
    AtomicInteger foreignExperience = experience(foreign, 7);
    Mockito.when(event.getEntity()).thenReturn(collector);
    Mockito.when(collector.isDead()).thenReturn(false);
    Mockito.when(collector.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.when(collector.getNearbyEntities(2.75D, 2.75D, 2.75D)).thenReturn(List.of(foreign));
    Mockito.when(foreign.isDead()).thenReturn(false);
    Mockito.when(foreign.getUniqueId()).thenReturn(UUID.randomUUID());
    Mockito.clearInvocations(foreign);

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenAnswer(
          invocation -> invocation.getArgument(0) == collector
      );

      tweak.on(event);

      Assertions.assertEquals(8, collectorExperience.get());
      Assertions.assertEquals(7, foreignExperience.get());
      Assertions.assertEquals(15, collectorExperience.get() + foreignExperience.get());
      Mockito.verify(foreign, Mockito.never()).isDead();
      Mockito.verify(foreign, Mockito.never()).isValid();
      Mockito.verify(foreign, Mockito.never()).getUniqueId();
      Mockito.verify(foreign, Mockito.never()).getExperience();
      Mockito.verify(foreign, Mockito.never()).setExperience(Mockito.anyInt());
      Mockito.verify(foreign, Mockito.never()).remove();
      scheduling.verify(() -> J.runEntity(
          Mockito.any(Entity.class),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      ), Mockito.never());
    }
  }

  private AtomicInteger experience(ExperienceOrb orb, int initial) {
    AtomicInteger value = new AtomicInteger(initial);
    Mockito.when(orb.getExperience()).thenAnswer(invocation -> value.get());
    Mockito.doAnswer(invocation -> {
      value.set(invocation.getArgument(0));
      return null;
    }).when(orb).setExperience(Mockito.anyInt());
    return value;
  }

  private void setInt(Object target, String name, int value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.setInt(target, value);
  }
}
