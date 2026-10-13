package art.arcane.react.core.telemetry;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import oshi.SystemInfo;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

class HostTelemetryCacheTest {
  @Test
  void networkCountersRefreshEveryTenSecondsAndTheInterfaceListEveryThirty(@TempDir Path dataPath) throws Exception {
    Fixture fixture = new Fixture();
    NetworkIF networkInterface = Mockito.mock(NetworkIF.class);
    Mockito.when(networkInterface.getName()).thenReturn("test0");
    Mockito.when(networkInterface.updateAttributes()).thenReturn(true);
    Mockito.when(networkInterface.getBytesRecv()).thenReturn(2_048L);
    Mockito.when(fixture.hardware.getNetworkIFs()).thenReturn(List.of(networkInterface));

    HostTelemetryProvider provider = fixture.provider(dataPath);
    HostTelemetrySnapshot first = provider.capture();
    for (int second = 1; second < 10; second++) {
      fixture.advance(1_000L);
      provider.capture();
    }

    Mockito.verify(fixture.hardware, Mockito.times(1)).getNetworkIFs();
    Mockito.verify(networkInterface, Mockito.never()).updateAttributes();
    Assertions.assertEquals(1, first.environment().network.length);

    fixture.advance(1_000L);
    provider.capture();
    fixture.advance(10_000L);
    HostTelemetrySnapshot updated = provider.capture();

    Mockito.verify(fixture.hardware, Mockito.times(1)).getNetworkIFs();
    Mockito.verify(networkInterface, Mockito.times(2)).updateAttributes();
    Assertions.assertEquals(1, updated.environment().network.length);

    fixture.advance(10_000L);
    provider.capture();

    Mockito.verify(fixture.hardware, Mockito.times(2)).getNetworkIFs();
    Mockito.verify(networkInterface, Mockito.times(2)).updateAttributes();
  }

  @Test
  void networkRatesSpanTheIntervalBetweenCounterRefreshes(@TempDir Path dataPath) throws Exception {
    Fixture fixture = new Fixture();
    NetworkIF networkInterface = Mockito.mock(NetworkIF.class);
    Mockito.when(networkInterface.getName()).thenReturn("test0");
    Mockito.when(networkInterface.updateAttributes()).thenReturn(true);
    Mockito.when(networkInterface.getBytesRecv()).thenReturn(1_000L, 1_000L + 30_000L);
    Mockito.when(networkInterface.getBytesSent()).thenReturn(500L, 500L + 10_000L);
    Mockito.when(fixture.hardware.getNetworkIFs()).thenReturn(List.of(networkInterface));

    HostTelemetryProvider provider = fixture.provider(dataPath);
    HostTelemetrySnapshot first = provider.capture();
    fixture.advance(10_000L);
    HostTelemetrySnapshot refreshed = provider.capture();
    fixture.advance(1_000L);
    HostTelemetrySnapshot between = provider.capture();

    Assertions.assertEquals(0D, first.reading(HostMetric.NETWORK_RECEIVE).value());
    Assertions.assertEquals(3_000D, refreshed.reading(HostMetric.NETWORK_RECEIVE).value());
    Assertions.assertEquals(1_000D, refreshed.reading(HostMetric.NETWORK_SEND).value());
    Assertions.assertEquals(3_000D, between.reading(HostMetric.NETWORK_RECEIVE).value());
    Assertions.assertEquals(1_000D, between.reading(HostMetric.NETWORK_SEND).value());
  }

  @Test
  void disksAndMountsAreReenumeratedEveryThirtySecondsWithoutInPlaceUpdates(@TempDir Path dataPath) throws Exception {
    Fixture fixture = new Fixture();
    HWDiskStore disk = Mockito.mock(HWDiskStore.class);
    Mockito.when(disk.getName()).thenReturn("disk0");
    OSFileStore fileStore = Mockito.mock(OSFileStore.class);
    Mockito.when(fixture.hardware.getDiskStores()).thenReturn(List.of(disk));
    Mockito.when(fixture.fileSystem.getFileStores()).thenReturn(List.of(fileStore));

    HostTelemetryProvider provider = fixture.provider(dataPath);
    HostTelemetrySnapshot first = provider.capture();
    for (int second = 1; second < 30; second++) {
      fixture.advance(1_000L);
      provider.capture();
    }

    Mockito.verify(fixture.hardware, Mockito.times(1)).getDiskStores();
    Mockito.verify(fixture.fileSystem, Mockito.times(1)).getFileStores();
    Assertions.assertEquals(1, first.environment().disks.length);
    Assertions.assertEquals(1, first.environment().mounts.length);

    fixture.advance(1_000L);
    HostTelemetrySnapshot refreshed = provider.capture();

    Mockito.verify(fixture.hardware, Mockito.times(2)).getDiskStores();
    Mockito.verify(fixture.fileSystem, Mockito.times(2)).getFileStores();
    Mockito.verify(disk, Mockito.never()).updateAttributes();
    Mockito.verify(fileStore, Mockito.never()).updateAttributes();
    Assertions.assertEquals(1, refreshed.environment().disks.length);
    Assertions.assertEquals(1, refreshed.environment().mounts.length);
  }

