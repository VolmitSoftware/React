package art.arcane.react.api.rendering;

import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.DirtyRect;
import art.arcane.react.platform.Facing;
import art.arcane.react.platform.FrameSnapshot;
import art.arcane.react.platform.MapHost;
import art.arcane.react.platform.MapHostListener;
import art.arcane.react.platform.MapItemTags;
import art.arcane.react.platform.MapItemView;
import art.arcane.react.platform.ViewerSnapshot;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

class MapRuntimeTest {
  private static final UUID OVERWORLD = UUID.randomUUID();
  private static final UUID NETHER = UUID.randomUUID();

  @Test
  void createdMapsAreClaimedAndTagged() {
    FakeHost host = new FakeHost(true, true);
    MapRuntime<Item> runtime = runtime(host);

    Item item = runtime.createMap(OVERWORLD, new RendererUnknown());

    Assertions.assertTrue(item.mapId >= 0);
    Assertions.assertTrue(host.claims.contains(item.mapId));
    Assertions.assertTrue(item.tags.reactFlag());
    Assertions.assertEquals("unknown", item.tags.rendererId());
    Assertions.assertEquals("Tag: " + item.tags.token(), item.lore.get(3));
    Assertions.assertTrue(runtime.isReactMapItem(item));
  }

  @Test
  void inventoryMapCarriedIntoAnotherWorldKeepsItsId() {
    FakeHost host = new FakeHost(true, true);
    MapRuntime<Item> runtime = runtime(host);
    Item item = runtime.createMap(OVERWORLD, new RendererUnknown());
    int mapId = item.mapId;
    UUID viewer = host.addViewer(NETHER, 0D, 0D, 0D);
    host.inventories.get(viewer).add(item);

    runtime.repairInventory(viewer, NETHER, false);

    Assertions.assertEquals(mapId, item.mapId);
  }

  @Test
  void worldBoundFrameMapsAreRemintedOnWorldMismatch() {
    FakeHost host = new FakeHost(true, true);
    MapRuntime<Item> runtime = runtime(host);
    Item item = runtime.createMap(OVERWORLD, new RendererUnknown());
    int original = item.mapId;
    FrameSnapshot<Item> frame = host.addFrame(NETHER, 0, 64, 0, item);

    runtime.refreshFrame(frame, false, runtime.memoizedViewers());

    Assertions.assertNotEquals(original, item.mapId);
    Assertions.assertEquals(NETHER, host.mapWorlds.get(item.mapId));
    Assertions.assertFalse(host.claims.contains(original), "the abandoned id loses its claim");
    Assertions.assertEquals(1, runtime.trackedFrameCount());
  }

  @Test
  void unboundHostsRestoreMissingDataInsteadOfReminting() {
    FakeHost host = new FakeHost(false, false);
    MapRuntime<Item> runtime = runtime(host);
    Item item = runtime.createMap(OVERWORLD, new RendererUnknown());
    int mapId = item.mapId;
    host.mapWorlds.remove(mapId);
    UUID viewer = host.addViewer(OVERWORLD, 0D, 0D, 0D);
    host.inventories.get(viewer).add(item);

    runtime.repairInventory(viewer, OVERWORLD, false);

    Assertions.assertEquals(mapId, item.mapId);
    Assertions.assertEquals(OVERWORLD, host.mapWorlds.get(mapId));
  }

