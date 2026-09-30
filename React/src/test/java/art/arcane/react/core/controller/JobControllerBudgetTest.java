package art.arcane.react.core.controller;

import art.arcane.react.React;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

class JobControllerBudgetTest {

  @Test
  void idleTicksDecayTheOverBudgetToZero() {
    JobController controller = new JobController();
    controller.setMaxComputeTime(1D);
    controller.setOverBudget(3.5D);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(Mockito.mock(PluginManager.class));

      for (int i = 0; i < 10; i++) {
        controller.execute();
      }
    }

    Assertions.assertEquals(0D, controller.getOverBudget(), 1.0E-9D);
  }

  @Test
  void idleTicksDrainTheSyncTickUsageAverage() {
    JobController controller = new JobController();
    for (int i = 0; i < 20; i++) {
      controller.getUsage().put(4D);
    }

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(Mockito.mock(PluginManager.class));

      for (int i = 0; i < 40; i++) {
        controller.execute();
      }
    }

    Assertions.assertEquals(0D, controller.getUsage().getAverage(), 1.0E-9D);
  }

  @Test
  void loadedConfigurationIsClampedToWorkableBounds() {
    JobController controller = new JobController();
    JobController loaded = new JobController();
    loaded.setMaxComputeTime(0D);
    loaded.setHighUtilizationThresholdPercent(4D);
    loaded.setLowUtilizationThresholdPercent(-2D);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      Assertions.assertTrue(controller.applyConfigurationSnapshot(loaded));
    }

    Assertions.assertEquals(0.05D, controller.getMaxComputeTime(), 1.0E-9D);
    Assertions.assertEquals(1D, controller.getHighUtilizationThresholdPercent(), 1.0E-9D);
    Assertions.assertEquals(0D, controller.getLowUtilizationThresholdPercent(), 1.0E-9D);
  }

  @Test
  void nonFiniteLoadedConfigurationFallsBackToDefaults() {
    JobController controller = new JobController();
    JobController loaded = new JobController();
    loaded.setMaxComputeTime(Double.NaN);
    loaded.setCurrentComputeTarget(Double.NaN);
    loaded.setHighUtilizationThresholdPercent(Double.NaN);
    loaded.setLowUtilizationThresholdPercent(Double.POSITIVE_INFINITY);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      Assertions.assertTrue(controller.applyConfigurationSnapshot(loaded));
    }

    Assertions.assertEquals(1D, controller.getMaxComputeTime(), 1.0E-9D);
    Assertions.assertEquals(0.01D, controller.getCurrentComputeTarget(), 1.0E-9D);
    Assertions.assertEquals(0.75D, controller.getHighUtilizationThresholdPercent(), 1.0E-9D);
    Assertions.assertEquals(0.25D, controller.getLowUtilizationThresholdPercent(), 1.0E-9D);
  }

  @Test
  void notANumberConfiguredComputeTimeStillDrainsQueuedJobs() {
    JobController controller = new JobController();
    JobController loaded = new JobController();
    loaded.setMaxComputeTime(Double.NaN);
    AtomicInteger ran = new AtomicInteger();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(Mockito.mock(PluginManager.class));
      controller.applyConfigurationSnapshot(loaded);

      for (int i = 0; i < 5; i++) {
        controller.queue(ran::incrementAndGet);
      }

      for (int i = 0; i < 1000 && ran.get() < 5; i++) {
        controller.execute();
      }
    }

    Assertions.assertEquals(5, ran.get());
  }

  @Test
  void zeroConfiguredComputeTimeStillDrainsQueuedJobs() {
    JobController controller = new JobController();
    JobController loaded = new JobController();
    loaded.setMaxComputeTime(0D);
    AtomicInteger ran = new AtomicInteger();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(Mockito.mock(PluginManager.class));
      controller.applyConfigurationSnapshot(loaded);

      for (int i = 0; i < 5; i++) {
        controller.queue(ran::incrementAndGet);
      }

      for (int i = 0; i < 1000 && ran.get() < 5; i++) {
        controller.execute();
      }
    }

    Assertions.assertEquals(5, ran.get());
  }
}
