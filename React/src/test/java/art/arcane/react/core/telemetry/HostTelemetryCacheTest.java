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
import oshi.software.os.OperatingSystem;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

class HostTelemetryCacheTest {
  @Test
  void networkCountersRefreshEveryTenSecondsAndTheInterfaceListEveryThirty(@TempDir Path dataPath) throws Exception {
    Fixture fixture = new Fixture();
    NetworkIF networkInterface = Mockito.mock(NetworkIF.class);
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
    Mockito.when(networkInterface.getBytesRecv()).thenReturn(1_000L, 1_000L + 30_000L);
    Mockito.when(networkInterface.getBytesSent()).thenReturn(500L, 500L + 10_000L);
    Mockito.when(fixture.hardware.getNetworkIFs()).thenReturn(List.of(networkInterface));

    HostTelemetryProvider provider = fixture.provider(dataPath);
    HostTelemetrySnapshot first = provider.capture();
    fixture.advance(10_000L);
    HostTelemetrySnapshot refreshed = provider.capture();
    fixture.advance(1_000L);
    HostTelemetrySnapshot between = provider.capture();

    Assertions.assertEquals(0D, first.networkReceiveBytesPerSecond());
    Assertions.assertEquals(3_000D, refreshed.networkReceiveBytesPerSecond());
    Assertions.assertEquals(1_000D, refreshed.networkSendBytesPerSecond());
    Assertions.assertEquals(3_000D, between.networkReceiveBytesPerSecond());
    Assertions.assertEquals(1_000D, between.networkSendBytesPerSecond());
  }

  @Test
  void disksAndMountsAreReenumeratedEveryThirtySecondsWithoutInPlaceUpdates(@TempDir Path dataPath) throws Exception {
    Fixture fixture = new Fixture();
    HWDiskStore disk = Mockito.mock(HWDiskStore.class);
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

    Assertions.assertEquals(0D, first.diskReadBytesPerSecond());
    Assertions.assertEquals(2_048D, refreshed.diskReadBytesPerSecond());
    Assertions.assertEquals(4_096D, refreshed.diskWriteBytesPerSecond());
    Assertions.assertEquals(2_048D, between.diskReadBytesPerSecond());
    Assertions.assertEquals(4_096D, between.diskWriteBytesPerSecond());
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
