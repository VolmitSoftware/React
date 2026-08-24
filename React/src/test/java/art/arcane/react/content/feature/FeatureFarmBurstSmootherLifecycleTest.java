package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class FeatureFarmBurstSmootherLifecycleTest {
    @Test
    void paperDeactivationStopsIntakeAndAppliesCancelledGrowth() throws Exception {
        World world = Mockito.mock(World.class);
        UUID worldId = UUID.randomUUID();
        Mockito.when(world.getUID()).thenReturn(worldId);
        GrowthFixture growth = growth(world, 4, 64, 7);
        Mockito.when(world.getBlockAt(4, 64, 7)).thenReturn(growth.block());
        FeatureFarmBurstSmoother feature = configuredFeature();
        feature.onActivate();
        feature.on(growth.event());
        Mockito.verify(growth.event()).setCancelled(true);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
             MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            scheduling.when(J::isFoliaThreading).thenReturn(false);
            scheduling.when(J::isPrimaryThread).thenReturn(true);

            feature.onDeactivate();

            Mockito.verify(growth.block()).setBlockData(growth.targetData(), false);
            Assertions.assertEquals(0, pendingSize(feature));
        }

        GrowthFixture rejected = growth(world, 5, 64, 7);
        feature.on(rejected.event());
        Mockito.verify(rejected.event(), Mockito.never()).setCancelled(true);
        Assertions.assertEquals(0, pendingSize(feature));
    }

    @Test
    void hotLoweredCapRejectsNewGrowthWithoutPruningCancelledEntries() throws Exception {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
        FeatureFarmBurstSmoother feature = configuredFeature();
        setField(feature, "maxPendingUpdates", 2);
        feature.onActivate();
        GrowthFixture first = growth(world, 1, 64, 1);
        GrowthFixture second = growth(world, 2, 64, 2);
        feature.on(first.event());
        feature.on(second.event());
        Assertions.assertEquals(2, pendingSize(feature));

        setField(feature, "maxPendingUpdates", 0);
        try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
            scheduling.when(J::isFoliaThreading).thenReturn(true);
            feature.onTick();
        }

        Assertions.assertEquals(2, pendingSize(feature));
        GrowthFixture rejected = growth(world, 3, 64, 3);
        feature.on(rejected.event());
        Mockito.verify(rejected.event(), Mockito.never()).setCancelled(true);
        Assertions.assertEquals(2, pendingSize(feature));
    }

    @Test
    void successfulWorldUnloadRetiresCancelledGrowthBeforeDeactivate() throws Exception {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
        GrowthFixture growth = growth(world, 6, 64, 6);
        WorldUnloadEvent unload = Mockito.mock(WorldUnloadEvent.class);
        Mockito.when(unload.getWorld()).thenReturn(world);
        Mockito.when(unload.isCancelled()).thenReturn(false);
        FeatureFarmBurstSmoother feature = configuredFeature();
        feature.onActivate();
        feature.on(growth.event());
        Mockito.verify(growth.event()).setCancelled(true);

        feature.on(unload);

        Assertions.assertEquals(0, pendingSize(feature));
        Assertions.assertDoesNotThrow(feature::onDeactivate);
        Mockito.verify(growth.block(), Mockito.never())
            .setBlockData(Mockito.any(BlockData.class), Mockito.anyBoolean());
    }

    @Test
    void cancelledWorldUnloadPreservesGrowthForDeactivateDrain() throws Exception {
        World world = Mockito.mock(World.class);
        UUID worldId = UUID.randomUUID();
        Mockito.when(world.getUID()).thenReturn(worldId);
        GrowthFixture growth = growth(world, 7, 64, 7);
        Mockito.when(world.getBlockAt(7, 64, 7)).thenReturn(growth.block());
        WorldUnloadEvent unload = Mockito.mock(WorldUnloadEvent.class);
        Mockito.when(unload.getWorld()).thenReturn(world);
        Mockito.when(unload.isCancelled()).thenReturn(true);
        FeatureFarmBurstSmoother feature = configuredFeature();
        feature.onActivate();
        feature.on(growth.event());

        feature.on(unload);

        Assertions.assertEquals(1, pendingSize(feature));
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
             MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            scheduling.when(J::isFoliaThreading).thenReturn(false);
            scheduling.when(J::isPrimaryThread).thenReturn(true);

            feature.onDeactivate();
        }

        Mockito.verify(growth.block()).setBlockData(growth.targetData(), false);
        Assertions.assertEquals(0, pendingSize(feature));
    }

    @Test
    void staleGrowthIsForceAppliedInsteadOfDiscarded() throws Exception {
        World world = Mockito.mock(World.class);
        UUID worldId = UUID.randomUUID();
        Mockito.when(world.getUID()).thenReturn(worldId);
        GrowthFixture growth = growth(world, 8, 64, 9);
        Mockito.when(world.getBlockAt(8, 64, 9)).thenReturn(growth.block());
        FeatureFarmBurstSmoother feature = configuredFeature();
        setField(feature, "stalePendingMS", -1);
        feature.onActivate();
        feature.on(growth.event());
        Thread.sleep(2L);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
             MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            scheduling.when(J::isFoliaThreading).thenReturn(false);
            scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                task.run();
                return null;
            });

            feature.onTick();
        }

        Mockito.verify(growth.block()).setBlockData(growth.targetData(), false);
        Assertions.assertEquals(0, pendingSize(feature));
    }

    @Test
    void foliaDeactivationAppliesPendingGrowthOnOwnerRegions() throws Exception {
        World world = Mockito.mock(World.class);
        UUID worldId = UUID.randomUUID();
        Mockito.when(world.getUID()).thenReturn(worldId);
        GrowthFixture first = growth(world, 12, 64, 12);
        GrowthFixture second = growth(world, 28, 64, 12);
        Mockito.when(world.getBlockAt(12, 64, 12)).thenReturn(first.block());
        Mockito.when(world.getBlockAt(28, 64, 12)).thenReturn(second.block());
        FeatureFarmBurstSmoother feature = configuredFeature();
        feature.onActivate();
        feature.on(first.event());
        feature.on(second.event());
        AtomicInteger regionTasks = new AtomicInteger(0);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
             MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            scheduling.when(J::isFoliaThreading).thenReturn(true);
            scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Location.class))).thenReturn(false);
            scheduling.when(() -> J.s(
                    Mockito.any(Location.class),
                    Mockito.any(Runnable.class),
                    Mockito.eq(0)))
                .thenAnswer(invocation -> {
                    regionTasks.incrementAndGet();
                    Runnable task = invocation.getArgument(1);
                    task.run();
                    return null;
                });

            feature.onDeactivate();
        }

        Assertions.assertEquals(2, regionTasks.get());
        Mockito.verify(first.block()).setBlockData(first.targetData(), false);
        Mockito.verify(second.block()).setBlockData(second.targetData(), false);
        Assertions.assertEquals(0, pendingSize(feature));
    }

    @Test
    void foliaShutdownRetriesOwnerFailureBeforeReturning() throws Exception {
        World world = Mockito.mock(World.class);
        UUID worldId = UUID.randomUUID();
        Mockito.when(world.getUID()).thenReturn(worldId);
        GrowthFixture growth = growth(world, 16, 64, 16);
        Mockito.when(world.getBlockAt(16, 64, 16)).thenReturn(growth.block());
        Mockito.doThrow(new IllegalStateException("region mutation failed"))
            .doNothing()
            .when(growth.block())
            .setBlockData(growth.targetData(), false);
        FeatureFarmBurstSmoother feature = configuredFeature();
        feature.onActivate();
        feature.on(growth.event());
        AtomicInteger regionTasks = new AtomicInteger(0);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
             MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
             MockedStatic<React> react = Mockito.mockStatic(React.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            scheduling.when(J::isFoliaThreading).thenReturn(true);
            scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Location.class))).thenReturn(false);
            scheduling.when(() -> J.s(
                    Mockito.any(Location.class),
                    Mockito.any(Runnable.class),
                    Mockito.eq(0)))
                .thenAnswer(invocation -> {
                    regionTasks.incrementAndGet();
                    Runnable task = invocation.getArgument(1);
                    task.run();
                    return null;
                });

            feature.onDeactivate();

            react.verify(() -> React.reportError(Mockito.any(IllegalStateException.class)));
        }

        Assertions.assertEquals(2, regionTasks.get());
        Mockito.verify(growth.block(), Mockito.times(2)).setBlockData(growth.targetData(), false);
        Assertions.assertEquals(0, pendingSize(feature));
    }

    @Test
    void foliaShutdownPropagatesTimeoutAndRetainsCancelledGrowth() throws Exception {
        World world = Mockito.mock(World.class);
        UUID worldId = UUID.randomUUID();
        Mockito.when(world.getUID()).thenReturn(worldId);
        GrowthFixture growth = growth(world, 20, 64, 20);
        Mockito.when(world.getBlockAt(20, 64, 20)).thenReturn(growth.block());
        FeatureFarmBurstSmoother feature = configuredFeature();
        setField(feature, "shutdownDrainTimeoutMS", 25);
        feature.onActivate();
        feature.on(growth.event());

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
             MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            scheduling.when(J::isFoliaThreading).thenReturn(true);
            scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Location.class))).thenReturn(false);

            IllegalStateException failure = Assertions.assertThrows(
                IllegalStateException.class,
                feature::onDeactivate);

            Assertions.assertTrue(failure.getMessage().contains("1 cancelled growth changes"));
            scheduling.verify(() -> J.s(
                Mockito.any(Location.class),
                Mockito.any(Runnable.class),
                Mockito.eq(0)));
        }

        Assertions.assertEquals(1, pendingSize(feature));
        Mockito.verify(growth.block(), Mockito.never()).setBlockData(Mockito.any(BlockData.class), Mockito.anyBoolean());
    }

    private static FeatureFarmBurstSmoother configuredFeature() throws Exception {
        FeatureFarmBurstSmoother feature = new FeatureFarmBurstSmoother();
        setField(feature, "burstTriggerCount", 1);
        setField(feature, "burstWindowMS", 1200);
        setField(feature, "minApplyDelayTicks", 1000);
        setField(feature, "maxApplyDelayTicks", 1000);
        setField(feature, "maxPendingUpdates", 16);
        setField(feature, "onlyDuringPressure", false);
        setField(feature, "bypassNearPlayers", false);
        return feature;
    }

    private static GrowthFixture growth(World world, int x, int y, int z) {
        Block block = Mockito.mock(Block.class);
        BlockState newState = Mockito.mock(BlockState.class);
        BlockData sourceData = Mockito.mock(BlockData.class);
        BlockData targetData = Mockito.mock(BlockData.class);
        BlockGrowEvent event = Mockito.mock(BlockGrowEvent.class);
        Mockito.when(block.getWorld()).thenReturn(world);
        Mockito.when(block.getX()).thenReturn(x);
        Mockito.when(block.getY()).thenReturn(y);
        Mockito.when(block.getZ()).thenReturn(z);
        Mockito.when(block.getType()).thenReturn(Material.WHEAT);
        Mockito.when(block.getLocation()).thenReturn(new Location(world, x, y, z));
        Mockito.when(newState.getBlockData()).thenReturn(sourceData);
        Mockito.when(sourceData.clone()).thenReturn(targetData);
        Mockito.when(event.getBlock()).thenReturn(block);
        Mockito.when(event.getNewState()).thenReturn(newState);
        return new GrowthFixture(event, block, targetData);
    }

    private static int pendingSize(FeatureFarmBurstSmoother feature) throws Exception {
        Field field = FeatureFarmBurstSmoother.class.getDeclaredField("pending");
        field.setAccessible(true);
        Map<?, ?> pending = (Map<?, ?>) field.get(feature);
        return pending.size();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record GrowthFixture(BlockGrowEvent event, Block block, BlockData targetData) {
    }
}
