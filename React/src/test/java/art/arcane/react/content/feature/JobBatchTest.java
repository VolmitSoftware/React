package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class JobBatchTest {
  @Test
  void unitCapBoundsEachRunAndEveryUnitRunsOnceThroughOneQueuedJob() {
    List<Runnable> jobs = new ArrayList<>();
    int[] runs = new int[10];
    AtomicInteger completions = new AtomicInteger();

    try (MockedStatic<J> scheduling = queueInto(jobs)) {
      JobBatch batch = new JobBatch(new JobBatch.Limits(4, Long.MAX_VALUE), completions::incrementAndGet);
      for (int index = 0; index < runs.length; index++) {
        int unit = index;
        batch.submit(() -> runs[unit]++);
      }
      Assertions.assertTrue(jobs.isEmpty());
      batch.seal();

      List<Integer> unitsPerRun = new ArrayList<>();
      while (!jobs.isEmpty()) {
        Assertions.assertEquals(1, jobs.size());
        int before = sum(runs);
        jobs.removeFirst().run();
        unitsPerRun.add(sum(runs) - before);
      }

      Assertions.assertEquals(List.of(4, 4, 2), unitsPerRun);
    }

    for (int count : runs) {
      Assertions.assertEquals(1, count);
    }
    Assertions.assertEquals(1, completions.get());
  }

  @Test
  void timeSliceBoundsEachRunAndRequeuesTheRemainder() {
    List<Runnable> jobs = new ArrayList<>();
    AtomicInteger ran = new AtomicInteger();
    AtomicInteger completions = new AtomicInteger();

    try (MockedStatic<J> scheduling = queueInto(jobs)) {
      JobBatch batch = new JobBatch(new JobBatch.Limits(64, 100_000L), completions::incrementAndGet);
      for (int index = 0; index < 10; index++) {
        batch.submit(() -> {
          spin(60_000L);
          ran.incrementAndGet();
        });
      }
      batch.seal();

      int runCount = 0;
      while (!jobs.isEmpty()) {
        Assertions.assertEquals(1, jobs.size());
        int before = ran.get();
        jobs.removeFirst().run();
        int executed = ran.get() - before;
        Assertions.assertTrue(executed >= 1 && executed <= 2, "units in one run: " + executed);
        runCount++;
      }
      Assertions.assertTrue(runCount >= 5, "runs: " + runCount);
    }

    Assertions.assertEquals(10, ran.get());
    Assertions.assertEquals(1, completions.get());
  }

  @Test
  void failingUnitDoesNotStopTheRestOfTheBatch() {
    List<Runnable> jobs = new ArrayList<>();
    AtomicInteger ran = new AtomicInteger();
    AtomicInteger completions = new AtomicInteger();

    try (MockedStatic<J> scheduling = queueInto(jobs);
         MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      JobBatch batch = new JobBatch(new JobBatch.Limits(64, Long.MAX_VALUE), completions::incrementAndGet);
      batch.submit(ran::incrementAndGet);
      batch.submit(() -> {
        throw new IllegalStateException("unit failure");
      });
      batch.submit(ran::incrementAndGet);
      batch.seal();
      while (!jobs.isEmpty()) {
        jobs.removeFirst().run();
      }
      react.verify(() -> React.reportError(Mockito.anyString(), Mockito.any(IllegalStateException.class)));
    }

    Assertions.assertEquals(2, ran.get());
    Assertions.assertEquals(1, completions.get());
  }

  @Test
  void emptyBatchCompletesAtSealWithoutQueueingAJob() {
    List<Runnable> jobs = new ArrayList<>();
    AtomicInteger completions = new AtomicInteger();

    try (MockedStatic<J> scheduling = queueInto(jobs)) {
      new JobBatch(new JobBatch.Limits(64, Long.MAX_VALUE), completions::incrementAndGet).seal();
    }

    Assertions.assertTrue(jobs.isEmpty());
    Assertions.assertEquals(1, completions.get());
  }

  @Test
  void submitAfterSealIsRejected() {
    List<Runnable> jobs = new ArrayList<>();

    try (MockedStatic<J> scheduling = queueInto(jobs)) {
      JobBatch batch = new JobBatch(new JobBatch.Limits(64, Long.MAX_VALUE), () -> { });
      batch.submit(() -> { });
      batch.seal();

      Assertions.assertThrows(IllegalStateException.class, () -> batch.submit(() -> { }));
    }
  }

  @Test
  void tickBudgetLimitsTakeAQuarterOfTheJobControllerTickBudgetCappedAtTheSliceCeiling() {
    Assertions.assertEquals(new JobBatch.Limits(64, 250_000L), JobBatch.Limits.forTickBudget(1D));
    Assertions.assertEquals(new JobBatch.Limits(64, 100_000L), JobBatch.Limits.forTickBudget(0.4D));
    Assertions.assertEquals(new JobBatch.Limits(64, 12_500L), JobBatch.Limits.forTickBudget(0.05D));
    Assertions.assertEquals(new JobBatch.Limits(64, 250_000L), JobBatch.Limits.forTickBudget(8D));
    Assertions.assertEquals(new JobBatch.Limits(64, 250_000L), JobBatch.Limits.forTickBudget(Double.NaN));
    Assertions.assertEquals(new JobBatch.Limits(64, 250_000L), JobBatch.Limits.forTickBudget(0D));
  }

  @Test
  void limitsClampToAtLeastOneUnitAndOneNanosecond() {
    JobBatch.Limits limits = new JobBatch.Limits(0, -5L);

    Assertions.assertEquals(1, limits.maxUnitsPerRun());
    Assertions.assertEquals(1L, limits.maxNanosPerRun());
  }

  private static MockedStatic<J> queueInto(List<Runnable> jobs) {
    MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
    scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
      jobs.add(invocation.getArgument(0));
      return null;
    });
    return scheduling;
  }

  private static int sum(int[] values) {
    int total = 0;
    for (int value : values) {
      total += value;
    }
    return total;
  }

  private static void spin(long nanos) {
    long until = System.nanoTime() + nanos;
    while (System.nanoTime() < until) {
      Thread.onSpinWait();
    }
  }
}
