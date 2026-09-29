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
  void storeAndInterfaceListsAreEnumeratedOncePerCacheWindow(@TempDir Path dataPath) throws Exception {
    HardwareAbstractionLayer hardware = Mockito.mock(HardwareAbstractionLayer.class, Mockito.RETURNS_DEEP_STUBS);
    OperatingSystem operatingSystem = Mockito.mock(OperatingSystem.class, Mockito.RETURNS_DEEP_STUBS);
    FileSystem fileSystem = Mockito.mock(FileSystem.class);
    SystemInfo systemInfo = Mockito.mock(SystemInfo.class);
    HWDiskStore disk = Mockito.mock(HWDiskStore.class);
    NetworkIF networkInterface = Mockito.mock(NetworkIF.class);
    OSFileStore fileStore = Mockito.mock(OSFileStore.class);
    AtomicLong clock = new AtomicLong(1_000_000L);

    Mockito.when(systemInfo.getHardware()).thenReturn(hardware);
    Mockito.when(systemInfo.getOperatingSystem()).thenReturn(operatingSystem);
    Mockito.when(operatingSystem.getFileSystem()).thenReturn(fileSystem);
    Mockito.when(hardware.getGraphicsCards()).thenReturn(List.of());
    Mockito.when(hardware.getSensors().getFanSpeeds()).thenReturn(new int[0]);
    Mockito.when(hardware.getPowerSources()).thenReturn(List.of());
    Mockito.when(hardware.getDiskStores()).thenReturn(List.of(disk));
    Mockito.when(hardware.getNetworkIFs()).thenReturn(List.of(networkInterface));
    Mockito.when(fileSystem.getFileStores()).thenReturn(List.of(fileStore));
    Mockito.when(disk.getReadBytes()).thenReturn(4_096L);
    Mockito.when(networkInterface.getBytesRecv()).thenReturn(2_048L);

    HostTelemetryProvider provider = new HostTelemetryProvider(dataPath, systemInfo, clock::get);
    HostTelemetrySnapshot first = provider.capture();
    clock.addAndGet(1_000L);
    provider.capture();
    clock.addAndGet(1_000L);
    HostTelemetrySnapshot third = provider.capture();

    Mockito.verify(hardware, Mockito.times(1)).getDiskStores();
    Mockito.verify(hardware, Mockito.times(1)).getNetworkIFs();
    Mockito.verify(fileSystem, Mockito.times(1)).getFileStores();
    Mockito.verify(disk, Mockito.times(3)).updateAttributes();
    Mockito.verify(networkInterface, Mockito.times(3)).updateAttributes();
    Mockito.verify(fileStore, Mockito.times(3)).updateAttributes();
    Assertions.assertEquals(1, first.environment().disks.length);
    Assertions.assertEquals(1, third.environment().mounts.length);
    Assertions.assertEquals(1, third.environment().network.length);

    clock.addAndGet(30_000L);
    provider.capture();

    Mockito.verify(hardware, Mockito.times(2)).getDiskStores();
    Mockito.verify(hardware, Mockito.times(2)).getNetworkIFs();
    Mockito.verify(fileSystem, Mockito.times(2)).getFileStores();
  }
}
