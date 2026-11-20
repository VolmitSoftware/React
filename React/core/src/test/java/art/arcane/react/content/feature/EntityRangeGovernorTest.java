package art.arcane.react.content.feature;

import art.arcane.react.platform.EntityActivityHost;
import art.arcane.react.platform.EntityRange;
import art.arcane.react.platform.EntityRanges;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

class EntityRangeGovernorTest {
  private final MemoryRanges ranges = new MemoryRanges();

  @BeforeEach
  void bindPlatform() {
    ReactWorld world = Mockito.mock(ReactWorld.class);
    UUID worldId = UUID.randomUUID();
    Mockito.when(world.id()).thenReturn(worldId);
    WorldHost worlds = Mockito.mock(WorldHost.class);
    Mockito.when(worlds.worlds()).thenReturn(List.of(world));
    Mockito.when(worlds.world(worldId)).thenReturn(Optional.of(world));
    EntityActivityHost activity = Mockito.mock(EntityActivityHost.class);
    Mockito.when(activity.entityRanges(world)).thenReturn(Optional.of(ranges));
    ReactPlatform platform = Mockito.mock(ReactPlatform.class);
    Mockito.when(platform.worldHost()).thenReturn(Optional.of(worlds));
    Mockito.when(platform.entityActivityHost()).thenReturn(Optional.of(activity));
    ReactPlatforms.unbind();
    ReactPlatforms.bind(platform);
  }

  @AfterEach
  void unbindPlatform() {
    ReactPlatforms.unbind();
  }

  @Test
  void activationGovernorScalesSupportedRangesAndRestoresThemOnDeactivate() throws ReflectiveOperationException {
    FeatureActivationRangeGovernor governor = new FeatureActivationRangeGovernor();
    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(J::isPrimaryThread).thenReturn(true);
      governor.onActivate();
      requestWorldState(governor, true);

      Assertions.assertEquals(16, ranges.range(EntityRange.ACTIVATION_ANIMAL));
      Assertions.assertEquals(19, ranges.range(EntityRange.ACTIVATION_MONSTER));
      Assertions.assertEquals(51, ranges.range(EntityRange.ACTIVATION_RAIDER));
      Assertions.assertEquals(8, ranges.range(EntityRange.ACTIVATION_MISC));
      Assertions.assertFalse(ranges.tickInactiveVillagers());
      Assertions.assertEquals(48, ranges.range(EntityRange.TRACKING_ANIMAL));

      governor.onDeactivate();

      Assertions.assertEquals(MemoryRanges.defaults(), ranges.values);
      Assertions.assertTrue(ranges.tickInactiveVillagers());
    }
  }

  @Test
  void trackerGovernorClampsToTheFloorAndSkipsRangesTheServerLacks() throws ReflectiveOperationException {
    FeatureTrackerRangeGovernor governor = new FeatureTrackerRangeGovernor();
    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(J::isPrimaryThread).thenReturn(true);
      governor.onActivate();
      requestWorldState(governor, true);

      Assertions.assertEquals(36, ranges.range(EntityRange.TRACKING_ANIMAL));
      Assertions.assertEquals(36, ranges.range(EntityRange.TRACKING_MONSTER));
      Assertions.assertEquals(16, ranges.range(EntityRange.TRACKING_MISC));
      Assertions.assertEquals(77, ranges.range(EntityRange.TRACKING_DISPLAY));
      Assertions.assertEquals(48, ranges.range(EntityRange.TRACKING_OTHER));
      Assertions.assertEquals(48, ranges.range(EntityRange.TRACKING_PLAYER));
      Assertions.assertFalse(ranges.touchedItem);
      Assertions.assertTrue(ranges.tickInactiveVillagers());

      requestWorldState(governor, false);

      Assertions.assertEquals(MemoryRanges.defaults(), ranges.values);
      governor.onDeactivate();
    }
  }

  @Test
  void governorsDisableThemselvesWhereTheServerHasNoEntityRanges() {
    EntityActivityHost activity = Mockito.mock(EntityActivityHost.class);
    Mockito.when(activity.entityRanges(Mockito.any())).thenReturn(Optional.empty());
    Mockito.when(ReactPlatforms.get().entityActivityHost()).thenReturn(Optional.of(activity));
    FeatureActivationRangeGovernor governor = new FeatureActivationRangeGovernor();
    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(J::isPrimaryThread).thenReturn(true);
      governor.onActivate();
      requestWorldState(governor, true);
    } catch (ReflectiveOperationException failure) {
      Assertions.fail(failure);
    }

    Assertions.assertFalse(governor.isEnabled());
    Assertions.assertEquals(MemoryRanges.defaults(), ranges.values);
  }

  private static void requestWorldState(Object governor, boolean engaged) throws ReflectiveOperationException {
    Field field = governor.getClass().getDeclaredField("lifecycleGeneration");
    field.setAccessible(true);
    long generation = ((AtomicLong) field.get(governor)).get();
    Method method = governor.getClass().getDeclaredMethod("requestWorldState", boolean.class, long.class);
    method.setAccessible(true);
    method.invoke(governor, engaged, generation);
  }

  private static final class MemoryRanges implements EntityRanges {
    private final Map<EntityRange, Integer> values = defaults();
    private boolean tickInactiveVillagers = true;
    private boolean touchedItem;

    private static Map<EntityRange, Integer> defaults() {
      Map<EntityRange, Integer> values = new EnumMap<>(EntityRange.class);
      values.put(EntityRange.TRACKING_PLAYER, 48);
      values.put(EntityRange.TRACKING_MISC, 32);
      values.put(EntityRange.TRACKING_DISPLAY, 128);
      values.put(EntityRange.TRACKING_ANIMAL, 48);
      values.put(EntityRange.TRACKING_MONSTER, 48);
      values.put(EntityRange.TRACKING_OTHER, 64);
      values.put(EntityRange.ACTIVATION_ANIMAL, 32);
      values.put(EntityRange.ACTIVATION_MONSTER, 32);
      values.put(EntityRange.ACTIVATION_RAIDER, 64);
      values.put(EntityRange.ACTIVATION_MISC, 16);
      values.put(EntityRange.ACTIVATION_WATER, 16);
      values.put(EntityRange.ACTIVATION_VILLAGER, 32);
      values.put(EntityRange.ACTIVATION_FLYING_MONSTER, 32);
      return values;
    }

    @Override
    public Set<EntityRange> supportedRanges() {
      return EnumSet.complementOf(EnumSet.of(EntityRange.TRACKING_ITEM));
    }

    @Override
    public int range(EntityRange type) {
      if (type == EntityRange.TRACKING_ITEM) {
        touchedItem = true;
        throw new UnsupportedOperationException();
      }
      return values.get(type);
    }

    @Override
    public void range(EntityRange type, int blocks) {
      if (type == EntityRange.TRACKING_ITEM) {
        touchedItem = true;
        throw new UnsupportedOperationException();
      }
      values.put(type, blocks);
    }

    @Override
    public boolean tickInactiveVillagers() {
      return tickInactiveVillagers;
    }

    @Override
    public void tickInactiveVillagers(boolean enabled) {
      tickInactiveVillagers = enabled;
    }
  }
}
