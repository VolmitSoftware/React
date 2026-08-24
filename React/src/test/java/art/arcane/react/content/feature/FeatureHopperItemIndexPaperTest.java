package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.Ticker;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

class FeatureHopperItemIndexPaperTest {
    private React previous;

    @BeforeEach
    void setUp() {
        previous = React.instance;
        React plugin = Mockito.mock(React.class);
        Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
        React.instance = plugin;
    }

    @AfterEach
    void tearDown() {
        React.instance = previous;
    }

    @Test
    void thousandsOfLoadedChunksSeedAndReconcileThroughBoundedWindows() {
        int loadedChunkCount = 4_096;
        UUID worldId = UUID.randomUUID();
        World world = world(worldId, "paper-scale");
        Chunk seedChunk = Mockito.mock(Chunk.class, Mockito.withSettings().stubOnly());
        Chunk scanChunk = Mockito.mock(Chunk.class);
        Chunk[] loadedChunks = new Chunk[loadedChunkCount];
        Arrays.fill(loadedChunks, seedChunk);
        AtomicInteger coordinateReads = new AtomicInteger(0);
        AtomicInteger loadedArrayReads = new AtomicInteger(0);
        Mockito.when(seedChunk.getX()).thenAnswer(invocation -> coordinateReads.getAndIncrement());
        Mockito.when(seedChunk.getZ()).thenReturn(0);
        Mockito.when(world.getLoadedChunks()).thenAnswer(invocation -> {
            loadedArrayReads.incrementAndGet();
            return loadedChunks;
        });
        Mockito.when(world.isChunkLoaded(Mockito.anyInt(), Mockito.eq(0))).thenReturn(true);
        Mockito.when(world.getChunkAt(Mockito.anyInt(), Mockito.eq(0), Mockito.eq(false))).thenReturn(scanChunk);
        Mockito.when(scanChunk.getEntities()).thenReturn(new Entity[0]);
        Mockito.when(scanChunk.getTileEntities(
            Mockito.<Predicate<? super Block>>any(),
            Mockito.eq(false))).thenReturn(List.of());

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
             MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
             MockedStatic<React> react = Mockito.mockStatic(React.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            scheduling.when(J::isFoliaThreading).thenReturn(false);
            runSyncJobsImmediately(scheduling);
            ObserverController observer = new ObserverController();
            observer.start();
            Assertions.assertEquals(0, coordinateReads.get());
            observer.onTick();
            Assertions.assertEquals(256, coordinateReads.get());
            react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
            FeatureHopperItemIndex feature = new FeatureHopperItemIndex();

            feature.onActivate();
            Assertions.assertEquals(1, loadedArrayReads.get());
            feature.onTick();

            Mockito.verify(world, Mockito.times(64)).getChunkAt(
                Mockito.anyInt(),
                Mockito.eq(0),
                Mockito.eq(false));
            Assertions.assertEquals(256, coordinateReads.get());
            Assertions.assertEquals(1, loadedArrayReads.get());
            feature.onDeactivate();
            observer.stop();
        }
    }

    @Test
    void tenThousandHoppersSeedAtNoMoreThan256PerPass() {
        int hopperCount = 10_000;
        UUID worldId = UUID.randomUUID();
        World world = world(worldId, "paper-hoppers");
        Chunk chunk = Mockito.mock(Chunk.class);
        BlockState hopperState = Mockito.mock(BlockState.class);
        ObserverController observer = Mockito.mock(ObserverController.class);
        ObserverController.LoadedChunkTarget target = new ObserverController.LoadedChunkTarget(worldId, 0, 0);
        AtomicInteger seededHoppers = new AtomicInteger(0);
        Mockito.when(observer.nextLoadedChunkCoordinateBatch(64))
            .thenReturn(List.of(target), List.of());
        Mockito.when(world.isChunkLoaded(0, 0)).thenReturn(true);
        Mockito.when(world.getChunkAt(0, 0, false)).thenReturn(chunk);
        Mockito.when(chunk.getEntities()).thenReturn(new Entity[0]);
        Mockito.when(chunk.getTileEntities(
            Mockito.<Predicate<? super Block>>any(),
            Mockito.eq(false))).thenReturn(Collections.nCopies(hopperCount, hopperState));
        Mockito.when(hopperState.getX()).thenAnswer(invocation -> seededHoppers.get() & 15);
        Mockito.when(hopperState.getY()).thenAnswer(invocation -> seededHoppers.get() >> 8);
        Mockito.when(hopperState.getZ()).thenAnswer(invocation -> {
            int index = seededHoppers.getAndIncrement();
            return (index >> 4) & 15;
        });

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
             MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
             MockedStatic<React> react = Mockito.mockStatic(React.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            scheduling.when(J::isFoliaThreading).thenReturn(false);
            runSyncJobsImmediately(scheduling);
            react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
            FeatureHopperItemIndex feature = new FeatureHopperItemIndex();
            feature.onActivate();

            for (int pass = 0; pass < 40; pass++) {
                int before = seededHoppers.get();
                feature.onTick();
                Assertions.assertTrue(seededHoppers.get() - before <= 256);
            }

            Assertions.assertEquals(hopperCount, seededHoppers.get());
            Assertions.assertEquals(hopperCount, feature.getPositionIndex().hopperCount(worldId));
            Mockito.verify(chunk).getEntities();
            Mockito.verify(chunk).getTileEntities(
                Mockito.<Predicate<? super Block>>any(),
                Mockito.eq(false));
            feature.onDeactivate();
        }
    }