  @Test
  void diskRatesSpanTheIntervalBetweenEnumerations(@TempDir Path dataPath) throws Exception {
    Fixture fixture = new Fixture();
    HWDiskStore before = Mockito.mock(HWDiskStore.class);
    HWDiskStore after = Mockito.mock(HWDiskStore.class);
    Mockito.when(before.getName()).thenReturn("disk0");
    Mockito.when(after.getName()).thenReturn("disk0");
    Mockito.when(before.getReadBytes()).thenReturn(4_096L);
    Mockito.when(before.getWriteBytes()).thenReturn(1_024L);
    Mockito.when(after.getReadBytes()).thenReturn(4_096L + 61_440L);
    Mockito.when(after.getWriteBytes()).thenReturn(1_024L + 122_880L);
    Mockito.when(fixture.hardware.getDiskStores()).thenReturn(List.of(before), List.of(after));

    HostTelemetryProvider provider = fixture.provider(dataPath);
    HostTelemetrySnapshot first = provider.capture();
    fixture.advance(30_000L);
    HostTelemetrySnapshot refreshed = provider.capture();
    fixture.advance(1_000L);
    HostTelemetrySnapshot between = provider.capture();

    Assertions.assertEquals(0D, first.reading(HostMetric.DISK_READ).value());
    Assertions.assertEquals(2_048D, refreshed.reading(HostMetric.DISK_READ).value());
    Assertions.assertEquals(4_096D, refreshed.reading(HostMetric.DISK_WRITE).value());
    Assertions.assertEquals(2_048D, between.reading(HostMetric.DISK_READ).value());
    Assertions.assertEquals(4_096D, between.reading(HostMetric.DISK_WRITE).value());
  }

  @Test
  void nativeFailureInvalidatesOnlyItsSourceAndKeepsJvmFresh(@TempDir Path dataPath) throws Exception {
    Fixture fixture = new Fixture();
    NetworkIF networkInterface = Mockito.mock(NetworkIF.class);
    Mockito.when(networkInterface.getName()).thenReturn("test0");
    Mockito.when(networkInterface.getName()).thenReturn("test0");
    Mockito.when(networkInterface.updateAttributes()).thenReturn(false);
    Mockito.when(fixture.hardware.getNetworkIFs()).thenReturn(List.of(networkInterface));
    HostTelemetryProvider provider = fixture.provider(dataPath);
    HostTelemetrySnapshot first = provider.capture();
    long networkAt = first.reading(HostMetric.NETWORK_DROPS).sampledAtMs();
    fixture.advance(10_000L);
    HostTelemetrySnapshot failed = provider.capture();
    Assertions.assertFalse(failed.reading(HostMetric.NETWORK_DROPS).available());
    Assertions.assertEquals("sampling-failed", failed.reading(HostMetric.NETWORK_DROPS).unavailableReason());
    Assertions.assertEquals(networkAt, failed.reading(HostMetric.NETWORK_DROPS).sampledAtMs());
    Assertions.assertTrue(failed.reading(HostMetric.HEAP_MAX).available());
    Assertions.assertTrue(failed.reading(HostMetric.HEAP_MAX).sampledAtMs() > networkAt);
  }

  @Test
  void cachedNativeReadingsKeepTheirActualRefreshTime(@TempDir Path dataPath) throws Exception {
    Fixture fixture = new Fixture();
    NetworkIF networkInterface = Mockito.mock(NetworkIF.class);
    Mockito.when(networkInterface.getName()).thenReturn("test0");
    Mockito.when(networkInterface.updateAttributes()).thenReturn(true);
    Mockito.when(fixture.hardware.getNetworkIFs()).thenReturn(List.of(networkInterface));
    HostTelemetryProvider provider = fixture.provider(dataPath);
    HostTelemetrySnapshot first = provider.capture();
    fixture.advance(1_000L);
    HostTelemetrySnapshot next = provider.capture();
    Assertions.assertEquals(first.reading(HostMetric.DISK_USABLE).sampledAtMs(), next.reading(HostMetric.DISK_USABLE).sampledAtMs());
    Assertions.assertEquals(first.reading(HostMetric.NETWORK_DROPS).sampledAtMs(), next.reading(HostMetric.NETWORK_DROPS).sampledAtMs());
    Assertions.assertTrue(next.reading(HostMetric.HEAP_MAX).sampledAtMs() > first.reading(HostMetric.HEAP_MAX).sampledAtMs());
  }

