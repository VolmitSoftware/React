package art.arcane.react.core.bridge;

import java.util.List;

public record BridgeHealthReport(List<BridgeHealthEntry> entries) {

    public record BridgeHealthEntry(
            String logicalId,
            BridgeKind kind,
            boolean available,
            String resolutionSummary,
            String failureReason
    ) {}

    public int availableCount() {
        int count = 0;
        for (BridgeHealthEntry entry : entries) {
            if (entry.available()) {
                count++;
            }
        }
        return count;
    }

    public int unavailableCount() {
        return entries.size() - availableCount();
    }
}
