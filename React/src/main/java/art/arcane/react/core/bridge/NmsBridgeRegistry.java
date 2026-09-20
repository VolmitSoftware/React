package art.arcane.react.core.bridge;

import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.react.React;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class NmsBridgeRegistry {
    private final ConcurrentHashMap<String, BridgeHealthReport.BridgeHealthEntry> capabilities = new ConcurrentHashMap<>();

    public void register(Class<?> capability) {
        String name = capability.getSimpleName();
        try {
            boolean available = NativeAdapters.find(capability).isPresent();
            capabilities.put(name, new BridgeHealthReport.BridgeHealthEntry(name, BridgeKind.METHOD,
                    available, available ? "Native capability loaded" : "",
                    available ? "" : "No implementation for this Minecraft version"));
        } catch (RuntimeException | LinkageError failure) {
            capabilities.put(name, new BridgeHealthReport.BridgeHealthEntry(name, BridgeKind.METHOD,
                    false, "", failure.getClass().getSimpleName() + ": " + failure.getMessage()));
            React.reportError("Native capability failed to load: " + name, failure);
        }
    }

    public void clear() {
        capabilities.clear();
    }

    public BridgeHealthReport snapshotHealth() {
        List<BridgeHealthReport.BridgeHealthEntry> entries = new ArrayList<>(capabilities.values());
        entries.sort(java.util.Comparator.comparing(BridgeHealthReport.BridgeHealthEntry::logicalId));
        return new BridgeHealthReport(List.copyOf(entries));
    }

    public void warnUnavailable(Consumer<String> logger) {
        for (BridgeHealthReport.BridgeHealthEntry entry : capabilities.values()) {
            if (!entry.available()) {
                logger.accept("Native capability unavailable: " + entry.logicalId() + ": " + entry.failureReason());
            }
        }
    }
}