  @Test
  void framePushesReachOnlyViewersInRangeAndSightWithFullThenPartialRects() throws InterruptedException {
    FakeHost host = new FakeHost(true, true);
    MapRuntime<Item> runtime = runtime(host);
    Item item = runtime.createMap(OVERWORLD, new RendererUnknown());
    UUID near = host.addViewer(OVERWORLD, 2D, 64D, 2D);
    UUID far = host.addViewer(OVERWORLD, 500D, 64D, 500D);
    UUID blocked = host.addViewer(OVERWORLD, 3D, 64D, 0D);
    host.blockedSight.add(blocked);
    FrameSnapshot<Item> frame = host.addFrame(OVERWORLD, 0, 64, 0, item);

    long deadline = System.currentTimeMillis() + 2_000L;
    while (host.sentRects(near).isEmpty() && System.currentTimeMillis() < deadline) {
      runtime.refreshFrame(host.frame(frame.frameId(), true), false, runtime.memoizedViewers());
      Thread.sleep(5L);
    }

    Assertions.assertEquals(List.of(DirtyRect.FULL), host.sentRects(near));
    Assertions.assertTrue(host.sentRects(far).isEmpty());
    Assertions.assertTrue(host.sentRects(blocked).isEmpty());
    Assertions.assertEquals(DirtyRect.EMPTY, runtime.takePending(item.mapId, near, System.currentTimeMillis()),
        "an unchanged surface leaves nothing pending");
  }

  @Test
  void heldMapsAreDeliveredWhenTheHostDoesNotPullThem() {
    FakeHost host = new FakeHost(false, false);
    MapRuntime<Item> runtime = runtime(host);
    Item item = runtime.createMap(OVERWORLD, new RendererUnknown());
    UUID holder = host.addViewer(OVERWORLD, 0D, 64D, 0D);
    host.holding.put(holder, item.mapId);

    runtime.onTick(System.currentTimeMillis());

    Assertions.assertEquals(List.of(DirtyRect.FULL), host.sentRects(holder));
    Assertions.assertFalse(host.published.isEmpty(), "composed pixels are published to the store");
  }

  @Test
  void stopReleasesEveryClaim() {
    FakeHost host = new FakeHost(true, true);
    MapRuntime<Item> runtime = runtime(host);
    runtime.createMap(OVERWORLD, new RendererUnknown());
    runtime.createMap(OVERWORLD, new RendererUnknown());

    runtime.stop();

    Assertions.assertTrue(host.claims.isEmpty());
    Assertions.assertFalse(host.bound);
  }

  @Test
  void chunkRepairScansOnlyTheConfiguredBatch() {
    FakeHost host = new FakeHost(true, true);
    MapRuntimeConfig config = config();
    Mockito.when(config.getItemFrameChunkBatchSize()).thenReturn(4);
    Mockito.when(config.getStartupBoostItemFrameChunkBatchSize()).thenReturn(4);
    MapRuntime<Item> runtime = new MapRuntime<Item>(host, config, new MapRendererRegistry());
    runtime.start();
    for (int chunk = 0; chunk < 1_000; chunk++) {
      host.loaded.add(new ChunkRef(OVERWORLD, chunk, 0));
    }

    runtime.seedOnlineState();
    runtime.maintenance();

    Assertions.assertEquals(4, host.chunkScans);
    Assertions.assertEquals(4, runtime.pendingChunkRepairs());
  }

  private static MapRuntime<Item> runtime(FakeHost host) {
    MapRuntime<Item> runtime = new MapRuntime<Item>(host, config(), new MapRendererRegistry());
    runtime.start();
    return runtime;
  }

  private static MapRuntimeConfig config() {
    MapRuntimeConfig config = Mockito.mock(MapRuntimeConfig.class);
    Mockito.when(config.getFrameMapPushIntervalMs()).thenReturn(600L);
    Mockito.when(config.getFrameMapIdlePushIntervalMs()).thenReturn(6_000L);
    Mockito.when(config.getFrameMapPushRadiusBlocks()).thenReturn(24D);
    Mockito.when(config.getFrameMapLookDotThreshold()).thenReturn(0.45D);
    Mockito.when(config.isFrameMapRequireLineOfSight()).thenReturn(true);
    Mockito.when(config.getFrameMapPushStateRetentionMs()).thenReturn(600_000L);
    Mockito.when(config.getHeldMapRedrawIntervalMs()).thenReturn(150L);
    Mockito.when(config.getFrameMapRedrawIntervalMs()).thenReturn(300L);
    Mockito.when(config.getFrameMapPushBatchSize()).thenReturn(128);
    Mockito.when(config.getMegamapMaxTiles()).thenReturn(32);
    Mockito.when(config.getItemFrameChunkBatchSize()).thenReturn(8);
    Mockito.when(config.getInventoryRepairBatchSize()).thenReturn(3);
    Mockito.when(config.getMaintenanceTickIntervalMs()).thenReturn(500L);
    return config;
  }

