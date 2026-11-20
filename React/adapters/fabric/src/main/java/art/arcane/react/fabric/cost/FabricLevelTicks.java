package art.arcane.react.fabric.cost;

import art.arcane.react.mod.cost.CostBucket;
import art.arcane.react.mod.cost.CostFrame;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Direct timing wrappers for the per-level tick listeners, whose interface names differ between Fabric API lines. */
final class FabricLevelTicks {
    private FabricLevelTicks() {
    }

    static Object wrap(Class<?> type, Object listener, CostBucket bucket) {
        if (type == ServerTickEvents.EndLevelTick.class) {
            ServerTickEvents.EndLevelTick delegate = (ServerTickEvents.EndLevelTick) listener;
            return (ServerTickEvents.EndLevelTick) level -> {
                long start = CostFrame.enter();
                try {
                    delegate.onEndTick(level);
                } finally {
                    CostFrame.exit(start, bucket);
                }
            };
        }
        if (type == ServerTickEvents.StartLevelTick.class) {
            ServerTickEvents.StartLevelTick delegate = (ServerTickEvents.StartLevelTick) listener;
            return (ServerTickEvents.StartLevelTick) level -> {
                long start = CostFrame.enter();
                try {
                    delegate.onStartTick(level);
                } finally {
                    CostFrame.exit(start, bucket);
                }
            };
        }
        return null;
    }
}
