package art.arcane.react.core.bridge;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class BridgeHealthReportTest {
    @Test
    void countsMixedCapabilityAvailability() {
        BridgeHealthReport report = new BridgeHealthReport(List.of(
                new BridgeHealthReport.BridgeHealthEntry("world", BridgeKind.METHOD, true, "Loaded", ""),
                new BridgeHealthReport.BridgeHealthEntry("monitor", BridgeKind.METHOD, false, "", "Unavailable")));
        Assertions.assertEquals(1, report.availableCount());
        Assertions.assertEquals(1, report.unavailableCount());
    }

    @Test
    void emptyRegistryProducesEmptyReport() {
        BridgeHealthReport report = new NmsBridgeRegistry().snapshotHealth();
        Assertions.assertTrue(report.entries().isEmpty());
        Assertions.assertEquals(0, report.availableCount());
        Assertions.assertEquals(0, report.unavailableCount());
    }
}
