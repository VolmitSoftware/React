package art.arcane.react.core.controller;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.history.HistoryPoint;
import art.arcane.react.core.history.HistorySegment;
import art.arcane.react.core.history.HistoryStore;
import art.arcane.react.core.history.HistoryTier;
import art.arcane.react.core.history.HistoryWal;
import art.arcane.react.core.history.VersionOneHistoryFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryVersionRecoveryTest {
  @Test
  void v1CheckpointOverlapAndRestartBeforeJournalResetDoNotDuplicateSamples(@TempDir Path directory) throws Exception {
    VersionOneHistoryFixtures.writeRawSegment(directory);
    Path walPath = VersionOneHistoryFixtures.writeWal(directory);
    byte[] originalWal = Files.readAllBytes(walPath);
    HistoryStore store = new HistoryStore(directory, 3);
    store.initialize();
    HistoryController first = new HistoryController();
    first.setHistoryStore(store);
    try (HistoryWal wal = new HistoryWal(walPath)) {
      first.recover(wal.open());
    }

    assertV1Values(store.read(HistoryTier.RAW, 0L, null));
    assertEquals(43L, first.getSequence().get());
    assertEquals(1L, store.read(HistoryTier.RAW, 0L, null).series("archived").count(5));
    assertArrayEquals(originalWal, Files.readAllBytes(walPath));
    assertEquals(2, ByteBuffer.wrap(Files.readAllBytes(directory.resolve("raw/0.rht"))).getShort(4));

    HistoryStore reopened = new HistoryStore(directory, 3);
    reopened.initialize();
    HistoryController second = new HistoryController();
    second.setHistoryStore(reopened);
    try (HistoryWal wal = new HistoryWal(walPath)) {
      HistoryWal.WalRecovery recovery = wal.open();
      second.setWal(wal);
      second.recover(recovery);
      assertV1Values(reopened.read(HistoryTier.RAW, 0L, null));
      assertEquals(43L, second.getSequence().get());
      assertEquals(1L, reopened.read(HistoryTier.RAW, 0L, null).series("archived").count(5));
    }
    try (HistoryWal wal = new HistoryWal(walPath)) {
      assertTrue(wal.open().samples().isEmpty());
      assertEquals(2, ByteBuffer.wrap(Files.readAllBytes(walPath)).getShort(4));
    }
  }

  @Test
  void v1JournalOnlyRecoveryKeepsTheLastCapturePerSecond(@TempDir Path directory) throws Exception {
    Path walPath = VersionOneHistoryFixtures.writeWal(directory);
    HistoryStore store = new HistoryStore(directory, 3);
    store.initialize();
    HistoryController controller = new HistoryController();
    controller.setHistoryStore(store);
    try (HistoryWal wal = new HistoryWal(walPath)) {
      HistoryWal.WalRecovery recovery = wal.open();
      controller.setWal(wal);
      controller.recover(recovery);
    }

    assertV1Values(store.read(HistoryTier.RAW, 0L, null));
    assertEquals(43L, controller.getSequence().get());
    assertEquals(Sampler.Metadata.UNKNOWN, store.descriptor("tps").metadata());
  }

  private static void assertV1Values(HistorySegment segment) {
    assertEquals(43L, segment.lastSequence());
    assertEquals(Sampler.Metadata.UNKNOWN, segment.series("tps").metadata());
    assertEquals(new HistoryPoint(1_000L, 1_000L, 18D, 18D, 18D, 18D, 18D, 1L, 0L, 0L),
        segment.series("tps").point(0L, 1_000L, 1));
    assertEquals(new HistoryPoint(2_000L, 1_000L, 19D, 19D, 19D, 19D, 19D, 1L, 0L, 0L),
        segment.series("tps").point(0L, 1_000L, 2));
  }
}
