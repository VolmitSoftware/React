package art.arcane.react.content.feature;

import art.arcane.react.core.controller.HopperPositionIndex;
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.Facing;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.HopperHost;
import art.arcane.react.platform.PlatformKind;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

class HopperFeaturesCoreTest {
  private final UUID worldId = UUID.randomUUID();
  private final Object worldHandle = new Object();
  private HopperHost hoppers;
  private BlockHost blocks;
  private ChunkHost chunks;
  private WorldHost worlds;

  @BeforeEach
  void bindPlatform() {
    hoppers = Mockito.mock(HopperHost.class);
    blocks = Mockito.mock(BlockHost.class);
    chunks = Mockito.mock(ChunkHost.class);
    worlds = Mockito.mock(WorldHost.class);
    ReactWorld world = Mockito.mock(ReactWorld.class);
    Mockito.when(world.id()).thenReturn(worldId);
    Mockito.when(world.handle()).thenReturn(worldHandle);
    Mockito.when(worlds.world(worldId)).thenReturn(Optional.of(world));
    Mockito.when(chunks.isLoaded(Mockito.eq(worldId), Mockito.anyInt(), Mockito.anyInt())).thenReturn(true);
    Mockito.when(blocks.onPlace(Mockito.any(), Mockito.anyBoolean(), Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(blocks.onBreak(Mockito.any(), Mockito.anyBoolean(), Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(blocks.onPiston(Mockito.any(), Mockito.anyBoolean(), Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(chunks.onChunkLoad(Mockito.any(), Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(chunks.onChunkUnload(Mockito.any(), Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(chunks.onWorldUnload(Mockito.any(), Mockito.anyBoolean(), Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(hoppers.onIndexChange(Mockito.any())).thenReturn(Subscription.NONE);
    ReactPlatform platform = Mockito.mock(ReactPlatform.class);
    Mockito.when(platform.kind()).thenReturn(PlatformKind.MODDED);
    Mockito.when(platform.hopperHost()).thenReturn(Optional.of(hoppers));
    Mockito.when(platform.blockHost()).thenReturn(Optional.of(blocks));
    Mockito.when(platform.chunkHost()).thenReturn(Optional.of(chunks));
    Mockito.when(platform.worldHost()).thenReturn(Optional.of(worlds));
    ReactPlatforms.bind(platform);
  }

  @AfterEach
  void unbindPlatform() {
    ReactPlatforms.unbind();
  }

  @Test
  void straightChainSkipsMiddleTicksAndMovesHeadToTail() throws Exception {
    Object chunk = new Object();
    List<HopperHost.HopperBlock> line = new ArrayList<>();
    for (int x = 0; x < 5; x++) {
      line.add(new HopperHost.HopperBlock(x, 64, 0, Facing.EAST));
    }
    Mockito.when(hoppers.chunk(worldId, 0, 0)).thenReturn(chunk);
    Mockito.when(hoppers.hoppers(chunk)).thenReturn(line);
    Mockito.when(hoppers.blockType(worldId, 5, 64, 0)).thenReturn("CHEST");
    Mockito.when(hoppers.blockType(worldId, 0, 65, 0)).thenReturn("AIR");
    Mockito.when(hoppers.isContainer(worldId, 5, 64, 0)).thenReturn(true);
    Mockito.when(hoppers.isOwnedByCurrentRegion(Mockito.eq(worldId), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
        .thenReturn(true);
    Mockito.when(hoppers.transferOne(Mockito.eq(worldId), Mockito.eq(0), Mockito.eq(64), Mockito.eq(0),
            Mockito.eq(5), Mockito.eq(64), Mockito.eq(0), Mockito.any(BooleanSupplier.class)))
        .thenAnswer(invocation -> invocation.<BooleanSupplier>getArgument(7).getAsBoolean());
    AtomicReference<HopperHost.TickFilter> filter = new AtomicReference<>();
    Mockito.when(hoppers.installTickFilter(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
      filter.set(invocation.getArgument(0));
      invocation.<Consumer<Boolean>>getArgument(1).accept(true);
      return Subscription.NONE;
    });
    Mockito.when(chunks.nextLoaded(Mockito.anyInt())).thenReturn(List.of(new ChunkRef(worldId, 0, 0)));

    FeatureHopperChainCoalescing feature = new FeatureHopperChainCoalescing();
    set(feature, "featureActMode", true);
    set(feature, "featureBucketBypass", true);
    set(feature, "engageOnTickMs", 0D);
    set(feature, "releaseOnTickMs", -1D);
    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.runChunk(Mockito.same(worldHandle), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(3).run();
            return true;
          });
      feature.onActivate();
      feature.onTick();

      Assertions.assertEquals(1L, feature.chainsDetectedSnapshot());
      Assertions.assertEquals(5D, feature.avgChainLengthSnapshot());
      Assertions.assertEquals(1L, feature.fastPathChainCountSnapshot());
      Assertions.assertFalse(feature.isMeasurementOnly());
      Assertions.assertFalse(filter.get().skip(worldId, 0, 64, 0));
      Assertions.assertTrue(filter.get().skip(worldId, 2, 64, 0));
      Assertions.assertFalse(filter.get().skip(worldId, 4, 64, 0));
      Assertions.assertEquals(1L, feature.readAndResetSkippedHopperTicks());
      Assertions.assertEquals((long) feature.transfersPerChainPerEvaluation(), feature.readAndResetSynthesizedTransfers());
      feature.onDeactivate();
    }
  }

  @Test
  void branchedOrTurningHoppersBreakTheChain() throws Exception {
    Object chunk = new Object();
    List<HopperHost.HopperBlock> hoppersInChunk = List.of(
        new HopperHost.HopperBlock(0, 64, 0, Facing.EAST),
        new HopperHost.HopperBlock(1, 64, 0, Facing.EAST),
        new HopperHost.HopperBlock(2, 64, 0, Facing.SOUTH),
        new HopperHost.HopperBlock(2, 64, 1, Facing.SOUTH),
        new HopperHost.HopperBlock(2, 64, 2, Facing.SOUTH));
    Mockito.when(hoppers.chunk(worldId, 0, 0)).thenReturn(chunk);
    Mockito.when(hoppers.hoppers(chunk)).thenReturn(hoppersInChunk);
    Mockito.when(chunks.nextLoaded(Mockito.anyInt())).thenReturn(List.of(new ChunkRef(worldId, 0, 0)));

    FeatureHopperChainCoalescing feature = new FeatureHopperChainCoalescing();
    set(feature, "minChainLength", 3);
    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.runChunk(Mockito.same(worldHandle), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(3).run();
            return true;
          });
      feature.onActivate();
      feature.onTick();

      Assertions.assertEquals(1L, feature.chainsDetectedSnapshot());
      Assertions.assertEquals(3D, feature.avgChainLengthSnapshot());
      Assertions.assertTrue(feature.isMeasurementOnly());
      feature.onDeactivate();
    }
  }

  @Test
  void itemIndexFollowsItemsAndHoppersAndForgetsUnloadedChunks() {
    Object item = new Object();
    UUID itemId = UUID.randomUUID();
    Mockito.when(hoppers.itemId(item)).thenReturn(itemId);
    Mockito.when(hoppers.isLiveItem(item)).thenReturn(true);
    Mockito.when(hoppers.itemLocation(item)).thenReturn(new GameLocation(worldId, 20.5D, 70D, -3D));

    FeatureHopperItemIndex feature = new FeatureHopperItemIndex();
    feature.onActivate();
    feature.itemSeen(item);
    feature.hopperPlaced(worldId, 20, 64, -3);
    feature.hopperPlaced(worldId, 21, 64, -3);
    feature.hopperGone(worldId, 21, 64, -3);

    Assertions.assertArrayEquals(new UUID[]{itemId}, feature.getItemIndex().itemIdsInChunk(worldId, 1, -1));
    Assertions.assertArrayEquals(new long[]{HopperPositionIndex.packPos(20, 64, -3)},
        feature.getPositionIndex().hoppersInChunk(worldId, 1, -1));

    feature.itemGone(itemId);
    Assertions.assertEquals(0, feature.getItemIndex().size());

    feature.itemSeen(item);
    feature.onChunkUnload(new ChunkRef(worldId, 1, -1));
    Assertions.assertEquals(0, feature.getItemIndex().size());
    Assertions.assertEquals(0, feature.getPositionIndex().hopperCount(worldId));
    feature.onDeactivate();
  }

  @Test
  void deadItemsAreNeverIndexed() {
    Object item = new Object();
    UUID itemId = UUID.randomUUID();
    Mockito.when(hoppers.itemId(item)).thenReturn(itemId);
    Mockito.when(hoppers.isLiveItem(item)).thenReturn(false);

    FeatureHopperItemIndex feature = new FeatureHopperItemIndex();
    feature.onActivate();
    feature.itemSeen(item);

    Assertions.assertEquals(0, feature.getItemIndex().size());
    feature.onDeactivate();
  }

  @Test
  void skippedHoppersAreDrainedIntoTheTailRecipientFirst() throws Exception {
    Map<Integer, Integer> items = new HashMap<>();
    items.put(0, 3);
    items.put(1, 10);
    items.put(5, 0);
    FeatureHopperChainCoalescing feature = engagedChain(items, true, 64);
    int budget = feature.transfersPerChainPerEvaluation();

    Assertions.assertEquals(budget, items.get(5));
    Assertions.assertEquals(10 - budget, items.get(1));
    Assertions.assertEquals(3, items.get(0));
    Assertions.assertEquals((long) budget, feature.readAndResetSynthesizedTransfers());
    feature.onDeactivate();
  }

  @Test
  void headFeedsTheRecipientOnceSkippedHoppersAreEmpty() throws Exception {
    Map<Integer, Integer> items = new HashMap<>();
    items.put(0, 1);
    items.put(2, 1);
    items.put(5, 0);
    FeatureHopperChainCoalescing feature = engagedChain(items, true, 64);

    Assertions.assertEquals(2, items.get(5));
    Assertions.assertEquals(0, items.get(0));
    Assertions.assertEquals(0, items.get(2));
    Assertions.assertEquals(2L, feature.readAndResetSynthesizedTransfers());
    feature.onDeactivate();
  }

  @Test
  void fullRecipientStopsTransfers() throws Exception {
    Map<Integer, Integer> items = new HashMap<>();
    items.put(1, 10);
    items.put(5, 0);
    FeatureHopperChainCoalescing feature = engagedChain(items, true, 1);

    Assertions.assertEquals(1, items.get(5));
    Assertions.assertEquals(9, items.get(1));
    Assertions.assertEquals(1L, feature.readAndResetSynthesizedTransfers());
    feature.onDeactivate();
  }

  @Test
  void measurementModeNeitherSkipsNorMovesItems() throws Exception {
    Map<Integer, Integer> items = new HashMap<>();
    items.put(1, 10);
    items.put(5, 0);
    FeatureHopperChainCoalescing feature = engagedChain(items, false, 64);

    Assertions.assertEquals(0, items.get(5));
    Assertions.assertEquals(0L, feature.readAndResetSynthesizedTransfers());
    Mockito.verify(hoppers, Mockito.never()).installTickFilter(Mockito.any(), Mockito.any());
    feature.onDeactivate();
  }

  private FeatureHopperChainCoalescing engagedChain(Map<Integer, Integer> items, boolean actMode, int recipientCapacity)
      throws Exception {
    Object chunk = new Object();
    List<HopperHost.HopperBlock> line = new ArrayList<>();
    for (int x = 0; x < 5; x++) {
      line.add(new HopperHost.HopperBlock(x, 64, 0, Facing.EAST));
    }
    Mockito.when(hoppers.chunk(worldId, 0, 0)).thenReturn(chunk);
    Mockito.when(hoppers.hoppers(chunk)).thenReturn(line);
    Mockito.when(hoppers.blockType(worldId, 5, 64, 0)).thenReturn("CHEST");
    Mockito.when(hoppers.blockType(worldId, 0, 65, 0)).thenReturn("AIR");
    Mockito.when(hoppers.isContainer(worldId, 5, 64, 0)).thenReturn(true);
    Mockito.when(hoppers.isOwnedByCurrentRegion(Mockito.eq(worldId), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
        .thenReturn(true);
    Mockito.when(hoppers.transferOne(Mockito.eq(worldId), Mockito.anyInt(), Mockito.eq(64), Mockito.eq(0),
            Mockito.eq(5), Mockito.eq(64), Mockito.eq(0), Mockito.any(BooleanSupplier.class)))
        .thenAnswer(invocation -> {
          int from = invocation.<Integer>getArgument(1);
          int available = items.getOrDefault(from, 0);
          int stored = items.getOrDefault(5, 0);
          if (available <= 0 || stored >= recipientCapacity || !invocation.<BooleanSupplier>getArgument(7).getAsBoolean()) {
            return false;
          }
          items.put(from, available - 1);
          items.put(5, stored + 1);
          return true;
        });
    Mockito.when(hoppers.installTickFilter(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
      invocation.<Consumer<Boolean>>getArgument(1).accept(true);
      return Subscription.NONE;
    });
    Mockito.when(chunks.nextLoaded(Mockito.anyInt())).thenReturn(List.of(new ChunkRef(worldId, 0, 0)));

    FeatureHopperChainCoalescing feature = new FeatureHopperChainCoalescing();
    set(feature, "featureActMode", actMode);
    set(feature, "featureBucketBypass", true);
    set(feature, "engageOnTickMs", 0D);
    set(feature, "releaseOnTickMs", -1D);
    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.runChunk(Mockito.same(worldHandle), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(3).run();
            return true;
          });
      feature.onActivate();
      feature.onTick();
    }
    return feature;
  }

  private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
