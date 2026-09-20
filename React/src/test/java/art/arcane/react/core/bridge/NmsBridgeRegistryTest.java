package art.arcane.react.core.bridge;

import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.monitor.NativeWorldAccess;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Optional;

class NmsBridgeRegistryTest {
    @Test
    void reportsAvailableAndMissingCapabilitiesAndClearsOnShutdown() {
        NmsBridgeRegistry registry = new NmsBridgeRegistry();
        try (MockedStatic<NativeAdapters> adapters = Mockito.mockStatic(NativeAdapters.class)) {
            adapters.when(() -> NativeAdapters.find(NativeWorldAccess.class)).thenReturn(Optional.empty());
            registry.register(NativeWorldAccess.class);
            Assertions.assertEquals(1, registry.snapshotHealth().unavailableCount());
            adapters.when(() -> NativeAdapters.find(NativeWorldAccess.class))
                    .thenReturn(Optional.of(Mockito.mock(NativeWorldAccess.class)));
            registry.register(NativeWorldAccess.class);
            Assertions.assertEquals(1, registry.snapshotHealth().availableCount());
            Assertions.assertEquals(0, registry.snapshotHealth().unavailableCount());
            registry.clear();
            Assertions.assertTrue(registry.snapshotHealth().entries().isEmpty());
        }
    }
}
