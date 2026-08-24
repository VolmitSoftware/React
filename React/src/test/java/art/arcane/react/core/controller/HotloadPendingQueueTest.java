package art.arcane.react.core.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotloadPendingQueueTest {
  private static final long THREE_SECONDS = TimeUnit.SECONDS.toNanos(3L);

  @TempDir
  Path temporaryDirectory;

  @Test
  void coalescesOneTrailingLatestStateDrainAfterCooldown() {
    AtomicLong clock = new AtomicLong();
    HotloadPendingQueue queue = new HotloadPendingQueue(THREE_SECONDS, THREE_SECONDS, clock::get);
    Path config = temporaryDirectory.resolve("feature.toml");

    queue.enqueue(config, true);
    assertEquals(
        List.of(new HotloadPendingQueue.ReadyChange(config.toAbsolutePath().normalize(), true)),
        queue.beginDrain()
    );

    queue.enqueue(temporaryDirectory.resolve(".").resolve("feature.toml"), true);
    queue.enqueue(config, true);
    queue.finishDrain();

    clock.set(THREE_SECONDS - 1L);
    assertTrue(queue.beginDrain().isEmpty());
    assertEquals(1, queue.pendingCount());

    clock.set(THREE_SECONDS);
    assertEquals(
        List.of(new HotloadPendingQueue.ReadyChange(config.toAbsolutePath().normalize(), true)),
        queue.beginDrain()
    );
    queue.finishDrain();
    assertEquals(0, queue.pendingCount());
  }

  @Test
  void anchorsTrailingRetryCooldownToLongRunningDrainCompletion() {
    AtomicLong clock = new AtomicLong();
    HotloadPendingQueue queue = new HotloadPendingQueue(THREE_SECONDS, THREE_SECONDS, clock::get);
    Path config = temporaryDirectory.resolve("feature.toml");

    queue.enqueue(config, true);
    assertEquals(
        List.of(new HotloadPendingQueue.ReadyChange(config.toAbsolutePath().normalize(), true)),
        queue.beginDrain()
    );

    clock.set(TimeUnit.SECONDS.toNanos(10L));
    queue.enqueue(config, false);
    queue.enqueue(config, true);
    assertTrue(queue.beginDrain().isEmpty());
    queue.finishDrain();

    clock.set(TimeUnit.SECONDS.toNanos(13L) - 1L);
    assertTrue(queue.beginDrain().isEmpty());
    assertEquals(1, queue.pendingCount());

    clock.set(TimeUnit.SECONDS.toNanos(13L));
    assertEquals(
        List.of(new HotloadPendingQueue.ReadyChange(config.toAbsolutePath().normalize(), true)),
        queue.beginDrain()
    );
    queue.finishDrain();
  }

  @Test
  void clearResetsCompletionCooldownAndIgnoresAStaleFinish() {
    AtomicLong clock = new AtomicLong();
    HotloadPendingQueue queue = new HotloadPendingQueue(THREE_SECONDS, THREE_SECONDS, clock::get);
    Path first = temporaryDirectory.resolve("first.toml");
    Path second = temporaryDirectory.resolve("second.toml");

    queue.enqueue(first, true);
    assertFalse(queue.beginDrain().isEmpty());
    queue.finishDrain();

    clock.set(TimeUnit.SECONDS.toNanos(1L));
    queue.enqueue(second, true);
    assertTrue(queue.beginDrain().isEmpty());

    queue.clear();
    queue.finishDrain();
    queue.enqueue(second, true);
    assertEquals(
        List.of(new HotloadPendingQueue.ReadyChange(second.toAbsolutePath().normalize(), true)),
        queue.beginDrain()
    );
    queue.finishDrain();
  }

  @Test
  void missingTargetWaitsForTombstoneGrace() {
    AtomicLong clock = new AtomicLong();
    HotloadPendingQueue queue = new HotloadPendingQueue(THREE_SECONDS, THREE_SECONDS, clock::get);
    Path config = temporaryDirectory.resolve("react.toml");

    queue.enqueue(config, false);
    assertTrue(queue.beginDrain().isEmpty());

    clock.set(THREE_SECONDS - 1L);
    assertTrue(queue.beginDrain().isEmpty());

    clock.set(THREE_SECONDS);
    assertEquals(
        List.of(new HotloadPendingQueue.ReadyChange(config.toAbsolutePath().normalize(), false)),
        queue.beginDrain()
    );
    queue.finishDrain();
  }

  @Test
  void reappearingTargetCancelsTombstoneDelay() {
    AtomicLong clock = new AtomicLong();
    HotloadPendingQueue queue = new HotloadPendingQueue(THREE_SECONDS, THREE_SECONDS, clock::get);
    Path config = temporaryDirectory.resolve("react.toml");

    queue.enqueue(config, false);
    clock.set(TimeUnit.SECONDS.toNanos(1L));
    queue.enqueue(config, true);

    assertEquals(
        List.of(new HotloadPendingQueue.ReadyChange(config.toAbsolutePath().normalize(), true)),
        queue.beginDrain()
    );
    queue.finishDrain();
  }

  @Test
  void disappearanceDuringApplyStartsANewTombstoneGrace() {
    AtomicLong clock = new AtomicLong();
    HotloadPendingQueue queue = new HotloadPendingQueue(THREE_SECONDS, THREE_SECONDS, clock::get);
    Path config = temporaryDirectory.resolve("react.toml");

    queue.enqueue(config, true);
    assertEquals(
        List.of(new HotloadPendingQueue.ReadyChange(config.toAbsolutePath().normalize(), true)),
        queue.beginDrain()
    );

    clock.set(TimeUnit.SECONDS.toNanos(1L));
    queue.enqueue(config, false);
    queue.finishDrain();

    clock.set(THREE_SECONDS);
    assertTrue(queue.beginDrain().isEmpty());

    clock.set(TimeUnit.SECONDS.toNanos(4L));
    assertEquals(
        List.of(new HotloadPendingQueue.ReadyChange(config.toAbsolutePath().normalize(), false)),
        queue.beginDrain()
    );
    queue.finishDrain();
  }

  @Test
  void recognizesCommonTemporaryArtifacts() {
    assertTrue(HotloadController.isTemporaryArtifactName(".react.toml"));
    assertTrue(HotloadController.isTemporaryArtifactName("config.tmp.toml"));
    assertTrue(HotloadController.isTemporaryArtifactName("react.toml.part"));
    assertTrue(HotloadController.isTemporaryArtifactName("react.toml.filepart"));
    assertTrue(HotloadController.isTemporaryArtifactName("react.toml.___jb_tmp___"));
    assertTrue(HotloadController.isTemporaryArtifactName("react.toml~"));
    assertFalse(HotloadController.isTemporaryArtifactName("temperature.toml"));
  }
}
