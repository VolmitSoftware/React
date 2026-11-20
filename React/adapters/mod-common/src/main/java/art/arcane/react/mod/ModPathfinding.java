package art.arcane.react.mod;

import art.arcane.react.core.telemetry.runtime.PathfindingRates;

/** Feeds vanilla path searches into React's pathfinding rates while React runs. */
public final class ModPathfinding {
    private static volatile PathfindingRates rates;

    private ModPathfinding() {
    }

    static void bind(PathfindingRates bound) {
        rates = bound;
    }

    /** A mob started a path search; mods have nothing that cancels one, so it always counts as accepted. */
    public static void searched() {
        PathfindingRates current = rates;
        if (current != null) {
            current.decision(false);
        }
    }

    public static void visited(int nodes) {
        PathfindingRates current = rates;
        if (current != null) {
            current.visited(nodes);
        }
    }
}
