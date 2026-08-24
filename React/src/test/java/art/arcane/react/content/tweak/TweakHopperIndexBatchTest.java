package art.arcane.react.content.tweak;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.feature.FeatureHopperContainerThroughputMap;
import art.arcane.react.content.feature.FeatureHopperItemIndex;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.NMS;
import art.arcane.react.core.bridge.NmsBridgeHandle;
import art.arcane.react.core.controller.HopperItemIndex;
import art.arcane.react.core.controller.HopperPositionIndex;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class TweakHopperIndexBatchTest {
    private static final MethodHandle ADD_ITEM_HANDLE;
    private static final MethodHandle BLOCK_POS_HANDLE;
    private static final MethodHandle GET_BLOCK_ENTITY_HANDLE;
    private static final MethodHandle IS_EMPTY_HANDLE;
    private static final VarHandle COOLDOWN_HANDLE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            ADD_ITEM_HANDLE = lookup.findStatic(
                TweakHopperIndexBatchTest.class,
                "addItem",
                MethodType.methodType(boolean.class, TestHopper.class, TestItemHandle.class));
            BLOCK_POS_HANDLE = lookup.findStatic(
                TweakHopperIndexBatchTest.class,
                "blockPos",
                MethodType.methodType(TestBlockPos.class, int.class, int.class, int.class));
            GET_BLOCK_ENTITY_HANDLE = lookup.findStatic(
                TweakHopperIndexBatchTest.class,
                "getBlockEntity",
                MethodType.methodType(TestHopper.class, TestWorldHandle.class, TestBlockPos.class));
            IS_EMPTY_HANDLE = lookup.findStatic(
                TweakHopperIndexBatchTest.class,
                "isEmpty",
                MethodType.methodType(boolean.class, TestHopper.class));
            COOLDOWN_HANDLE = lookup.findVarHandle(TestHopper.class, "cooldown", int.class);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void overlappingHoppersRetainHopperFirstCompetition(boolean foliaThreading) throws Exception {
        try (TestContext context = new TestContext(foliaThreading)) {
            TestHopper first = context.addHopper(0, 64, 0, 1);
            TestHopper second = context.addHopper(1, 64, 0, 1);
            IndexedItem firstItem = context.addItem(1.0D, 65.0D, 0.5D, true, false);
            IndexedItem secondItem = context.addItem(1.0D, 65.0D, 0.5D, true, false);

            context.tick();

            Assertions.assertEquals(1, first.accepted);
            Assertions.assertEquals(1, second.accepted);
            Assertions.assertEquals(8, first.cooldown);
            Assertions.assertEquals(8, second.cooldown);
            Assertions.assertEquals(4, context.worldHandle.attempts.size());
            int firstHopperId = context.worldHandle.attempts.get(0).hopperId();
            int secondHopperId = context.worldHandle.attempts.get(2).hopperId();
            Assertions.assertEquals(firstHopperId, context.worldHandle.attempts.get(1).hopperId());
            Assertions.assertEquals(secondHopperId, context.worldHandle.attempts.get(3).hopperId());
            Assertions.assertNotEquals(firstHopperId, secondHopperId);
            Assertions.assertEquals(0L, context.metricCount());
            Assertions.assertEquals(foliaThreading ? 1 : 0, context.tasks.size());
            context.verifyResolvedOnce(firstItem);
            context.verifyResolvedOnce(secondItem);
        }
    }

    @Test
    void pickupPredicateKeepsExactInclusiveAndExclusiveBounds() {
        int hopperX = 8;
        int hopperY = 64;
        int hopperZ = -4;
        double centerX = hopperX + 0.5D;
        double centerZ = hopperZ + 0.5D;
        double minimumY = hopperY + 0.9D;
        double maximumY = hopperY + 2.1D;

        Assertions.assertTrue(TweakHopperIndex.isWithinPickupBounds(centerX, minimumY, centerZ, hopperX, hopperY, hopperZ));
        Assertions.assertTrue(TweakHopperIndex.isWithinPickupBounds(centerX, maximumY, centerZ, hopperX, hopperY, hopperZ));
        Assertions.assertTrue(TweakHopperIndex.isWithinPickupBounds(
            Math.nextDown(centerX + 0.6D),
            minimumY,
            centerZ,
            hopperX,
            hopperY,
            hopperZ));
        Assertions.assertTrue(TweakHopperIndex.isWithinPickupBounds(
            Math.nextUp(centerX - 0.6D),
            maximumY,
            centerZ,
            hopperX,
            hopperY,
            hopperZ));
        Assertions.assertTrue(TweakHopperIndex.isWithinPickupBounds(
            centerX,
            minimumY,
            Math.nextDown(centerZ + 0.6D),
            hopperX,
            hopperY,
            hopperZ));
        Assertions.assertTrue(TweakHopperIndex.isWithinPickupBounds(
            centerX,
            maximumY,
            Math.nextUp(centerZ - 0.6D),
            hopperX,
            hopperY,
            hopperZ));

        Assertions.assertFalse(TweakHopperIndex.isWithinPickupBounds(
            Math.nextUp(centerX + 0.6D),
            minimumY,
            centerZ,
            hopperX,
            hopperY,
            hopperZ));
        Assertions.assertFalse(TweakHopperIndex.isWithinPickupBounds(
            Math.nextDown(centerX - 0.6D),
            maximumY,
            centerZ,
            hopperX,
            hopperY,
            hopperZ));
        Assertions.assertFalse(TweakHopperIndex.isWithinPickupBounds(
            centerX,
            Math.nextDown(minimumY),
            centerZ,
            hopperX,
            hopperY,
            hopperZ));
        Assertions.assertFalse(TweakHopperIndex.isWithinPickupBounds(
            centerX,
            Math.nextUp(maximumY),
            centerZ,
            hopperX,
            hopperY,
            hopperZ));
        Assertions.assertFalse(TweakHopperIndex.isWithinPickupBounds(
            centerX,
            minimumY,
            Math.nextUp(centerZ + 0.6D),
            hopperX,
            hopperY,
            hopperZ));
        Assertions.assertFalse(TweakHopperIndex.isWithinPickupBounds(
            centerX,
            maximumY,
            Math.nextDown(centerZ - 0.6D),
            hopperX,
            hopperY,
            hopperZ));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void boundaryFilteringRunsBeforeNmsHandleResolution(boolean foliaThreading) throws Exception {
        try (TestContext context = new TestContext(foliaThreading)) {
            TestHopper hopper = context.addHopper(8, 64, 0, 4);
            double centerX = 8.5D;
            IndexedItem insideHorizontal = context.addItem(
                Math.nextDown(centerX + 0.6D),
                64.9D,
                0.5D,
                true,
                false);
            IndexedItem insideVertical = context.addItem(centerX, 66.1D, 0.5D, true, false);
            IndexedItem outsideHorizontal = context.addItem(
                Math.nextUp(centerX + 0.6D),
                65.0D,
                0.5D,
                true,
                false);
            IndexedItem outsideVertical = context.addItem(
                centerX,
                Math.nextDown(64.9D),
                0.5D,
                true,
                false);

            context.tick();

            Assertions.assertEquals(2, hopper.accepted);
            Assertions.assertEquals(2, context.worldHandle.attempts.size());
            Assertions.assertEquals(8, hopper.cooldown);
            Assertions.assertEquals(0L, context.metricCount());
            context.verifyResolvedOnce(insideHorizontal);
            context.verifyResolvedOnce(insideVertical);
            context.verifyNotResolved(outsideHorizontal);
            context.verifyNotResolved(outsideVertical);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidDeadAndMissingItemsFallBackOncePerHopper(boolean foliaThreading) throws Exception {
        try (TestContext context = new TestContext(foliaThreading)) {
            TestHopper hopper = context.addHopper(0, 64, 0, 4);
            context.addItem(0.5D, 65.0D, 0.5D, false, false);
            context.addItem(0.5D, 65.0D, 0.5D, true, true);
            context.addMissingItem(0, 0);

            context.tick();

            Assertions.assertEquals(0, hopper.accepted);
            Assertions.assertEquals(0, hopper.cooldown);
            Assertions.assertTrue(context.worldHandle.attempts.isEmpty());
            Assertions.assertEquals(1L, context.metricCount());
            Assertions.assertEquals(foliaThreading ? 1 : 0, context.tasks.size());
            context.nms.verify(() -> NMS.getHandle(Mockito.any(Entity.class)), Mockito.never());
        }
    }

    @Test
    void staleFoliaRegionItemIsSkippedBeforeEntityStateAccess() throws Exception {
        try (TestContext context = new TestContext(true)) {
            context.addHopper(0, 64, 0, 4);
            IndexedItem movedItem = context.addItem(0.5D, 65.0D, 0.5D, true, false);
            context.scheduler.when(() -> J.isOwnedByCurrentRegion(movedItem.bukkit)).thenReturn(false);

            context.tick();

            Assertions.assertEquals(1L, context.metricCount());
            Mockito.verify(movedItem.bukkit, Mockito.never()).isValid();
            Mockito.verify(movedItem.bukkit, Mockito.never()).isDead();
            Mockito.verify(movedItem.bukkit, Mockito.never()).getLocation();
            context.nms.verify(() -> NMS.getHandle(movedItem.bukkit), Mockito.never());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedEntityLookupDoesNotAbortRemainingChunkItems(boolean foliaThreading) throws Exception {
        try (TestContext context = new TestContext(foliaThreading)) {
            TestHopper hopper = context.addHopper(0, 64, 0, 4);
            context.addThrowingLookup(0, 0);
            IndexedItem validItem = context.addItem(0.5D, 65.0D, 0.5D, true, false);

            context.tick();

            Assertions.assertEquals(1, hopper.accepted);
            Assertions.assertEquals(8, hopper.cooldown);
            Assertions.assertEquals(0L, context.metricCount());
            Assertions.assertEquals(1, context.itemIndex.size());
            context.verifyResolvedOnce(validItem);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fullInventoryLeavesCooldownAndFallbackMetricUntouched(boolean foliaThreading) throws Exception {
        try (TestContext context = new TestContext(foliaThreading)) {
            TestHopper hopper = context.addHopper(0, 64, 0, 0);
            context.addItem(0.5D, 65.0D, 0.5D, true, false);

            context.tick();

            Assertions.assertEquals(0, hopper.accepted);
            Assertions.assertEquals(0, hopper.cooldown);
            Assertions.assertEquals(1, context.worldHandle.attempts.size());
            Assertions.assertEquals(1L, context.metricCount());
            Assertions.assertEquals(foliaThreading ? 1 : 0, context.tasks.size());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void itemChunksBatchEntityLookupsAndPreserveFallbackCounts(boolean foliaThreading) throws Exception {
        try (TestContext context = new TestContext(foliaThreading)) {
            context.addHopper(0, 64, 0, 0);
            context.addHopper(1, 64, 0, 0);
            context.addHopper(2, 64, 0, 0);
            context.addHopper(32, 64, 0, 0);
            context.addHopper(80, 64, 0, 0);
            context.addHopper(81, 64, 0, 0);
            IndexedItem firstItem = context.addItem(0.5D, 65.0D, 0.5D, true, false);
            IndexedItem secondItem = context.addItem(32.5D, 65.0D, 0.5D, true, false);

            context.tick();

            Assertions.assertEquals(6L, context.metricCount());
            Assertions.assertEquals(foliaThreading ? 2 : 0, context.tasks.size());
            context.verifyResolvedOnce(firstItem);
            context.verifyResolvedOnce(secondItem);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void denseChunkPickupWorkScalesWithItemCellsInsteadOfHopperItemPairs(boolean foliaThreading) throws Exception {
        try (TestContext context = new TestContext(foliaThreading)) {
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 8; z++) {
                    context.addHopper(x, 64, z, 0);
                }
            }
            for (int item = 0; item < 128; item++) {
                context.addItem(0.5D, 65.0D, 0.5D, true, false);
            }

            context.tick();

            Assertions.assertEquals(256, context.itemValidityReads());
            Assertions.assertEquals(128, context.worldHandle.attempts.size());
            Assertions.assertEquals(64L, context.metricCount());
            Assertions.assertEquals(foliaThreading ? 1 : 0, context.tasks.size());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void hopperAtChunkEdgeConsumesAnItemIndexedInTheAdjacentChunk(boolean foliaThreading) throws Exception {
        try (TestContext context = new TestContext(foliaThreading)) {
            TestHopper hopper = context.addHopper(15, 64, 0, 1);
            context.addItem(16.05D, 65.0D, 0.5D, true, false);

            context.tick();

            Assertions.assertEquals(1, hopper.accepted);
            Assertions.assertEquals(8, hopper.cooldown);
            Assertions.assertEquals(0L, context.metricCount());
            if (foliaThreading) {
                Assertions.assertEquals(1, context.tasks.size());
                Assertions.assertEquals(0, context.tasks.getFirst().chunkX());
            }
        }
    }

    @Test
    void rejectedFoliaChunkTaskCountsEveryCandidateHopperAsFallback() throws Exception {
        try (TestContext context = new TestContext(true)) {
            context.executeTasks = false;
            context.acceptTasks = false;
            context.addHopper(0, 64, 0, 4);
            context.addHopper(1, 64, 0, 4);
            context.addItem(0.5D, 65.0D, 0.5D, true, false);

            context.tick();

            Assertions.assertEquals(1, context.tasks.size());
            Assertions.assertEquals(2L, context.metricCount());

            context.tick();

            Assertions.assertEquals(2, context.tasks.size());
            Assertions.assertEquals(4L, context.metricCount());
        }
    }

    @Test
    void itemChunkBudgetRotatesWithoutReschedulingInflightChunks() throws Exception {
        try (TestContext context = new TestContext(true)) {
            context.executeTasks = false;
            context.setItemChunkBudget(4);
            for (int chunkX = 0; chunkX < 8; chunkX++) {
                int blockX = chunkX << 4;
                context.addHopper(blockX, 64, 0, 4);
                context.addItem(blockX + 0.5D, 65.0D, 0.5D, true, false);
            }

            context.tick();

            Assertions.assertEquals(4, context.tasks.size());
            Set<Integer> firstChunks = new HashSet<>();
            for (ChunkTask task : context.tasks) {
                firstChunks.add(task.chunkX());
            }
            Assertions.assertEquals(4, firstChunks.size());

            context.tick();

            Assertions.assertEquals(8, context.tasks.size());
            Set<Integer> visitedChunks = new HashSet<>();
            for (ChunkTask task : context.tasks) {
                visitedChunks.add(task.chunkX());
            }
            Assertions.assertEquals(8, visitedChunks.size());
        }
    }

    @Test
    void acceptedFoliaChunkRemainsClaimedUntilItsTaskCompletes() throws Exception {
        try (TestContext context = new TestContext(true)) {
            context.executeTasks = false;
            context.setItemChunkBudget(1);
            context.addHopper(0, 64, 0, 4);
            context.addItem(0.5D, 65.0D, 0.5D, true, false);

            context.tick();
            context.tick();

            Assertions.assertEquals(1, context.tasks.size());
            context.tasks.getFirst().work().run();

            context.tick();

            Assertions.assertEquals(2, context.tasks.size());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void everyTickIdleProbeCadenceLeavesVanillaPollingAvailable(boolean foliaThreading) throws Exception {
        try (TestContext context = new TestContext(foliaThreading)) {
            context.enableIdleStretch();
            List<TestHopper> hoppers = new ArrayList<>();
            for (int x = 0; x < 12; x++) {
                hoppers.add(context.addHopper(x, 64, 0, 4));
            }

            context.tick();

            Assertions.assertEquals(12L, context.metricCount());
            Assertions.assertEquals(0, context.tasks.size());
            for (TestHopper hopper : hoppers) {
                Assertions.assertEquals(0, hopper.cooldown);
            }
        }
    }

    @Test
    void idleStretchCooldownAlwaysEndsBeforeTheNextProbeCycle() {
        Assertions.assertEquals(0, TweakHopperIndex.effectiveIdleStretchCooldown(40, 1));
        Assertions.assertEquals(9, TweakHopperIndex.effectiveIdleStretchCooldown(40, 10));
        Assertions.assertEquals(39, TweakHopperIndex.effectiveIdleStretchCooldown(40, 40));
        Assertions.assertEquals(40, TweakHopperIndex.effectiveIdleStretchCooldown(40, 80));

        for (int spread = 2; spread <= 100; spread++) {
            int cooldown = TweakHopperIndex.effectiveIdleStretchCooldown(200, spread);
            boolean vanillaOpportunity = false;
            for (int tick = 0; tick < spread; tick++) {
                cooldown = Math.max(0, cooldown - 1);
                vanillaOpportunity |= cooldown == 0;
            }
            Assertions.assertTrue(vanillaOpportunity);
        }
    }

    private static boolean addItem(TestHopper hopper, TestItemHandle item) {
        hopper.world.attempts.add(new Attempt(hopper.id, item.id));
        if (hopper.accepted >= hopper.capacity || item.consumed) {
            return false;
        }
        hopper.accepted++;
        item.consumed = true;
        return true;
    }

    private static TestBlockPos blockPos(int x, int y, int z) {
        return new TestBlockPos(x, y, z);
    }

    private static TestHopper getBlockEntity(TestWorldHandle world, TestBlockPos position) {
        return world.hoppers.get(position);
    }

    private static boolean isEmpty(TestHopper hopper) {
        return hopper.accepted == 0;
    }

    private static NmsBridgeHandle methodBridge(MethodHandle methodHandle) {
        NmsBridgeHandle bridge = Mockito.mock(NmsBridgeHandle.class);
        Mockito.when(bridge.available()).thenReturn(true);
        Mockito.when(bridge.methodHandle()).thenReturn(methodHandle);
        return bridge;
    }

    private static NmsBridgeHandle cooldownBridge() {
        NmsBridgeHandle bridge = Mockito.mock(NmsBridgeHandle.class);
        Mockito.when(bridge.available()).thenReturn(true);
        Mockito.when(bridge.varHandle()).thenReturn(COOLDOWN_HANDLE);
        return bridge;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class TestContext implements AutoCloseable {
        private final boolean foliaThreading;
        private final UUID worldId;
        private final World world;
        private final HopperItemIndex itemIndex;
        private final HopperPositionIndex positionIndex;
        private final TestWorldHandle worldHandle;
        private final TweakHopperIndex tweak;
        private final Map<UUID, Entity> entities;
        private final Map<Entity, Object> handles;
        private final Set<UUID> throwingLookups;
        private final AtomicInteger itemValidityReads;
        private final MockedStatic<React> react;
        private final MockedStatic<Bukkit> bukkit;
        private final MockedStatic<J> scheduler;
        private final MockedStatic<NMS> nms;
        private final List<ChunkTask> tasks;
        private boolean executeTasks;
        private boolean acceptTasks;
        private int nextHopperId;

        private TestContext(boolean foliaThreading) throws Exception {
            this.foliaThreading = foliaThreading;
            worldId = UUID.randomUUID();
            world = Mockito.mock(World.class);
            itemIndex = new HopperItemIndex();
            positionIndex = new HopperPositionIndex();
            worldHandle = new TestWorldHandle();
            tweak = new TweakHopperIndex();
            entities = new HashMap<>();
            handles = new IdentityHashMap<>();
            throwingLookups = new HashSet<>();
            itemValidityReads = new AtomicInteger();
            tasks = new ArrayList<>();
            executeTasks = true;
            acceptTasks = true;

            react = Mockito.mockStatic(React.class);
            bukkit = Mockito.mockStatic(Bukkit.class);
            scheduler = Mockito.mockStatic(J.class);
            nms = Mockito.mockStatic(NMS.class);
            FeatureHopperContainerThroughputMap.suckInItemsInvocations.set(0L);

            Mockito.when(world.getUID()).thenReturn(worldId);
            Mockito.when(world.isChunkLoaded(Mockito.anyInt(), Mockito.anyInt())).thenReturn(true);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            bukkit.when(() -> Bukkit.getEntity(Mockito.any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID itemId = invocation.getArgument(0);
                    if (throwingLookups.contains(itemId)) {
                        throw new IllegalStateException("stale entity lookup");
                    }
                    return entities.get(itemId);
                });
            scheduler.when(J::isFoliaThreading).thenReturn(foliaThreading);
            scheduler.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenReturn(true);
            scheduler.when(() -> J.runChunk(
                    Mockito.eq(world),
                    Mockito.anyInt(),
                    Mockito.anyInt(),
                    Mockito.any(Runnable.class)))
                .thenAnswer(invocation -> {
                    int chunkX = invocation.getArgument(1);
                    int chunkZ = invocation.getArgument(2);
                    Runnable runnable = invocation.getArgument(3);
                    tasks.add(new ChunkTask(chunkX, chunkZ, runnable));
                    if (executeTasks) {
                        runnable.run();
                    }
                    return acceptTasks;
                });
            nms.when(() -> NMS.getWorldServer(world)).thenReturn(worldHandle);
            nms.when(() -> NMS.getHandle(Mockito.any(Entity.class)))
                .thenAnswer(invocation -> handles.get(invocation.getArgument(0)));

            Sampler tickSampler = Mockito.mock(Sampler.class);
            Mockito.when(tickSampler.sample()).thenReturn(100D);
            react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(tickSampler);

            FeatureHopperItemIndex feature = Mockito.mock(FeatureHopperItemIndex.class);
            Mockito.when(feature.getItemIndex()).thenReturn(itemIndex);
            Mockito.when(feature.getPositionIndex()).thenReturn(positionIndex);
            setField(tweak, "bridgeAddItem", methodBridge(ADD_ITEM_HANDLE));
            setField(tweak, "bridgeCooldownTime", cooldownBridge());
            setField(tweak, "bridgeGetBlockEntity", methodBridge(GET_BLOCK_ENTITY_HANDLE));
            setField(tweak, "bridgeBlockPosCtor", methodBridge(BLOCK_POS_HANDLE));
            setField(tweak, "bridgeIsEmpty", methodBridge(IS_EMPTY_HANDLE));
            setField(tweak, "bridgesAvailable", true);
            setField(tweak, "indexFeature", feature);
            setField(tweak, "idleStretch", false);
        }

        private TestHopper addHopper(int x, int y, int z, int capacity) {
            HopperOptions options = new HopperOptions(worldHandle, nextHopperId++, capacity);
            TestHopper hopper = new TestHopper(options);
            worldHandle.hoppers.put(new TestBlockPos(x, y, z), hopper);
            positionIndex.addHopper(worldId, x, y, z);
            return hopper;
        }

        private IndexedItem addItem(double x, double y, double z, boolean valid, boolean dead) {
            UUID id = UUID.randomUUID();
            Item item = Mockito.mock(Item.class);
            TestItemHandle handle = new TestItemHandle(id);
            IndexedItem indexed = new IndexedItem(id, item, handle);
            Mockito.when(item.isValid()).thenAnswer(ignored -> {
                itemValidityReads.incrementAndGet();
                return valid;
            });
            Mockito.when(item.isDead()).thenReturn(dead);
            Mockito.when(item.getLocation()).thenReturn(new Location(world, x, y, z));
            entities.put(id, item);
            handles.put(item, handle);
            itemIndex.addItem(worldId, ((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4, id);
            return indexed;
        }

        private void addMissingItem(int chunkX, int chunkZ) {
            itemIndex.addItem(worldId, chunkX, chunkZ, UUID.randomUUID());
        }

        private UUID addThrowingLookup(int chunkX, int chunkZ) {
            UUID itemId = UUID.randomUUID();
            throwingLookups.add(itemId);
            itemIndex.addItem(worldId, chunkX, chunkZ, itemId);
            return itemId;
        }

        private void enableIdleStretch() throws Exception {
            setField(tweak, "idleStretch", true);
            setField(tweak, "idleStretchSpreadPasses", 1);
            setField(tweak, "idleStretchMinTickMs", 0D);
        }

        private void setItemChunkBudget(int budget) throws Exception {
            setField(tweak, "itemChunkBudgetPerTick", budget);
        }

        private void tick() {
            tweak.tickAllWorlds();
        }

        private long metricCount() {
            return FeatureHopperContainerThroughputMap.suckInItemsInvocations.get();
        }

        private int itemValidityReads() {
            return itemValidityReads.get();
        }

        private void verifyResolvedOnce(IndexedItem item) {
            bukkit.verify(() -> Bukkit.getEntity(item.id), Mockito.times(1));
            Mockito.verify(item.bukkit, Mockito.times(1)).getLocation();
            nms.verify(() -> NMS.getHandle(item.bukkit), Mockito.times(1));
        }

        private void verifyNotResolved(IndexedItem item) {
            bukkit.verify(() -> Bukkit.getEntity(item.id), Mockito.times(1));
            Mockito.verify(item.bukkit, Mockito.times(1)).getLocation();
            nms.verify(() -> NMS.getHandle(item.bukkit), Mockito.never());
        }

        @Override
        public void close() {
            FeatureHopperContainerThroughputMap.suckInItemsInvocations.set(0L);
            nms.close();
            scheduler.close();
            bukkit.close();
            react.close();
        }
    }

    private static final class TestWorldHandle {
        private final Map<TestBlockPos, TestHopper> hoppers = new HashMap<>();
        private final List<Attempt> attempts = new ArrayList<>();
    }

    private static final class TestHopper {
        private final TestWorldHandle world;
        private final int id;
        private final int capacity;
        private int accepted;
        private int cooldown;

        private TestHopper(HopperOptions options) {
            world = options.world();
            id = options.id();
            capacity = options.capacity();
        }
    }

    private static final class TestItemHandle {
        private final UUID id;
        private boolean consumed;

        private TestItemHandle(UUID id) {
            this.id = id;
        }
    }

    private record HopperOptions(TestWorldHandle world, int id, int capacity) {
    }

    private record IndexedItem(UUID id, Item bukkit, TestItemHandle handle) {
    }

    private record TestBlockPos(int x, int y, int z) {
    }

    private record Attempt(int hopperId, UUID itemId) {
    }

    private record ChunkTask(int chunkX, int chunkZ, Runnable work) {
    }
}
