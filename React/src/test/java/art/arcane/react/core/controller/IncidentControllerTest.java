package art.arcane.react.core.controller;

import art.arcane.react.core.incident.IncidentRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IncidentControllerTest {
  @Test
  void persistsBoundedRecordsAndLoadsNewestFirst(@TempDir File directory) {
    File storage = new File(directory, "incidents.json");
    IncidentController writer = new IncidentController(storage, 3);
    writer.start();
    writer.record(record("one", 1L));
    writer.record(record("two", 2L));
    writer.record(record("three", 3L));
    writer.record(record("four", 4L));
    writer.stop();

    IncidentController reader = new IncidentController(storage, 3);
    reader.start();
    List<IncidentRecord> records = reader.recent(10);
    reader.stop();

    assertEquals(3, records.size());
    assertEquals("four", records.get(0).id());
    assertEquals("three", records.get(1).id());
    assertEquals("two", records.get(2).id());
  }

  @Test
  void concurrentRecordsStayBounded(@TempDir File directory) throws Exception {
    IncidentController controller = new IncidentController(new File(directory, "incidents.json"), 64);
    controller.start();
    int workers = 4;
    int recordsPerWorker = 100;
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    List<Runnable> tasks = new ArrayList<>(workers);
    for (int worker = 0; worker < workers; worker++) {
      int workerId = worker;
      tasks.add(() -> {
        ready.countDown();
        try {
          start.await();
          for (int index = 0; index < recordsPerWorker; index++) {
            controller.record(record(workerId + "-" + index, index));
          }
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt();
        }
      });
    }
    for (Runnable task : tasks) {
      executor.execute(task);
    }
    assertTrue(ready.await(5L, TimeUnit.SECONDS));
    start.countDown();
    executor.shutdown();
    assertTrue(executor.awaitTermination(10L, TimeUnit.SECONDS));

    assertEquals(64, controller.size());
    assertEquals(64, controller.recent(1000).size());
    controller.stop();
  }

  private IncidentRecord record(String id, long occurredAtMs) {
    return new IncidentRecord(
        id,
        "incident",
        "TEST",
        "STARTED",
        "INFO",
        occurredAtMs,
        occurredAtMs,
        "test",
        "Test",
        "Summary",
        "Cause",
        null,
        List.of(),
        List.of(),
        Map.of()
    );
  }
}
