package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class RecurringScanSingleFlightTest {
  static {
    if (React.instance == null) {
      React react = Mockito.mock(React.class);
      Mockito.when(react.getName()).thenReturn("react");
      Mockito.when(react.namespace()).thenReturn("react");
      React.instance = react;
    }
  }

  @Test
  void adaptiveSleepKeepsOnlyOnePendingScan() {
    FeatureAdaptiveEntitySleep feature = new FeatureAdaptiveEntitySleep();
    feature.onActivate();
    assertSinglePendingFeatureScan(feature::onTick);
    feature.onDeactivate();
  }

  @Test
  void dynamicActivationRangeKeepsOnlyOnePendingScan() {
    FeatureDynamicActivationRange feature = new FeatureDynamicActivationRange();
    feature.onActivate();
    assertSinglePendingFeatureScan(feature::onTick);
    feature.onDeactivate();
  }

  @Test
  void pathfinderBudgetKeepsOnlyOnePendingScan() throws Exception {
    FeaturePathfinderBudget feature = new FeaturePathfinderBudget();
    Field bridgesAvailable = FeaturePathfinderBudget.class.getDeclaredField("bridgesAvailable");
    bridgesAvailable.setAccessible(true);
    bridgesAvailable.setBoolean(feature, true);
    Field active = FeaturePathfinderBudget.class.getDeclaredField("active");
    active.setAccessible(true);
    active.setBoolean(feature, true);
    assertSinglePendingFeatureScan(feature::onTick);
  }

  @Test
  void itemBackpressureKeepsOnlyOnePendingScan() {
    FeatureItemBackpressure feature = new FeatureItemBackpressure();
    feature.onActivate();
    assertSinglePendingFeatureScan(feature::onTick);
  }

  @Test
  void hopperIndexKeepsOnePendingJobPerReconciliationType() {
    FeatureHopperItemIndex feature = new FeatureHopperItemIndex();
    List<Runnable> queued = new ArrayList<>();

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      captureQueuedJobs(scheduling, queued);

      feature.onTick();
      feature.onTick();

      Assertions.assertEquals(2, queued.size());
      queued.forEach(Runnable::run);
      feature.onTick();
      Assertions.assertEquals(4, queued.size());
    }
  }

  @Test
  void hopperIndexFoliaReconciliationAvoidsGlobalEntityLookups() {
    FeatureHopperItemIndex feature = new FeatureHopperItemIndex();

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);

      feature.onActivate();
      feature.onTick();

      scheduling.verify(() -> J.s(Mockito.any(Runnable.class)), Mockito.never());
      bukkit.verify(() -> Bukkit.getEntity(Mockito.any(UUID.class)), Mockito.never());
      feature.onDeactivate();
    }
  }

  private static void assertSinglePendingFeatureScan(Runnable tick) {
    Sampler sampler = Mockito.mock(Sampler.class);
    List<Runnable> queued = new ArrayList<>();
    Mockito.when(sampler.sample()).thenReturn(40D);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      captureQueuedJobs(scheduling, queued);
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of());

      tick.run();
      tick.run();

      Assertions.assertEquals(1, queued.size());
      queued.getFirst().run();
      tick.run();
      Assertions.assertEquals(2, queued.size());
    }
  }

  private static void captureQueuedJobs(MockedStatic<J> scheduling, List<Runnable> queued) {
    scheduling.when(J::isFoliaThreading).thenReturn(false);
    scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
      queued.add(invocation.getArgument(0));
      return null;
    });
  }
}
