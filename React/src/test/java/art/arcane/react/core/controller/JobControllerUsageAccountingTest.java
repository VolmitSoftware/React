package art.arcane.react.core.controller;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

/** Budget accounting driven by the controller's PrecisionStopwatch and RollingSequences. */
class JobControllerUsageAccountingTest {

  @Test
  void firstBusyTickFillsTheUsageWindowAndChargesTheOverrun() {
    JobController controller = busyController();
    controller.queue(() -> pause(60L));

    execute(controller, 1);

    double used = controller.getUsage().getAverage();
    Assertions.assertTrue(used >= 58D && used < 5_000D, "usage " + used);
    Assertions.assertEquals(used, controller.getCostPerJob());
    Assertions.assertEquals(used - 25D, controller.getOverBudget(), 1.0E-9D);
    Assertions.assertEquals(used / 25D, controller.getUsageCyclePercent().getAverage(), 1.0E-9D);
    Assertions.assertEquals(25.05D, controller.getCurrentComputeTarget(), 1.0E-9D);
    Assertions.assertEquals(0, controller.getQueueSize());
  }

  @Test
  void overBudgetTickSkipsJobsAndRepaysOneBudget() {
    JobController controller = busyController();
    AtomicInteger ran = new AtomicInteger();
    controller.queue(() -> pause(60L));
    execute(controller, 1);
    double used = controller.getUsage().getAverage();
    double overBudget = controller.getOverBudget();
    controller.queue(ran::incrementAndGet);

    execute(controller, 1);

    Assertions.assertEquals(0, ran.get());
    Assertions.assertEquals(1, controller.getQueueSize());
    Assertions.assertEquals(overBudget - 30D, controller.getOverBudget(), 1.0E-9D);
    Assertions.assertEquals(used * 19D / 20D, controller.getUsage().getAverage(), 1.0E-9D);
  }

  @Test
  void queuedJobWaitsUntilTheOverrunIsRepaid() {
    JobController controller = new JobController();
    AtomicInteger ran = new AtomicInteger();
    controller.setOverBudget(2.5D);
    controller.queue(ran::incrementAndGet);

    execute(controller, 2);
    Assertions.assertEquals(0, ran.get());
    Assertions.assertEquals(0.5D, controller.getOverBudget(), 1.0E-9D);

    for (int attempt = 0; attempt < 200 && ran.get() == 0; attempt++) {
      execute(controller, 1);
    }
    Assertions.assertEquals(1, ran.get());
    Assertions.assertEquals(0, controller.getQueueSize());
  }

  @Test
  void stopwatchBudgetStopsTheDrainBeforeTheQueueIsEmpty() {
    JobController controller = new JobController();
    controller.setCurrentComputeTarget(0.5D);
    AtomicInteger ran = new AtomicInteger();
    Runnable job = ran::incrementAndGet;
    int queued = 500_000;
    for (int index = 0; index < queued; index++) {
      controller.queue(job);
    }

    execute(controller, 1);

    Assertions.assertTrue(ran.get() < queued, "ran " + ran.get());
    Assertions.assertEquals(queued - ran.get(), controller.getQueueSize());
    Assertions.assertTrue(controller.getUsage().getAverage() >= 0.5D, "usage " + controller.getUsage().getAverage());
  }

  @Test
  void lowUtilizationShrinksTheComputeTarget() {
    JobController controller = new JobController();
    controller.setMaxComputeTime(1_000D);
    controller.setCurrentComputeTarget(1_000D);
    controller.queue(() -> {
    });

    execute(controller, 1);

    Assertions.assertEquals(990.0001D, controller.getCurrentComputeTarget(), 1.0E-9D);
    Assertions.assertEquals(0D, controller.getOverBudget());
  }

  private static JobController busyController() {
    JobController controller = new JobController();
    controller.setMaxComputeTime(30D);
    controller.setCurrentComputeTarget(25D);
    return controller;
  }

  private static void execute(JobController controller, int times) {
    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(Mockito.mock(PluginManager.class));
      for (int index = 0; index < times; index++) {
        controller.execute();
      }
    }
  }

  private static void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }
}
