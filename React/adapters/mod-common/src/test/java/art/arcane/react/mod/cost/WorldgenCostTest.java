package art.arcane.react.mod.cost;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldgenCostTest {
    private final ModOwners owners = new ModOwners() {
        @Override
        public String ownerOf(Class<?> type) {
            return UNKNOWN;
        }

        @Override
        public String ownerOfNamespace(String namespace) {
            return switch (namespace) {
                case "wgtest_mod" -> "wgtest_mod";
                case "wgtest_bundled" -> "wgtest_parent";
                default -> null;
            };
        }

        @Override
        public String displayName(String ownerId) {
            return ownerId;
        }

        @Override
        public List<LoadedMod> loadedMods() {
            return List.of();
        }
    };
    private final ModCostProbe probe = new ModCostProbe(owners, null, "not under test");

    @AfterEach
    void clear() {
        WorldgenCost.clear();
        probe.drainInto(new ArrayList<>());
    }

    @Test
    void contentIsChargedToTheModThatOwnsItsNamespace() {
        Object modFeature = new Object();
        Object bundledStructure = new Object();
        Object datapackCarver = new Object();
        Map<Object, String> namespaces = new IdentityHashMap<>();
        namespaces.put(modFeature, "wgtest_mod");
        namespaces.put(bundledStructure, "wgtest_bundled");
        namespaces.put(datapackCarver, "WGTEST_Pack");
        WorldgenCost.index(namespaces, owners);

        assertEquals("wgtest_mod", WorldgenCost.bucket(modFeature).ownerId());
        assertEquals("wgtest_parent", WorldgenCost.bucket(bundledStructure).ownerId());
        assertEquals("wgtest_pack", WorldgenCost.bucket(datapackCarver).ownerId());
        assertEquals(ModOwners.UNKNOWN, WorldgenCost.bucket(new Object()).ownerId());
        assertEquals(CostKind.WORLDGEN, WorldgenCost.bucket(modFeature).kind());
        assertEquals(Set.of("wgtest_mod", "wgtest_parent", "wgtest_pack"), WorldgenCost.owners());
    }

    @Test
    void equalContentFromTwoNamespacesKeepsItsOwnOwner() {
        String vanilla = new String("same");
        String modded = new String("same");
        Map<Object, String> namespaces = new IdentityHashMap<>();
        namespaces.put(vanilla, "minecraft");
        namespaces.put(modded, "wgtest_mod");
        WorldgenCost.index(namespaces, owners);

        assertEquals("minecraft", WorldgenCost.bucket(vanilla).ownerId());
        assertEquals("wgtest_mod", WorldgenCost.bucket(modded).ownerId());
    }

    @Test
    void workerThreadGenerationIsTimedFoldedIntoTheModAndKeptApart() throws InterruptedException {
        Object feature = new Object();
        Map<Object, String> namespaces = new IdentityHashMap<>();
        namespaces.put(feature, "wgtest_mod");
        WorldgenCost.index(namespaces, owners);
        CostBucket entity = CostRegistry.bucket("wgtest_mod", CostKind.ENTITY);
        AtomicLong spent = new AtomicLong();
        Thread worker = new Thread(() -> {
            long stepStart = System.nanoTime();
            long start = WorldgenCost.enter();
            spin(5_000_000L);
            WorldgenCost.exit(start, WorldgenCost.bucket(feature));
            WorldgenCost.step(WorldgenStep.FEATURES, stepStart);
            spent.set(System.nanoTime() - stepStart);
        });
        worker.start();
        worker.join();
        CostRegistry.serverThread = Thread.currentThread();
        try {
            CostFrame.exit(CostFrame.enter(), entity);
        } finally {
            CostRegistry.serverThread = null;
        }

        List<ModCostProbe.OwnerCost> costs = new ArrayList<>();
        probe.drainInto(costs);
        ModCostProbe.OwnerCost total = costs.stream().filter((ModCostProbe.OwnerCost cost) -> cost.ownerId().equals("wgtest_mod")).findFirst().orElseThrow();
        ModCostProbe.Worldgen worldgen = probe.worldgen();
        long generated = worldgen.ownerNanos("wgtest_mod");
        assertTrue(generated >= 5_000_000L && generated <= spent.get(), "worldgen " + generated + " of " + spent.get());
        assertTrue(total.exclusiveNanos() >= generated, "total " + total.exclusiveNanos());
        assertEquals(2L, total.syncCalls());
        assertEquals(0L, total.asyncCalls());
        assertTrue(worldgen.stepNanos(WorldgenStep.FEATURES) >= generated);
        assertEquals(0L, worldgen.stepNanos(WorldgenStep.NOISE));

        probe.drainInto(new ArrayList<>());
        assertEquals(0L, probe.worldgen().ownerNanos("wgtest_mod"));
        assertEquals(0L, probe.worldgen().stepNanos(WorldgenStep.FEATURES));
    }

    @Test
    void indexIsReplacedNotMerged() {
        Object first = new Object();
        Map<Object, String> namespaces = new IdentityHashMap<>();
        namespaces.put(first, "wgtest_mod");
        WorldgenCost.index(namespaces, owners);
        CostBucket bucket = WorldgenCost.bucket(first);
        WorldgenCost.index(Map.of(), owners);

        assertEquals(ModOwners.UNKNOWN, WorldgenCost.bucket(first).ownerId());
        assertSame(bucket, CostRegistry.bucket("wgtest_mod", CostKind.WORLDGEN));
    }

    private static void spin(long nanos) {
        long end = System.nanoTime() + nanos;
        while (System.nanoTime() < end) {
            Thread.onSpinWait();
        }
    }
}