  static final class Item {
    private int mapId = -1;
    private MapItemTags tags = MapItemTags.NONE;
    private List<String> lore = List.of();
  }

  private static final class FakeHost implements MapHost<Item> {
    private final boolean worldBound;
    private final boolean pullsHeld;
    private final Set<Integer> claims = new HashSet<>();
    private final Map<Integer, UUID> mapWorlds = new HashMap<>();
    private final Map<UUID, ViewerSnapshot> viewers = new HashMap<>();
    private final Map<UUID, Integer> holding = new HashMap<>();
    private final Map<UUID, List<Item>> inventories = new HashMap<>();
    private final Map<UUID, List<DirtyRect>> sent = new HashMap<>();
    private final Map<UUID, FrameSnapshot<Item>> frames = new HashMap<>();
    private final Set<UUID> blockedSight = new HashSet<>();
    private final List<ChunkRef> loaded = new ArrayList<>();
    private final List<Integer> published = new ArrayList<>();
    private int nextMapId;
    private int chunkScans;
    private boolean bound;

    private FakeHost(boolean worldBound, boolean pullsHeld) {
      this.worldBound = worldBound;
      this.pullsHeld = pullsHeld;
    }

    private UUID addViewer(UUID world, double x, double y, double z) {
      UUID id = UUID.randomUUID();
      viewers.put(id, new ViewerSnapshot(id, world, x, y, z, x, y + 1.6D, z, 0D, 0D, -1D, true, -1, -1));
      inventories.put(id, new ArrayList<>());
      return id;
    }

    private FrameSnapshot<Item> addFrame(UUID world, int x, int y, int z, Item item) {
      FrameSnapshot<Item> frame = new FrameSnapshot<Item>(UUID.randomUUID(), world, x, y, z, x + 0.5D, y + 0.5D, z + 0.03D,
          Facing.SOUTH, 0, item, null);
      frames.put(frame.frameId(), frame);
      return frame;
    }

    private List<DirtyRect> sentRects(UUID viewer) {
      return sent.getOrDefault(viewer, List.of());
    }

    @Override
    public int allocateMapId(UUID worldId) {
      int id = nextMapId++;
      mapWorlds.put(id, worldId);
      return id;
    }

    @Override
    public void ensureMapData(int mapId, UUID worldId) {
      mapWorlds.put(mapId, worldId);
    }

    @Override
    public boolean mapIdsAreWorldBound() {
      return worldBound;
    }

    @Override
    public UUID mapWorld(int mapId) {
      return mapWorlds.get(mapId);
    }

    @Override
    public void claim(int mapId) {
      claims.add(mapId);
    }

    @Override
    public void release(int mapId) {
      claims.remove(mapId);
    }

    @Override
    public boolean isClaimed(int mapId) {
      return claims.contains(mapId);
    }

    @Override
    public void publish(int mapId, byte[] surface, DirtyRect changed) {
      published.add(mapId);
    }

    @Override
    public void send(UUID viewerId, int mapId, byte[] surface, DirtyRect rect) {
      sent.computeIfAbsent(viewerId, ignored -> new ArrayList<>()).add(rect);
    }

    @Override
    public boolean pullsHeldMaps() {
      return pullsHeld;
    }

    @Override
    public MapItemView readMapItem(Item item) {
      return item == null ? null : new MapItemView(item.mapId, item.tags, item.lore, false);
    }

