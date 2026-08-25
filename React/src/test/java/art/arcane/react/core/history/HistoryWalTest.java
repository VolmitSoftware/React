package art.arcane.react.core.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryWalTest {
  @Test
  void recoversEveryCompleteFrameAndTruncatesIncompleteTail(@TempDir Path directory) throws Exception {
    Path path = directory.resolve("active.wal");
    HistoryWal first = new HistoryWal(path);
    first.open();
    first.append(snapshot(1L, 1_000L, 20D));
    first.append(snapshot(2L, 2_000L, 19D));
    first.force();
    first.close();
    long size = Files.size(path);
    try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
      channel.truncate(size - 3L);
    }

    HistoryWal second = new HistoryWal(path);
    HistoryWal.WalRecovery recovery = second.open();

    assertTrue(recovery.truncated());
    assertEquals(1, recovery.metrics().size());
    assertEquals(1, recovery.samples().size());
    assertEquals(1L, recovery.samples().get(0).sequence());
    assertEquals(20D, recovery.samples().get(0).values().get(0).value());
    second.close();
  }

  private static MetricSnapshot snapshot(long sequence, long timestampMs, double value) {
    return MetricSnapshot.of(
        sequence,
        timestampMs,
        List.of(new MetricSnapshotValue("tps", "TPS", "", value, Double.toString(value), true))
    );
  }
}