  @Test
  void processRatesAndDescriptorLimitsHaveIndependentCadences(@TempDir Path dataPath) throws Exception {
    Fixture fixture = new Fixture();
    OSProcess process = Mockito.mock(OSProcess.class);
    Mockito.when(fixture.operatingSystem.getProcess(Mockito.anyInt())).thenReturn(process);
    Mockito.when(process.getResidentSetSize()).thenReturn(8_192L);
    Mockito.when(process.getOpenFiles()).thenReturn(25L);
    Mockito.when(process.getSoftOpenFileLimit()).thenReturn(100L);
    Mockito.when(process.getBytesRead()).thenReturn(1_000L, 11_000L);
    HostTelemetryProvider provider = fixture.provider(dataPath);
    HostTelemetrySnapshot first = provider.capture();
    Assertions.assertFalse(first.reading(HostMetric.PROCESS_READ).available());
    fixture.advance(5_000L);
    HostTelemetrySnapshot second = provider.capture();
    Assertions.assertEquals(2_000D, second.reading(HostMetric.PROCESS_READ).value());
    Assertions.assertEquals(75D, second.reading(HostMetric.FILE_HEADROOM).value());
    Assertions.assertEquals(25D, second.reading(HostMetric.FILE_UTILIZATION).value());
    Assertions.assertEquals(first.reading(HostMetric.OPEN_FILES).sampledAtMs(), second.reading(HostMetric.OPEN_FILES).sampledAtMs());
    Mockito.verify(process, Mockito.times(1)).getOpenFiles();
  }

  @Test
  void unknownDescriptorLimitsRemainUnavailable(@TempDir Path dataPath) throws Exception {
    Fixture fixture = new Fixture();
    OSProcess process = Mockito.mock(OSProcess.class);
    Mockito.when(fixture.operatingSystem.getProcess(Mockito.anyInt())).thenReturn(process);
    Mockito.when(process.getOpenFiles()).thenReturn(-1L);
    Mockito.when(process.getSoftOpenFileLimit()).thenReturn(-1L);
    HostTelemetrySnapshot snapshot = fixture.provider(dataPath).capture();
    Assertions.assertFalse(snapshot.reading(HostMetric.OPEN_FILES).available());
    Assertions.assertFalse(snapshot.reading(HostMetric.FILE_LIMIT).available());
    Assertions.assertFalse(snapshot.reading(HostMetric.FILE_HEADROOM).available());
    Assertions.assertFalse(snapshot.reading(HostMetric.FILE_UTILIZATION).available());
  }

  @Test
  void newNetworkInterfacesCannotInventTrafficFromLifetimeCounters(@TempDir Path dataPath) throws Exception {
    Fixture fixture = new Fixture();
    NetworkIF first = Mockito.mock(NetworkIF.class);
    NetworkIF replacement = Mockito.mock(NetworkIF.class);
    Mockito.when(first.getName()).thenReturn("first");
    Mockito.when(replacement.getName()).thenReturn("replacement");
    Mockito.when(first.getBytesRecv()).thenReturn(100L);
    Mockito.when(replacement.getBytesRecv()).thenReturn(1_000_000L);
    Mockito.when(fixture.hardware.getNetworkIFs()).thenReturn(List.of(first), List.of(replacement));
    HostTelemetryProvider provider = fixture.provider(dataPath);
    provider.capture();
    fixture.advance(30_000L);
    HostTelemetrySnapshot snapshot = provider.capture();
    Assertions.assertFalse(snapshot.reading(HostMetric.NETWORK_RECEIVE).available());
    Assertions.assertEquals(0D, snapshot.reading(HostMetric.NETWORK_RECEIVE).value());
  }

  private static final class Fixture {
    private final HardwareAbstractionLayer hardware = Mockito.mock(HardwareAbstractionLayer.class, Mockito.RETURNS_DEEP_STUBS);
    private final OperatingSystem operatingSystem = Mockito.mock(OperatingSystem.class, Mockito.RETURNS_DEEP_STUBS);
    private final FileSystem fileSystem = Mockito.mock(FileSystem.class);
    private final SystemInfo systemInfo = Mockito.mock(SystemInfo.class);
    private final AtomicLong clock = new AtomicLong(1_000_000L);

    private Fixture() {
      Mockito.when(systemInfo.getHardware()).thenReturn(hardware);
      Mockito.when(systemInfo.getOperatingSystem()).thenReturn(operatingSystem);
      Mockito.when(operatingSystem.getFileSystem()).thenReturn(fileSystem);
      Mockito.when(hardware.getGraphicsCards()).thenReturn(List.of());
      Mockito.when(hardware.getSensors().getFanSpeeds()).thenReturn(new int[0]);
      Mockito.when(hardware.getPowerSources()).thenReturn(List.of());
      Mockito.when(hardware.getDiskStores()).thenReturn(List.of());
      Mockito.when(hardware.getNetworkIFs()).thenReturn(List.of());
      Mockito.when(fileSystem.getFileStores()).thenReturn(List.of());
    }

    private HostTelemetryProvider provider(Path dataPath) {
      return new HostTelemetryProvider(dataPath, systemInfo, clock::get);
    }

    private void advance(long millis) {
      clock.addAndGet(millis);
    }
  }
}