    @Override
    public Item writeMapItem(Item item, int mapId, MapItemTags tags, List<String> lore) {
      item.mapId = mapId;
      item.tags = tags;
      item.lore = lore;
      return item;
    }

    @Override
    public Item newMapItem(int mapId, MapItemTags tags, List<String> lore) {
      return writeMapItem(new Item(), mapId, tags, lore);
    }

    @Override
    public void giveToMainHand(UUID viewerId, Item item) {
      inventories.get(viewerId).add(0, item);
    }

    @Override
    public void giveToInventory(UUID viewerId, Item item) {
      inventories.get(viewerId).add(item);
    }

    @Override
    public void rewriteInventoryMaps(UUID viewerId, Function<Item, Item> rewrite) {
      List<Item> items = inventories.get(viewerId);
      for (int i = 0; i < items.size(); i++) {
        Item replacement = rewrite.apply(items.get(i));
        if (replacement != null) {
          items.set(i, replacement);
        }
      }
    }

    @Override
    public boolean moveToMainHand(UUID viewerId, Predicate<Item> match) {
      return false;
    }

    @Override
    public boolean framesInChunk(UUID worldId, int chunkX, int chunkZ, Consumer<FrameSnapshot<Item>> sink) {
      chunkScans++;
      return true;
    }

    @Override
    public FrameSnapshot<Item> frame(UUID frameId, boolean withItem) {
      return frames.get(frameId);
    }

    @Override
    public boolean frameExists(UUID frameId) {
      return frames.containsKey(frameId);
    }

    @Override
    public void setFrameItemQuietly(FrameSnapshot<Item> frame, Item item) {
      frames.put(frame.frameId(), frame.withItem(item));
    }

    @Override
    public void setFrameRotation(UUID frameId, int rotation) {
    }

    @Override
    public boolean isWorldLoaded(UUID worldId) {
      return true;
    }

    @Override
    public UUID defaultWorld() {
      return OVERWORLD;
    }

    @Override
    public void loadedChunks(Consumer<ChunkRef> sink) {
      loaded.forEach(sink);
    }

    @Override
    public List<ViewerSnapshot> viewers() {
      List<ViewerSnapshot> snapshots = new ArrayList<>();
      for (ViewerSnapshot viewer : viewers.values()) {
        int held = holding.getOrDefault(viewer.viewerId(), -1);
        snapshots.add(new ViewerSnapshot(viewer.viewerId(), viewer.worldId(), viewer.x(), viewer.y(), viewer.z(),
            viewer.eyeX(), viewer.eyeY(), viewer.eyeZ(), viewer.dirX(), viewer.dirY(), viewer.dirZ(),
            viewer.hasDirection(), held, -1));
      }
      return snapshots;
    }

    @Override
    public UUID viewerWorld(UUID viewerId) {
      ViewerSnapshot viewer = viewers.get(viewerId);
      return viewer == null ? null : viewer.worldId();
    }

    @Override
    public boolean isOnline(UUID viewerId) {
      return viewers.containsKey(viewerId);
    }

    @Override
    public boolean hasLineOfSight(UUID viewerId, UUID frameId, UUID worldId, double x, double y, double z) {
      return !blockedSight.contains(viewerId);
    }

    @Override
    public boolean runOnFrameOwner(UUID frameId, long delayTicks, Runnable work, Runnable retired) {
      work.run();
      return true;
    }

    @Override
    public boolean runOnViewerOwner(UUID viewerId, long delayTicks, Runnable work) {
      work.run();
      return true;
    }

    @Override
    public boolean runOnChunkOwner(UUID worldId, int chunkX, int chunkZ, Runnable work) {
      work.run();
      return true;
    }

    @Override
    public void bind(MapHostListener<Item> listener) {
      bound = true;
    }

    @Override
    public void unbind() {
      bound = false;
    }
  }
}
