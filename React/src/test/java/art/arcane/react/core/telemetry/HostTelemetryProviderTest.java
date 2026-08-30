package art.arcane.react.core.telemetry;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class HostTelemetryProviderTest {
  @Test
  void ratesUseElapsedTimeAndIgnoreCounterResets() {
    Assertions.assertEquals(2_000D, HostTelemetryProvider.rate(3_000L, 1_000L, 1_000L));
    Assertions.assertEquals(120_000D, HostTelemetryProvider.perMinute(3_000L, 1_000L, 1_000L));
    Assertions.assertEquals(0D, HostTelemetryProvider.rate(999L, 1_000L, 1_000L));
    Assertions.assertEquals(0D, HostTelemetryProvider.rate(3_000L, 1_000L, 0L));
  }

  @Test
  void suppressesOnlyUnsupportedWindowsThermalZoneQueries() {
    Assertions.assertTrue(WindowsSensorWmiQueryHandler.shouldSuppress(
        "MSAcpi_ThermalZoneTemperature",
        0x8004100C
    ));
    Assertions.assertFalse(WindowsSensorWmiQueryHandler.shouldSuppress(
        "MSAcpi_ThermalZoneTemperature",
        0x80041010
    ));
    Assertions.assertFalse(WindowsSensorWmiQueryHandler.shouldSuppress("Win32_Processor", 0x8004100C));
    Assertions.assertFalse(WindowsSensorWmiQueryHandler.shouldSuppress(
        "MSAcpi_ThermalZoneTemperature",
        null
    ));
  }

  @Test
  void skipsLegacyWindowsSensorQueriesOnlyWhenLibreHardwareMonitorIsMissing() {
    Assertions.assertFalse(WindowsSensorWmiQueryHandler.shouldQuerySensors("Windows Server 2025", "6.9.0", false));
    Assertions.assertFalse(WindowsSensorWmiQueryHandler.shouldQuerySensors("Windows 11", "6.10.0", false));
    Assertions.assertFalse(WindowsSensorWmiQueryHandler.shouldQuerySensors("Windows 11", null, false));
    Assertions.assertFalse(WindowsSensorWmiQueryHandler.shouldQuerySensors("Windows 11", "invalid", false));
    Assertions.assertTrue(WindowsSensorWmiQueryHandler.shouldQuerySensors("Windows 11", "6.11.0", false));
    Assertions.assertTrue(WindowsSensorWmiQueryHandler.shouldQuerySensors("Windows 11", "7.0.0", false));
    Assertions.assertTrue(WindowsSensorWmiQueryHandler.shouldQuerySensors("Windows 11", "6.9.0", true));
    Assertions.assertTrue(WindowsSensorWmiQueryHandler.shouldQuerySensors("Linux", "6.9.0", false));
  }

  @Test
  void captureProvidesOneCompleteCachedHostSnapshot(@TempDir Path dataPath) throws Exception {
    HostTelemetryProvider provider = new HostTelemetryProvider(dataPath);
    HostTelemetrySnapshot snapshot = provider.capture();

    Assertions.assertTrue(snapshot.available());
    Assertions.assertTrue(snapshot.physicalMemoryUsed() > 0L);
    Assertions.assertTrue(snapshot.heapMax() > 0L);
    Assertions.assertTrue(snapshot.processUptimeMs() > 0L);
    Assertions.assertNotNull(snapshot.environment().disks);
    Assertions.assertNotNull(snapshot.environment().mounts);
    Assertions.assertNotNull(snapshot.environment().network);
  }
}
