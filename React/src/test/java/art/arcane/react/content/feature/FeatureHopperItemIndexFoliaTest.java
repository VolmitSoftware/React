package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.core.controller.HopperItemIndex;
import art.arcane.react.core.controller.HopperPositionIndex;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

class FeatureHopperItemIndexFoliaTest {
    @Test
    void foliaSweepBoundsAndDeduplicatesOwnerRegionWork() {
        World world = Mockito.mock(World.class);
        UUID worldId = UUID.randomUUID();
        Mockito.when(world.getUID()).thenReturn(worldId);
        Player[] players = new Player[20];
        for (int i = 0; i < players.length; i++) {
            Player player = Mockito.mock(Player.class);
            Mockito.when(player.isOnline()).thenReturn(true);
            Mockito.when(player.getLocation()).thenReturn(new Location(world, i * 1600D, 64D, 0D));
            players[i] = player;
        }
        EntityController controller = Mockito.mock(EntityController.class);
        Mockito.when(controller.getFoliaPlayers()).thenReturn(players);
        List<Entity> playerTasks = new ArrayList<>();
        List<ChunkTask> chunkTasks = new ArrayList<>();

        try (MockedStatic<React> react = Mockito.mockStatic(React.class);
             MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
             MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
            scheduling.when(J::isFoliaThreading).thenReturn(true);
            scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenReturn(true);
            scheduling.when(() -> J.runEntity(
                    Mockito.any(Entity.class),
                    Mockito.any(Runnable.class),
                    Mockito.eq(0),
                    Mockito.any(Runnable.class)))
                .thenAnswer(invocation -> {
                    playerTasks.add(invocation.getArgument(0));
                    Runnable task = invocation.getArgument(1);
                    task.run();
                    return true;
                });
            scheduling.when(() -> J.runChunk(
                    Mockito.same(world),
                    Mockito.anyInt(),
                    Mockito.anyInt(),
                    Mockito.any(Runnable.class)))
                .thenAnswer(invocation -> {
                    chunkTasks.add(new ChunkTask(invocation.getArgument(1), invocation.getArgument(2)));
                    return true;
                });

            FeatureHopperItemIndex feature = new FeatureHopperItemIndex();
            feature.onActivate();
            feature.onTick();

            Assertions.assertEquals(16, playerTasks.size());
            Assertions.assertEquals(16, new HashSet<>(playerTasks).size());
            Assertions.assertEquals(64, chunkTasks.size());
            Assertions.assertEquals(64, new HashSet<>(chunkTasks).size());
            bukkit.verify(Bukkit::getWorlds, Mockito.never());
            bukkit.verify(() -> Bukkit.getEntity(Mockito.any(UUID.class)), Mockito.never());
            feature.onDeactivate();
        }
    }

    @Test
    void ownerChunkScanSeedsHoppersAndMovesItemsBetweenBuckets() {
        UUID worldId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        World world = Mockito.mock(World.class);
        Chunk chunk = Mockito.mock(Chunk.class);
        Item item = Mockito.mock(Item.class);
        Hopper hopper = Mockito.mock(Hopper.class);
        Block hopperBlock = Mockito.mock(Block.class);
        ChunkLoadEvent event = Mockito.mock(ChunkLoadEvent.class);
        Mockito.when(world.getUID()).thenReturn(worldId);
        Mockito.when(world.isChunkLoaded(0, 0)).thenReturn(true);
        Mockito.when(world.getChunkAt(0, 0, false)).thenReturn(chunk);
        Mockito.when(chunk.getWorld()).thenReturn(world);
        Mockito.when(chunk.getX()).thenReturn(0);
        Mockito.when(chunk.getZ()).thenReturn(0);
        Mockito.when(chunk.getEntities()).thenReturn(new Entity[]{item});
        Mockito.when(chunk.getTileEntities(
            Mockito.<Predicate<? super Block>>any(),
            Mockito.eq(false))).thenReturn(List.of(hopper));
        Mockito.when(item.getUniqueId()).thenReturn(itemId);
        Mockito.when(item.isValid()).thenReturn(true);
        Mockito.when(item.isDead()).thenReturn(false);
        Mockito.when(item.getLocation()).thenReturn(new Location(world, 80.5D, 65D, 0.5D));
        Mockito.when(hopper.getX()).thenReturn(3);
        Mockito.when(hopper.getY()).thenReturn(64);
        Mockito.when(hopper.getZ()).thenReturn(4);
        Mockito.when(world.getBlockAt(3, 64, 4)).thenReturn(hopperBlock);
        Mockito.when(hopperBlock.getType()).thenReturn(Material.HOPPER);
        Mockito.when(event.getChunk()).thenReturn(chunk);

        try (MockedStatic<React> react = Mockito.mockStatic(React.class);
             MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
             MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            scheduling.when(J::isFoliaThreading).thenReturn(true);
            scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenReturn(true);
            scheduling.when(() -> J.runChunk(
                    Mockito.same(world),
                    Mockito.eq(0),
                    Mockito.eq(0),
                    Mockito.any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable task = invocation.getArgument(3);
                    task.run();
                    return true;
                });

            FeatureHopperItemIndex feature = new FeatureHopperItemIndex();
            feature.onActivate();
            HopperItemIndex itemIndex = feature.getItemIndex();
            itemIndex.addItem(worldId, 0, 0, itemId);
            feature.on(event);
            feature.onTick();

            Assertions.assertArrayEquals(new UUID[0], itemIndex.itemIdsInChunk(worldId, 0, 0));
            Assertions.assertArrayEquals(new UUID[]{itemId}, itemIndex.itemIdsInChunk(worldId, 5, 0));
            long[] hoppers = feature.getPositionIndex().hoppersInChunk(worldId, 0, 0);
            Assertions.assertArrayEquals(new long[]{HopperPositionIndex.packPos(3, 64, 4)}, hoppers);

            feature.on(event);
            feature.onTick();

            Mockito.verify(chunk, Mockito.times(1)).getEntities();
            Mockito.verify(chunk, Mockito.times(1)).getTileEntities(
                Mockito.<Predicate<? super Block>>any(),
                Mockito.eq(false));
            bukkit.verify(() -> Bukkit.getEntity(Mockito.any(UUID.class)), Mockito.never());
            feature.onDeactivate();
        }
    }

