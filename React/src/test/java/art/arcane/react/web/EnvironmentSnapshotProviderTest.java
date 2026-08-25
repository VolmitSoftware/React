package art.arcane.react.web;

import art.arcane.react.api.web.EnvironmentSnapshotProvider;
import art.arcane.react.api.web.dto.EnvironmentDto;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.core.telemetry.HostTelemetrySnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnvironmentSnapshotProviderTest {

    private IdentityDto fakeIdentity() {
        IdentityDto dto = new IdentityDto();
        dto.version = "9.9.9-test";
        dto.serverName = "TestBrand";
        dto.folia = true;
        dto.serverId = "127.0.0.1:0";
        return dto;
    }

    private HostTelemetrySnapshot fakeTelemetry() {
        HostTelemetrySnapshot snapshot = HostTelemetrySnapshot.empty();
        snapshot.environment().cpu.cores = Runtime.getRuntime().availableProcessors();
        snapshot.environment().memory.physicalTotal = 16L * 1024L * 1024L;
        snapshot.environment().jvm.javaVersion = System.getProperty("java.version");
        snapshot.environment().jvm.heapMax = Runtime.getRuntime().maxMemory();
        return snapshot;
    }

    @Test
    void snapshotCpuIsNonNullAndCoresMatchRuntime() {
        EnvironmentSnapshotProvider provider = new EnvironmentSnapshotProvider(this::fakeIdentity, this::fakeTelemetry);
        EnvironmentDto dto = provider.snapshot();

        assertNotNull(dto.cpu, "cpu must not be null");
        assertEquals(Runtime.getRuntime().availableProcessors(), dto.cpu.cores,
            "cpu.cores must match Runtime.availableProcessors()");
    }

    @Test
    void snapshotMemoryIsNonNullAndPhysicalTotalPositive() {
        EnvironmentSnapshotProvider provider = new EnvironmentSnapshotProvider(this::fakeIdentity, this::fakeTelemetry);
        EnvironmentDto dto = provider.snapshot();

        assertNotNull(dto.memory, "memory must not be null");
        assertTrue(dto.memory.physicalTotal > 0, "physicalTotal must be > 0");
    }

    @Test
    void snapshotJvmIsNonNullAndVersionNonBlank() {
        EnvironmentSnapshotProvider provider = new EnvironmentSnapshotProvider(this::fakeIdentity, this::fakeTelemetry);
        EnvironmentDto dto = provider.snapshot();

        assertNotNull(dto.jvm, "jvm must not be null");
        assertNotNull(dto.jvm.javaVersion, "jvm.javaVersion must not be null");
        assertTrue(!dto.jvm.javaVersion.isBlank(), "jvm.javaVersion must not be blank");
        assertTrue(dto.jvm.heapMax > 0, "jvm.heapMax must be > 0");
    }

    @Test
    void snapshotServerFieldsMatchInjectedIdentity() {
        EnvironmentSnapshotProvider provider = new EnvironmentSnapshotProvider(this::fakeIdentity, this::fakeTelemetry);
        EnvironmentDto dto = provider.snapshot();

        assertNotNull(dto.server, "server must not be null");
        assertEquals("TestBrand", dto.server.brand, "server.brand must match injected identity serverName");
        assertEquals("9.9.9-test", dto.server.version, "server.version must match injected identity version");
        assertTrue(dto.server.folia, "server.folia must match injected identity folia");
        assertNotNull(dto.disks, "disks must not be null");
        assertNotNull(dto.mounts, "mounts must not be null");
        assertNotNull(dto.network, "network must not be null");
    }
}
