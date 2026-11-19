package art.arcane.react.mod.cost;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModCostProbeTest {
    private final ModCostProbe probe = new ModCostProbe(new ModOwners() {
        @Override
        public String ownerOf(Class<?> type) {
            return UNKNOWN;
        }

        @Override
        public String ownerOfNamespace(String namespace) {
            return null;
        }

        @Override
        public String displayName(String ownerId) {
            return ownerId.toUpperCase();
        }
    }, null, "not under test");

    @BeforeEach
    void serverThread() {
        CostRegistry.serverThread = Thread.currentThread();
        probe.drainInto(new ArrayList<>());
    }

    @AfterEach
    void clearServerThread() {
        CostRegistry.serverThread = null;
    }

    @Test
    void nestedWorkIsChargedToTheInnerOwnerOnly() {
        CostBucket outer = CostRegistry.bucket("test-outer", CostKind.ENTITY);
        CostBucket inner = CostRegistry.bucket("test-inner", CostKind.EVENT);
        long outerStart = CostFrame.enter();
        spin(2_000_000L);
        long innerStart = CostFrame.enter();
        spin(20_000_000L);
        CostFrame.exit(innerStart, inner);
        CostFrame.exit(outerStart, outer);

        Map<String, ModCostProbe.OwnerCost> costs = drain();
        long outerNanos = costs.get("test-outer").exclusiveNanos();
        long innerNanos = costs.get("test-inner").exclusiveNanos();
        assertTrue(innerNanos >= 20_000_000L, "inner " + innerNanos);
        assertTrue(outerNanos >= 2_000_000L && outerNanos < 20_000_000L, "outer " + outerNanos);
        assertEquals(1L, costs.get("test-outer").syncCalls());
        assertEquals("TEST-INNER", costs.get("test-inner").displayName());
    }

    @Test
    void callsOffTheServerThreadAreOnlyCounted() throws InterruptedException {
        CostBucket bucket = CostRegistry.bucket("test-async", CostKind.EVENT);
        AtomicReference<Long> start = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            start.set(CostFrame.enter());
            CostFrame.exit(start.get(), bucket);
        });
        worker.start();
        worker.join();

        ModCostProbe.OwnerCost cost = drain().get("test-async");
        assertEquals(CostFrame.OFF_THREAD, start.get());
        assertEquals(0L, cost.exclusiveNanos());
        assertEquals(0L, cost.syncCalls());
        assertEquals(1L, cost.asyncCalls());
    }

    @Test
    void drainSumsKindsPerOwnerAndResets() {
        for (CostKind kind : CostKind.values()) {
            CostBucket bucket = CostRegistry.bucket("test-sum", kind);
            CostFrame.exit(CostFrame.enter(), bucket);
        }
        assertEquals(3L, drain().get("test-sum").syncCalls());
        assertTrue(!drain().containsKey("test-sum"));
    }

    private Map<String, ModCostProbe.OwnerCost> drain() {
        List<ModCostProbe.OwnerCost> costs = new ArrayList<>();
        probe.drainInto(costs);
        Map<String, ModCostProbe.OwnerCost> byOwner = new HashMap<>();
        for (ModCostProbe.OwnerCost cost : costs) {
            byOwner.put(cost.ownerId(), cost);
        }
        return byOwner;
    }

    private static void spin(long nanos) {
        long end = System.nanoTime() + nanos;
        while (System.nanoTime() < end) {
            Thread.onSpinWait();
        }
    }
}