    @Test
    void denseChunkSeedMaterializesOnceAndOwnerDispatchAdvancesAcrossCycles() {
        UUID worldId = UUID.randomUUID();
        World world = Mockito.mock(World.class);
        Chunk chunk = Mockito.mock(Chunk.class);
        ChunkLoadEvent event = Mockito.mock(ChunkLoadEvent.class);
        Entity[] entities = new Entity[300];
        Set<UUID> itemIds = new HashSet<>();
        Mockito.when(world.getUID()).thenReturn(worldId);
        Mockito.when(world.isChunkLoaded(0, 0)).thenReturn(true);
        Mockito.when(world.getChunkAt(0, 0, false)).thenReturn(chunk);
        Mockito.when(chunk.getWorld()).thenReturn(world);
        Mockito.when(chunk.getX()).thenReturn(0);
        Mockito.when(chunk.getZ()).thenReturn(0);
        Mockito.when(chunk.getTileEntities(
            Mockito.<Predicate<? super Block>>any(),
            Mockito.eq(false))).thenReturn(List.of());
        Mockito.when(event.getChunk()).thenReturn(chunk);
        for (int i = 0; i < entities.length; i++) {
            UUID itemId = UUID.randomUUID();
            Item item = Mockito.mock(Item.class);
            Mockito.when(item.getUniqueId()).thenReturn(itemId);
            Mockito.when(item.isValid()).thenReturn(true);
            Mockito.when(item.isDead()).thenReturn(false);
            Mockito.when(item.getLocation()).thenReturn(new Location(world, 0.5D, 65D, 0.5D));
            entities[i] = item;
            itemIds.add(itemId);
        }
        Mockito.when(chunk.getEntities()).thenReturn(entities);

        try (MockedStatic<React> react = Mockito.mockStatic(React.class);
             MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
            List<Entity> ownerTasks = new ArrayList<>();
            scheduling.when(J::isFoliaThreading).thenReturn(true);
            scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Entity.class))).thenReturn(true);
            scheduling.when(() -> J.runEntity(
                    Mockito.any(Entity.class),
                    Mockito.any(Runnable.class),
                    Mockito.eq(0),
                    Mockito.any(Runnable.class)))
                .thenAnswer(invocation -> {
                    ownerTasks.add(invocation.getArgument(0));
                    return true;
                });
            scheduling.when(() -> J.runChunk(
                    Mockito.same(world),
                    Mockito.eq(0),
                    Mockito.eq(0),
                    Mockito.any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable task = invocation.getArgument(3);
                    task.run();
                    return true;
                });

            FeatureHopperItemIndex feature = new FeatureHopperItemIndex();
            feature.onActivate();
            feature.on(event);
            feature.onTick();

            Assertions.assertEquals(300, feature.getItemIndex().size());
            Assertions.assertEquals(itemIds, feature.getItemIndex().allItemIds());
            Assertions.assertEquals(0, ownerTasks.size());

            feature.on(event);
            feature.onTick();
            Assertions.assertEquals(256, ownerTasks.size());

            feature.on(event);
            feature.onTick();

            Assertions.assertEquals(300, ownerTasks.size());
            Assertions.assertEquals(300, new HashSet<>(ownerTasks).size());
            Mockito.verify(chunk, Mockito.times(1)).getEntities();
            Mockito.verify(chunk, Mockito.times(1)).getTileEntities(
                Mockito.<Predicate<? super Block>>any(),
                Mockito.eq(false));
            feature.onDeactivate();
        }
    }

    @Test
    void entitiesLoadSeedsAndOwnerReconciliationFollowsChunkMigration() {
        UUID worldId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        World world = Mockito.mock(World.class);
        Item item = Mockito.mock(Item.class);
        EntitiesLoadEvent event = Mockito.mock(EntitiesLoadEvent.class);
        AtomicReference<Location> location = new AtomicReference<>(new Location(world, 0.5D, 65D, 0.5D));
        Mockito.when(world.getUID()).thenReturn(worldId);
        Mockito.when(item.getUniqueId()).thenReturn(itemId);
        Mockito.when(item.isValid()).thenReturn(true);
        Mockito.when(item.isDead()).thenReturn(false);
        Mockito.when(item.getLocation()).thenAnswer(invocation -> location.get());
        Mockito.when(event.getEntities()).thenReturn(List.of(item));

        try (MockedStatic<React> react = Mockito.mockStatic(React.class);
             MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
             MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            scheduling.when(J::isFoliaThreading).thenReturn(true);
            scheduling.when(() -> J.isOwnedByCurrentRegion(item)).thenReturn(true);
            scheduling.when(() -> J.runEntity(
                    Mockito.same(item),
                    Mockito.any(Runnable.class),
                    Mockito.eq(0),
                    Mockito.any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable task = invocation.getArgument(1);
                    task.run();
                    return true;
                });

            FeatureHopperItemIndex feature = new FeatureHopperItemIndex();
            feature.onActivate();
            feature.on(event);
            Assertions.assertArrayEquals(
                new UUID[]{itemId},
                feature.getItemIndex().itemIdsInChunk(worldId, 0, 0));

            location.set(new Location(world, 32.5D, 65D, 0.5D));
            feature.onTick();

            Assertions.assertArrayEquals(
                new UUID[0],
                feature.getItemIndex().itemIdsInChunk(worldId, 0, 0));
            Assertions.assertArrayEquals(
                new UUID[]{itemId},
                feature.getItemIndex().itemIdsInChunk(worldId, 2, 0));
            bukkit.verify(Bukkit::getWorlds, Mockito.never());
            bukkit.verify(() -> Bukkit.getEntity(Mockito.any(UUID.class)), Mockito.never());
            feature.onDeactivate();
        }
    }

    @Test
    void rejectedChunkWorkIsReleasedAndRetried() {
        UUID worldId = UUID.randomUUID();
        World world = Mockito.mock(World.class);
        Chunk chunk = Mockito.mock(Chunk.class);
        ChunkLoadEvent event = Mockito.mock(ChunkLoadEvent.class);
        Mockito.when(world.getUID()).thenReturn(worldId);
        Mockito.when(chunk.getWorld()).thenReturn(world);
        Mockito.when(chunk.getX()).thenReturn(0);
        Mockito.when(chunk.getZ()).thenReturn(0);
        Mockito.when(event.getChunk()).thenReturn(chunk);

        try (MockedStatic<React> react = Mockito.mockStatic(React.class);
             MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
            scheduling.when(J::isFoliaThreading).thenReturn(true);
            scheduling.when(() -> J.runChunk(
                    Mockito.same(world),
                    Mockito.eq(0),
                    Mockito.eq(0),
                    Mockito.any(Runnable.class)))
                .thenReturn(false);

            FeatureHopperItemIndex feature = new FeatureHopperItemIndex();
            feature.onActivate();
            feature.on(event);
            feature.onTick();
            feature.onTick();

            scheduling.verify(() -> J.runChunk(
                Mockito.same(world),
                Mockito.eq(0),
                Mockito.eq(0),
                Mockito.any(Runnable.class)), Mockito.times(2));
            feature.onDeactivate();
        }
    }

    @Test
    void retiredChunkTaskCannotMutateReactivatedIndices() {
        UUID worldId = UUID.randomUUID();
        World world = Mockito.mock(World.class);
        Chunk chunk = Mockito.mock(Chunk.class);
        ChunkLoadEvent event = Mockito.mock(ChunkLoadEvent.class);
        List<Runnable> tasks = new ArrayList<>();
        Mockito.when(world.getUID()).thenReturn(worldId);
        Mockito.when(chunk.getWorld()).thenReturn(world);
        Mockito.when(chunk.getX()).thenReturn(0);
        Mockito.when(chunk.getZ()).thenReturn(0);
        Mockito.when(event.getChunk()).thenReturn(chunk);

        try (MockedStatic<React> react = Mockito.mockStatic(React.class);
             MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
            scheduling.when(J::isFoliaThreading).thenReturn(true);
            scheduling.when(() -> J.runChunk(
                    Mockito.same(world),
                    Mockito.eq(0),
                    Mockito.eq(0),
                    Mockito.any(Runnable.class)))
                .thenAnswer(invocation -> {
                    tasks.add(invocation.getArgument(3));
                    return true;
                });

            FeatureHopperItemIndex feature = new FeatureHopperItemIndex();
            feature.onActivate();
            feature.on(event);
            feature.onTick();
            Assertions.assertEquals(1, tasks.size());

            feature.onDeactivate();
            feature.onActivate();
            tasks.getFirst().run();

            Assertions.assertEquals(0, feature.getItemIndex().size());
            Assertions.assertEquals(0, feature.getPositionIndex().hopperCount(worldId));
            Mockito.verify(world, Mockito.never()).isChunkLoaded(Mockito.anyInt(), Mockito.anyInt());
            feature.onDeactivate();
        }
    }

    private record ChunkTask(int chunkX, int chunkZ) {
    }
}