    @Test
    void tenThousandEntitiesInspectAtNoMoreThan256PerPass() {
        int entityCount = 10_000;
        UUID worldId = UUID.randomUUID();
        World world = world(worldId, "paper-entities");
        Chunk chunk = Mockito.mock(Chunk.class);
        ObserverController observer = Mockito.mock(ObserverController.class);
        ObserverController.LoadedChunkTarget target = new ObserverController.LoadedChunkTarget(worldId, 0, 0);
        AtomicInteger inspectedItems = new AtomicInteger(0);
        Entity[] entities = new Entity[entityCount];
        Location itemLocation = new Location(world, 0.5D, 64D, 0.5D);
        for (int index = 0; index < entityCount; index++) {
            entities[index] = item(new UUID(0L, index + 1L), itemLocation, inspectedItems);
        }
        Mockito.when(observer.nextLoadedChunkCoordinateBatch(64))
            .thenReturn(List.of(target), List.of());
        Mockito.when(world.isChunkLoaded(0, 0)).thenReturn(true);
        Mockito.when(world.getChunkAt(0, 0, false)).thenReturn(chunk);
        Mockito.when(chunk.getEntities()).thenReturn(entities);
        Mockito.when(chunk.getTileEntities(
            Mockito.<Predicate<? super Block>>any(),
            Mockito.eq(false))).thenReturn(List.of());

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
             MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
             MockedStatic<React> react = Mockito.mockStatic(React.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            scheduling.when(J::isFoliaThreading).thenReturn(false);
            runSyncJobsImmediately(scheduling);
            react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
            FeatureHopperItemIndex feature = new FeatureHopperItemIndex();
            feature.onActivate();

            for (int pass = 0; pass < 40; pass++) {
                int before = inspectedItems.get();
                feature.onTick();
                Assertions.assertTrue(inspectedItems.get() - before <= 256);
            }

            Assertions.assertEquals(entityCount, inspectedItems.get());
            Assertions.assertEquals(entityCount, feature.getItemIndex().size());
            Mockito.verify(chunk).getEntities();
            Mockito.verify(chunk).getTileEntities(
                Mockito.<Predicate<? super Block>>any(),
                Mockito.eq(false));
            feature.onDeactivate();
        }
    }

    private World world(UUID worldId, String name) {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getUID()).thenReturn(worldId);
        Mockito.when(world.getKey()).thenReturn(new NamespacedKey("react-test", name));
        Mockito.when(world.getName()).thenReturn(name);
        Mockito.when(world.getSpawnLocation()).thenReturn(new Location(world, 0D, 64D, 0D));
        return world;
    }

    private Item item(UUID itemId, Location location, AtomicInteger inspectedItems) {
        return (Item) Proxy.newProxyInstance(
            Item.class.getClassLoader(),
            new Class<?>[]{Item.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> {
                    inspectedItems.incrementAndGet();
                    yield itemId;
                }
                case "isValid" -> true;
                case "isDead" -> false;
                case "getLocation" -> location.clone();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                case "toString" -> "PaperItemFixture[" + itemId + "]";
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0D;
    }

    private void runSyncJobsImmediately(MockedStatic<J> scheduling) {
        scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        });
    }
}
