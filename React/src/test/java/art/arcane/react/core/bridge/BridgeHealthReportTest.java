package art.arcane.react.core.bridge;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

public class BridgeHealthReportTest {

    private NmsBridgeRegistry registry;

    @BeforeEach
    public void setup() {
        registry = new NmsBridgeRegistry();
    }

    @Test
    public void emptyRegistry_producesEmptyReport() {
        BridgeHealthReport report = registry.snapshotHealth();
        Assertions.assertNotNull(report);
        Assertions.assertTrue(report.entries().isEmpty());
        Assertions.assertEquals(0, report.availableCount());
        Assertions.assertEquals(0, report.unavailableCount());
    }

    @Test
    public void availableHandle_reportedAsAvailable() {
        NmsBridgeDescriptor descriptor = new NmsBridgeDescriptor(
                "Integer.parseInt",
                BridgeKind.STATIC_METHOD,
                List.of("java.lang.Integer"),
                "parseInt",
                List.of(List.of("java.lang.String")),
                "int",
                Optional.empty()
        );
        registry.resolve(descriptor);

        BridgeHealthReport report = registry.snapshotHealth();
        Assertions.assertEquals(1, report.availableCount());
        Assertions.assertEquals(0, report.unavailableCount());

        BridgeHealthReport.BridgeHealthEntry entry = report.entries().get(0);
        Assertions.assertEquals("Integer.parseInt", entry.logicalId());
        Assertions.assertTrue(entry.available());
        Assertions.assertNotNull(entry.resolutionSummary());
        Assertions.assertFalse(entry.resolutionSummary().isBlank());
    }

    @Test
    public void unavailableHandle_reportedAsUnavailable() {
        NmsBridgeDescriptor descriptor = new NmsBridgeDescriptor(
                "does.not.Exist.missing",
                BridgeKind.METHOD,
                List.of("does.not.Exist"),
                "missing",
                List.of(List.of()),
                "void",
                Optional.empty()
        );
        registry.resolve(descriptor);

        BridgeHealthReport report = registry.snapshotHealth();
        Assertions.assertEquals(0, report.availableCount());
        Assertions.assertEquals(1, report.unavailableCount());

        BridgeHealthReport.BridgeHealthEntry entry = report.entries().get(0);
        Assertions.assertEquals("does.not.Exist.missing", entry.logicalId());
        Assertions.assertFalse(entry.available());
        Assertions.assertNotNull(entry.failureReason());
        Assertions.assertFalse(entry.failureReason().isBlank());
    }

    @Test
    public void mixedRegistry_countsCorrectly() {
        registry.resolve(new NmsBridgeDescriptor(
                "Integer.parseInt",
                BridgeKind.STATIC_METHOD,
                List.of("java.lang.Integer"),
                "parseInt",
                List.of(List.of("java.lang.String")),
                "int",
                Optional.empty()
        ));
        registry.resolve(new NmsBridgeDescriptor(
                "Missing.one",
                BridgeKind.METHOD,
                List.of("does.not.Exist"),
                "one",
                List.of(List.of()),
                "void",
                Optional.empty()
        ));
        registry.resolve(new NmsBridgeDescriptor(
                "Missing.two",
                BridgeKind.METHOD,
                List.of("does.not.Exist"),
                "two",
                List.of(List.of()),
                "void",
                Optional.empty()
        ));

        BridgeHealthReport report = registry.snapshotHealth();
        Assertions.assertEquals(1, report.availableCount());
        Assertions.assertEquals(2, report.unavailableCount());
        Assertions.assertEquals(3, report.entries().size());
    }

    @Test
    public void snapshotIsImmutableCopy() {
        NmsBridgeDescriptor descriptor = new NmsBridgeDescriptor(
                "Integer.parseInt",
                BridgeKind.STATIC_METHOD,
                List.of("java.lang.Integer"),
                "parseInt",
                List.of(List.of("java.lang.String")),
                "int",
                Optional.empty()
        );
        registry.resolve(descriptor);

        BridgeHealthReport report1 = registry.snapshotHealth();
        registry.clear();
        BridgeHealthReport report2 = registry.snapshotHealth();

        Assertions.assertEquals(1, report1.entries().size());
        Assertions.assertEquals(0, report2.entries().size());
    }
}
