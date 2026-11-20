package art.arcane.react.fabric.cost;

import art.arcane.react.mod.cost.CostBucket;
import art.arcane.react.mod.cost.CostFrame;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Direct timing wrappers for the per-level tick listeners, whose interface names differ between Fabric API lines. */
final class FabricLevelTicks {
    private FabricLevelTicks() {
    }

    static Object wrap(Class<?> type, Object listener, CostBucket bucket) {
        if (type == ServerTickEvents.EndWorldTick.class) {
            ServerTickEvents.EndWorldTick delegate = (ServerTickEvents.EndWorldTick) listener;
            return (ServerTickEvents.EndWorldTick) level -> {
                long start = CostFrame.enter();
                try {
                    delegate.onEndTick(level);
                } finally {
                    CostFrame.exit(start, bucket);
                }
            };
        }
        if (type == ServerTickEvents.StartWorldTick.class) {
            ServerTickEvents.StartWorldTick delegate = (ServerTickEvents.StartWorldTick) listener;
            return (ServerTickEvents.StartWorldTick) level -> {
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
