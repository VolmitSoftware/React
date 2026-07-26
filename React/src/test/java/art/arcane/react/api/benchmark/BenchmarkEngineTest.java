package art.arcane.react.api.benchmark;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class BenchmarkEngineTest {
  private static final double ELIMINATED_KERNEL_MOPS = 100_000.0;

  @Test
  public void processorMeasurementReportsFinitePositiveThroughput() {
    CpuMeasurement measurement = BenchmarkEngine.measureCpu(BenchmarkProfile.quick());

    assertPositive(measurement.integerMops(), "integer");
    assertPositive(measurement.floatingMops(), "floating");
    assertPositive(measurement.cacheMops(), "cache");
    assertPositive(measurement.multiCoreMops(), "multi core");
    Assertions.assertTrue(measurement.threads() >= 1);
    Assertions.assertTrue(measurement.scaling() > 0.0);
    Assertions.assertTrue(measurement.elapsedMillis() >= 0L);
  }

  @Test
  public void processorKernelsAreNotOptimisedAway() {
    CpuMeasurement measurement = BenchmarkEngine.measureCpu(BenchmarkProfile.quick());

    Assertions.assertTrue(measurement.integerMops() < ELIMINATED_KERNEL_MOPS, "integer kernel was eliminated");
    Assertions.assertTrue(measurement.floatingMops() < ELIMINATED_KERNEL_MOPS, "floating kernel was eliminated");
    Assertions.assertTrue(measurement.cacheMops() < ELIMINATED_KERNEL_MOPS, "cache kernel was eliminated");
    Assertions.assertTrue(
        measurement.multiCoreMops() < ELIMINATED_KERNEL_MOPS * measurement.threads(),
        "parallel kernel was eliminated"
    );
  }

  @Test
  public void memoryMeasurementReportsBandwidthAndLatency() {
    MemoryMeasurement measurement = BenchmarkEngine.measureMemory(BenchmarkProfile.quick());

    assertPositive(measurement.writeGigabytesPerSecond(), "write");
    assertPositive(measurement.readGigabytesPerSecond(), "read");
    assertPositive(measurement.copyGigabytesPerSecond(), "copy");
    assertPositive(measurement.randomAccessNanos(), "latency");
    Assertions.assertEquals(BenchmarkProfile.quick().workingSetBytes(), measurement.workingSetBytes());
    Assertions.assertTrue(measurement.allocationNanos() > 0L);
  }

  @Test
  public void driveMeasurementReportsThroughputAndFlushLatency(@TempDir Path directory) throws Exception {
    Path target = directory.resolve("drive");
    DriveMeasurement measurement = BenchmarkEngine.measureDrive(target, DriveProfile.quick());

    assertPositive(measurement.writeMegabytesPerSecond(), "write");
    assertPositive(measurement.readMegabytesPerSecond(), "read");
    assertPositive(measurement.randomReadIops(), "random read");
    assertPositive(measurement.flushMillis(), "flush");
    Assertions.assertEquals(DriveProfile.quick().alignedPayloadBytes(), measurement.payloadBytes());
    Assertions.assertEquals(target.toAbsolutePath().toString(), measurement.target());
  }

  @Test
  public void driveMeasurementRemovesItsScratchFile(@TempDir Path directory) throws Exception {
    Path target = directory.resolve("drive");
    BenchmarkEngine.measureDrive(target, DriveProfile.quick());

    try (Stream<Path> files = Files.list(target)) {
      List<Path> remaining = files.toList();
      Assertions.assertTrue(remaining.isEmpty(), "left behind " + remaining);
    }
  }

  @Test
  public void driveMeasurementCreatesAMissingTargetDirectory(@TempDir Path directory) throws Exception {
    Path target = directory.resolve("nested").resolve("drive");
    Assertions.assertFalse(Files.exists(target));

    BenchmarkEngine.measureDrive(target, DriveProfile.quick());

    Assertions.assertTrue(Files.isDirectory(target));
  }

  @Test
  public void profilesRejectDegenerateConfiguration() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new BenchmarkProfile(10L, 0L, 1, 1024 * 1024));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new BenchmarkProfile(10L, 10L, 0, 1024 * 1024));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new BenchmarkProfile(-1L, 10L, 1, 1024 * 1024));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new BenchmarkProfile(10L, 10L, 1, 1024));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new DriveProfile(1024L, 4096, 8, 4096, 4));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new DriveProfile(4096L, 4096, 0, 4096, 4));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new DriveProfile(4096L, 4096, 8, 4096, 0));
  }

  private void assertPositive(double value, String name) {
    Assertions.assertTrue(Double.isFinite(value), name + " was not finite: " + value);
    Assertions.assertTrue(value > 0.0, name + " was not positive: " + value);
  }
}
