package art.arcane.react.core.bridge;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

public class BridgeHealthReportTest {

    private NmsBridgeRegistry registry;

    @Before
    public void setup() {
        registry = new NmsBridgeRegistry();
    }

    @Test
    public void emptyRegistry_producesEmptyReport() {
        BridgeHealthReport report = registry.snapshotHealth();
        Assert.assertNotNull(report);
        Assert.assertTrue(report.entries().isEmpty());
        Assert.assertEquals(0, report.availableCount());
        Assert.assertEquals(0, report.unavailableCount());
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
        Assert.assertEquals(1, report.availableCount());
        Assert.assertEquals(0, report.unavailableCount());

        BridgeHealthReport.BridgeHealthEntry entry = report.entries().get(0);
        Assert.assertEquals("Integer.parseInt", entry.logicalId());
        Assert.assertTrue(entry.available());
        Assert.assertNotNull(entry.resolutionSummary());
        Assert.assertFalse(entry.resolutionSummary().isBlank());
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
        Assert.assertEquals(0, report.availableCount());
        Assert.assertEquals(1, report.unavailableCount());

        BridgeHealthReport.BridgeHealthEntry entry = report.entries().get(0);
        Assert.assertEquals("does.not.Exist.missing", entry.logicalId());
        Assert.assertFalse(entry.available());
        Assert.assertNotNull(entry.failureReason());
        Assert.assertFalse(entry.failureReason().isBlank());
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
        Assert.assertEquals(1, report.availableCount());
        Assert.assertEquals(2, report.unavailableCount());
        Assert.assertEquals(3, report.entries().size());
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

        Assert.assertEquals(1, report1.entries().size());
        Assert.assertEquals(0, report2.entries().size());
    }
}
