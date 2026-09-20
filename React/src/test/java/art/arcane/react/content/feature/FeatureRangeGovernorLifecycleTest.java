package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.monitor.NativeWorldAccess;
import art.arcane.volmlib.nativelib.monitor.EntityRangeSettings;
import art.arcane.volmlib.nativelib.monitor.EntityRangeSettings.Range;
import java.util.Optional;
import java.util.Set;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

class FeatureRangeGovernorLifecycleTest {
  @Test
  void inactiveGovernorsDoNotSampleOrQueueMutations() {
    FeatureActivationRangeGovernor activation = new FeatureActivationRangeGovernor();
    FeatureTrackerRangeGovernor tracker = new FeatureTrackerRangeGovernor();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      activation.onTick();
      tracker.onTick();

      react.verify(() -> React.sampler(SamplerTickTime.ID), Mockito.never());
      scheduling.verifyNoInteractions();
    }
  }

  @Test
  void activationGovernorReleasesSynchronouslyOnPrimaryThread() throws ReflectiveOperationException {
    ActivationConfig config = new ActivationConfig();
    World world = world(config);
    FeatureActivationRangeGovernor feature = new FeatureActivationRangeGovernor();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = worlds(world);
         MockedStatic<NativeAdapters> natives = nativeSettings(world, config)) {
      scheduling.when(J::isPrimaryThread).thenReturn(true);
      feature.onActivate();
      requestWorldState(feature, true);

      Assertions.assertEquals(32, config.animalActivationRange);
      Assertions.assertFalse(config.tickInactiveVillagers);
      feature.onDeactivate();

      Assertions.assertEquals(64, config.animalActivationRange);
      Assertions.assertTrue(config.tickInactiveVillagers);
      scheduling.verify(() -> J.sync(Mockito.any(Runnable.class)), Mockito.never());
    }
  }

  @Test
  void trackerGovernorReleasesSynchronouslyOnPrimaryThread() throws ReflectiveOperationException {
    TrackerConfig config = new TrackerConfig();
    World world = world(config);
    FeatureTrackerRangeGovernor feature = new FeatureTrackerRangeGovernor();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = worlds(world);
         MockedStatic<NativeAdapters> natives = nativeSettings(world, config)) {
      scheduling.when(J::isPrimaryThread).thenReturn(true);
      feature.onActivate();
      requestWorldState(feature, true);

      Assertions.assertEquals(32, config.itemTrackingRange);
      feature.onDeactivate();

      Assertions.assertEquals(64, config.itemTrackingRange);
      scheduling.verify(() -> J.sync(Mockito.any(Runnable.class)), Mockito.never());
    }
  }

  @Test
  void staleActivationGovernorEngageCannotCrossReload() throws ReflectiveOperationException {
    ActivationConfig config = new ActivationConfig();
    World world = world(config);
    FeatureActivationRangeGovernor feature = new FeatureActivationRangeGovernor();
    AtomicBoolean primary = new AtomicBoolean(false);
    List<Runnable> queued = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = worlds(world);
         MockedStatic<NativeAdapters> natives = nativeSettings(world, config)) {
      scheduling.when(J::isPrimaryThread).thenAnswer(invocation -> primary.get());
      scheduling.when(() -> J.sync(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        queued.add(invocation.getArgument(0));
        return null;
      });
      feature.onActivate();
      requestWorldState(feature, true);
      Assertions.assertEquals(1, queued.size());

      primary.set(true);
      feature.onDeactivate();
      feature.onActivate();
      queued.getFirst().run();

      Assertions.assertEquals(64, config.animalActivationRange);
      Assertions.assertTrue(config.tickInactiveVillagers);
    }
  }

  @Test
  void staleTrackerGovernorEngageCannotCrossReload() throws ReflectiveOperationException {
    TrackerConfig config = new TrackerConfig();
    World world = world(config);
    FeatureTrackerRangeGovernor feature = new FeatureTrackerRangeGovernor();
    AtomicBoolean primary = new AtomicBoolean(false);
    List<Runnable> queued = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = worlds(world);
         MockedStatic<NativeAdapters> natives = nativeSettings(world, config)) {
      scheduling.when(J::isPrimaryThread).thenAnswer(invocation -> primary.get());
      scheduling.when(() -> J.sync(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        queued.add(invocation.getArgument(0));
        return null;
      });
      feature.onActivate();
      requestWorldState(feature, true);
      Assertions.assertEquals(1, queued.size());

      primary.set(true);
      feature.onDeactivate();
      feature.onActivate();
      queued.getFirst().run();

      Assertions.assertEquals(64, config.itemTrackingRange);
    }
  }

  @Test
  void queuedActivationGovernorReleaseSurvivesReload() throws ReflectiveOperationException {
    ActivationConfig config = new ActivationConfig();
    World world = world(config);
    FeatureActivationRangeGovernor feature = new FeatureActivationRangeGovernor();
    AtomicBoolean primary = new AtomicBoolean(true);
    List<Runnable> queued = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = worlds(world);
         MockedStatic<NativeAdapters> natives = nativeSettings(world, config)) {
      scheduling.when(J::isPrimaryThread).thenAnswer(invocation -> primary.get());
      scheduling.when(() -> J.sync(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        queued.add(invocation.getArgument(0));
        return null;
      });
      feature.onActivate();
      requestWorldState(feature, true);
      Assertions.assertEquals(32, config.animalActivationRange);

      primary.set(false);
      feature.onDeactivate();
      Assertions.assertEquals(1, queued.size());
      feature.onActivate();
      queued.getFirst().run();

      Assertions.assertEquals(64, config.animalActivationRange);
      Assertions.assertTrue(config.tickInactiveVillagers);
    }
  }

  @Test
  void queuedTrackerGovernorReleaseSurvivesReload() throws ReflectiveOperationException {
    TrackerConfig config = new TrackerConfig();
    World world = world(config);
    FeatureTrackerRangeGovernor feature = new FeatureTrackerRangeGovernor();
    AtomicBoolean primary = new AtomicBoolean(true);
    List<Runnable> queued = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = worlds(world);
         MockedStatic<NativeAdapters> natives = nativeSettings(world, config)) {
      scheduling.when(J::isPrimaryThread).thenAnswer(invocation -> primary.get());
      scheduling.when(() -> J.sync(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        queued.add(invocation.getArgument(0));
        return null;
      });
      feature.onActivate();
      requestWorldState(feature, true);
      Assertions.assertEquals(32, config.itemTrackingRange);

      primary.set(false);
      feature.onDeactivate();
      Assertions.assertEquals(1, queued.size());
      feature.onActivate();
      queued.getFirst().run();

      Assertions.assertEquals(64, config.itemTrackingRange);
    }
  }

  @Test
  void newActivationEngagesBeforeStaleReleaseWithoutLosingOriginalBaseline()
      throws ReflectiveOperationException {
    ActivationConfig config = new ActivationConfig();
    World world = world(config);
    FeatureActivationRangeGovernor feature = new FeatureActivationRangeGovernor();
    AtomicBoolean primary = new AtomicBoolean(true);
    List<Runnable> queued = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = worlds(world);
         MockedStatic<NativeAdapters> natives = nativeSettings(world, config)) {
      scheduling.when(J::isPrimaryThread).thenAnswer(invocation -> primary.get());
      scheduling.when(() -> J.sync(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        queued.add(invocation.getArgument(0));
        return null;
      });
      feature.onActivate();
      requestWorldState(feature, true);
      Assertions.assertEquals(32, config.animalActivationRange);
      Assertions.assertFalse(config.tickInactiveVillagers);

      primary.set(false);
      feature.onDeactivate();
      feature.onActivate();
      primary.set(true);
      requestWorldState(feature, true);

      Assertions.assertEquals(32, config.animalActivationRange);
      Assertions.assertFalse(config.tickInactiveVillagers);
      queued.getFirst().run();
      Assertions.assertEquals(32, config.animalActivationRange);
      Assertions.assertFalse(config.tickInactiveVillagers);

      feature.onDeactivate();
      Assertions.assertEquals(64, config.animalActivationRange);
      Assertions.assertTrue(config.tickInactiveVillagers);
    }
  }

  @Test
  void newTrackerGenerationEngagesBeforeStaleReleaseWithoutLosingOriginalBaseline()
      throws ReflectiveOperationException {
    TrackerConfig config = new TrackerConfig();
    World world = world(config);
    FeatureTrackerRangeGovernor feature = new FeatureTrackerRangeGovernor();
    AtomicBoolean primary = new AtomicBoolean(true);
    List<Runnable> queued = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = worlds(world);
         MockedStatic<NativeAdapters> natives = nativeSettings(world, config)) {
      scheduling.when(J::isPrimaryThread).thenAnswer(invocation -> primary.get());
      scheduling.when(() -> J.sync(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        queued.add(invocation.getArgument(0));
        return null;
      });
      feature.onActivate();
      requestWorldState(feature, true);
      Assertions.assertEquals(32, config.itemTrackingRange);

      primary.set(false);
      feature.onDeactivate();
      feature.onActivate();
      primary.set(true);
      requestWorldState(feature, true);

      Assertions.assertEquals(32, config.itemTrackingRange);
      queued.getFirst().run();
      Assertions.assertEquals(32, config.itemTrackingRange);

      feature.onDeactivate();
      Assertions.assertEquals(64, config.itemTrackingRange);
    }
  }

  private static void requestWorldState(Object governor, boolean engaged) throws ReflectiveOperationException {
    Method method = governor.getClass().getDeclaredMethod("requestWorldState", boolean.class, long.class);
    method.setAccessible(true);
    method.invoke(governor, engaged, lifecycleGeneration(governor));
  }

  private static long lifecycleGeneration(Object governor) throws ReflectiveOperationException {
    Field field = governor.getClass().getDeclaredField("lifecycleGeneration");
    field.setAccessible(true);
    AtomicLong generation = (AtomicLong) field.get(governor);
    return generation.get();
  }

  private static MockedStatic<Bukkit> worlds(World world) {
    MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
    bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
    bukkit.when(() -> Bukkit.getWorld(world.getUID())).thenReturn(world);
    return bukkit;
  }

  private static World world(Object config) {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    return world;
  }

  private static MockedStatic<NativeAdapters> nativeSettings(World world, Object config) {
    NativeWorldAccess access = Mockito.mock(NativeWorldAccess.class);
    EntityRangeSettings settings = Mockito.mock(EntityRangeSettings.class);
    Mockito.when(access.entityRanges(world)).thenReturn(settings);
    if (config instanceof ActivationConfig activation) {
      Mockito.when(settings.supportedRanges()).thenReturn(Set.of(Range.ACTIVATION_ANIMAL));
      Mockito.when(settings.range(Range.ACTIVATION_ANIMAL)).thenAnswer(invocation -> activation.animalActivationRange);
      Mockito.doAnswer(invocation -> {
        activation.animalActivationRange = invocation.getArgument(1);
        return null;
      }).when(settings).range(Mockito.eq(Range.ACTIVATION_ANIMAL), Mockito.anyInt());
      Mockito.when(settings.tickInactiveVillagers()).thenAnswer(invocation -> activation.tickInactiveVillagers);
      Mockito.doAnswer(invocation -> {
        activation.tickInactiveVillagers = invocation.getArgument(0);
        return null;
      }).when(settings).tickInactiveVillagers(Mockito.anyBoolean());
    } else if (config instanceof TrackerConfig tracker) {
      Mockito.when(settings.supportedRanges()).thenReturn(Set.of(Range.TRACKING_ITEM));
      Mockito.when(settings.range(Range.TRACKING_ITEM)).thenAnswer(invocation -> tracker.itemTrackingRange);
      Mockito.doAnswer(invocation -> {
        tracker.itemTrackingRange = invocation.getArgument(1);
        return null;
      }).when(settings).range(Mockito.eq(Range.TRACKING_ITEM), Mockito.anyInt());
    }
    MockedStatic<NativeAdapters> natives = Mockito.mockStatic(NativeAdapters.class);
    natives.when(() -> NativeAdapters.find(NativeWorldAccess.class)).thenReturn(Optional.of(access));
    return natives;
  }

  private static final class ActivationConfig {
    private int animalActivationRange = 64;
    private boolean tickInactiveVillagers = true;
  }

  private static final class TrackerConfig {
    private int itemTrackingRange = 64;
  }
}
