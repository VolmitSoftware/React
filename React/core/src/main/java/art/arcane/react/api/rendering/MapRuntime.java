package art.arcane.react.api.rendering;

import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.DirtyRect;
import art.arcane.react.platform.Facing;
import art.arcane.react.platform.FrameSnapshot;
import art.arcane.react.platform.MapHost;
import art.arcane.react.platform.MapHostListener;
import art.arcane.react.platform.MapItemTags;
import art.arcane.react.platform.MapItemView;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ViewerSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Platform-free React map runtime: renderer bindings, item metadata, frame tracking, megamap walls, viewer
 * selection, push cadence and delivery. Everything platform specific goes through {@link MapHost}.
 */
public final class MapRuntime<I> implements MapHostListener<I> {
  private static final int MEGAMAP_SPLITS_PER_PASS = 8;
  private static final int MEGAMAP_ROTATION_RESETS_PER_PASS = 8;

  private final MapHost<I> host;
  private final MapRuntimeConfig config;
  private final MapRendererRegistry registry;
  private final MapDelivery delivery = new MapDelivery();
  private final Map<Integer, MapComposer> composers = new ConcurrentHashMap<>();
  private final Map<FramePushKey, Long> pushMsByViewerKey = new ConcurrentHashMap<>();
  private final Map<Integer, Long> frameMapLastSeenByMapId = new ConcurrentHashMap<>();
  private final Map<UUID, ActiveFrameMap> activeFrameMaps = new ConcurrentHashMap<>();
  private final ConcurrentLinkedQueue<ActiveFrameMap> activeFrameMapQueue = new ConcurrentLinkedQueue<>();
  private final Map<UUID, Map<Long, FrameChunkRepair>> frameChunkRepairsByWorld = new ConcurrentHashMap<>();
  private final ConcurrentLinkedQueue<FrameChunkRepair> frameChunkRepairQueue = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<FrameChunkSeed> frameChunkSeedQueue = new ConcurrentLinkedQueue<>();
  private final Map<Integer, UUID> frameIdsByMapId = new ConcurrentHashMap<>();
  private final Set<UUID> megamapSplitsInFlight = ConcurrentHashMap.newKeySet();
  private final AtomicInteger megamapStateVersion = new AtomicInteger(1);
  private volatile MegamapGrid.MegamapSolution megamapSolution = MegamapGrid.MegamapSolution.EMPTY;
  private volatile boolean active;
  private int megamapSolvedVersion;
  private boolean megamapSolvedEnabled;
  private long lastInventoryRepairMs;
  private long lastItemFrameRepairMs;
  private long lastFramePushStatePruneMs;
  private long lastMaintenanceMs;
  private int inventoryRepairCursor;
  private volatile long startupBoostUntilMs;

  public MapRuntime(MapHost<I> host, MapRuntimeConfig config, MapRendererRegistry registry) {
    this.host = Objects.requireNonNull(host, "host");
    this.config = Objects.requireNonNull(config, "config");
    this.registry = registry == null ? new MapRendererRegistry() : registry;
  }

  public MapHost<I> host() {
    return host;
  }

  public MapRendererRegistry registry() {
    return registry;
  }

  public boolean isActive() {
    return active;
  }

  public void start() {
    active = true;
    megamapSolution = MegamapGrid.MegamapSolution.EMPTY;
    megamapSolvedVersion = 0;
    megamapSolvedEnabled = !config.isMegamapEnabled();
    boostStartup();
    host.bind(this);
  }

  public void stop() {
    active = false;
    host.unbind();
    for (MapComposer composer : composers.values()) {
      composer.close();
      host.release(composer.mapId());
    }
    composers.clear();
    delivery.clear();
    pushMsByViewerKey.clear();
    frameMapLastSeenByMapId.clear();
    activeFrameMaps.clear();
    activeFrameMapQueue.clear();
    frameChunkRepairsByWorld.clear();
    frameChunkRepairQueue.clear();
    frameChunkSeedQueue.clear();
    frameIdsByMapId.clear();
    megamapSplitsInFlight.clear();
    registry.clearCaches();
    megamapSolution = MegamapGrid.MegamapSolution.EMPTY;
    megamapSolvedVersion = 0;
  }

  public void boostStartup() {
    startupBoostUntilMs = System.currentTimeMillis() + Math.max(0L, config.getStartupBoostDurationMs());
  }

  /**
   * Repairs every online viewer's inventory and queues the loaded chunks for frame repair.
   */
  public void seedOnlineState() {
    for (ViewerSnapshot viewer : host.viewers()) {
      UUID viewerId = viewer.viewerId();
      host.runOnViewerOwner(viewerId, 0L, () -> onViewerJoin(viewerId));
    }

    Map<UUID, List<ChunkRef>> byWorld = new LinkedHashMap<>();
    host.loadedChunks(chunk -> byWorld.computeIfAbsent(chunk.worldId(), ignored -> new ArrayList<>()).add(chunk));
    for (Map.Entry<UUID, List<ChunkRef>> entry : byWorld.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        frameChunkSeedQueue.offer(new FrameChunkSeed(entry.getKey(), entry.getValue()));
      }
    }
  }

  // ---- map ids and composition ----------------------------------------------------------

  public int allocate(UUID worldId, ReactRenderer renderer) {
    if (worldId == null) {
      return -1;
    }

    int mapId = host.allocateMapId(worldId);
    if (mapId >= 0) {
      bind(mapId, worldId, renderer);
    }
    return mapId;
  }

  public void bind(int mapId, UUID worldId, ReactRenderer renderer) {
    if (mapId < 0 || renderer == null || !active) {
      return;
    }

    MapComposer previous = composers.put(mapId, new MapComposer(mapId, worldId, renderer));
    if (previous != null) {
      previous.close();
    }
    delivery.forget(mapId);
    host.claim(mapId);
  }

  public void release(int mapId) {
    if (mapId < 0) {
      return;
    }

    MapComposer removed = composers.remove(mapId);
    if (removed != null) {
      removed.close();
      host.release(mapId);
    }
    delivery.forget(mapId);
  }

  public MapComposer composer(int mapId) {
    return composers.get(mapId);
  }

  public String liveRendererId(int mapId) {
    MapComposer composer = composers.get(mapId);
    if (composer == null || !composer.isActive() || !MapRendererRegistry.isSpecific(composer.rendererId())) {
      return RendererUnknown.ID;
    }
    return composer.rendererId();
  }

  private boolean hasExpectedBinding(int mapId, ReactRenderer renderer) {
    MapComposer composer = composers.get(mapId);
    return composer != null
        && composer.isActive()
        && host.isClaimed(mapId)
        && composer.normalizedRendererId().equals(registry.metadataFor(renderer.getId()).normalizedId());
  }

  /**
   * Draws the map if due. The changed area is queued for every receiver of the map and published to the host.
   */
  public DirtyRect compose(int mapId, UUID viewerId, long now) {
    MapComposer composer = composers.get(mapId);
    if (composer == null || !active) {
      return DirtyRect.EMPTY;
    }

    DirtyRect changed = composer.compose(
        registry,
        new MapRenderTarget(mapId, composer.worldId(), viewerId),
        megamapTileFor(mapId),
        megamapDefectFor(mapId),
        redrawIntervalMsFor(mapId),
        config.getMegamapMaxTiles(),
        now
    );
    if (!changed.isEmpty()) {
      delivery.changed(mapId, changed);
      host.publish(mapId, composer.published(), changed);
    }
    return changed;
  }

  /**
   * The area a receiver has not seen yet. A receiver's first take is the full surface.
   */
  public DirtyRect takePending(int mapId, UUID receiver, long now) {
    return delivery.take(mapId, receiver, now);
  }

  public void resetReceiver(int mapId, UUID receiver) {
    delivery.reset(mapId, receiver);
  }

  private void deliver(UUID viewerId, int mapId, long now, boolean skipEmpty) {
    MapComposer composer = composers.get(mapId);
    if (composer == null) {
      host.send(viewerId, mapId, null, DirtyRect.FULL);
      return;
    }

    compose(mapId, viewerId, now);
    DirtyRect pending = delivery.take(mapId, viewerId, now);
    if (skipEmpty && pending.isEmpty()) {
      return;
    }
    host.send(viewerId, mapId, composer.published(), pending);
  }

  @Override
  public DirtyRect pull(int mapId, ViewerSnapshot viewer, long nowMs) {
    if (!active || !composers.containsKey(mapId) || !shouldRenderForViewer(mapId, viewer)) {
      return DirtyRect.EMPTY;
    }
    compose(mapId, viewer.viewerId(), nowMs);
    return delivery.take(mapId, viewer.viewerId(), nowMs);
  }

  @Override
  public byte[] published(int mapId) {
    MapComposer composer = composers.get(mapId);
    return composer == null ? null : composer.published();
  }

  public long redrawIntervalMsFor(int mapId) {
    if (isKnownFrameMap(mapId, System.currentTimeMillis())) {
      return Math.max(0L, config.getFrameMapRedrawIntervalMs());
    }
    return Math.max(0L, config.getHeldMapRedrawIntervalMs());
  }

  public boolean shouldRenderForViewer(int mapId, ViewerSnapshot viewer) {
    if (viewer == null || viewer.worldId() == null || mapId < 0) {
      return false;
    }

    long now = System.currentTimeMillis();
    FrameAnchor anchor = frameAnchor(mapId);
    boolean holding = viewer.isHolding(mapId);
    boolean outsideRange = config.isFrameMapPushOutsideRangeWhenHolding();
    if (anchor == null) {
      if (isKnownFrameMap(mapId, now)) {
        return holding && outsideRange;
      }
      return true;
    }

    if (!(holding && outsideRange)) {
      if (!anchor.worldId().equals(viewer.worldId())) {
        return false;
      }

      double dx = viewer.x() - anchor.x();
      double dy = viewer.y() - anchor.y();
      double dz = viewer.z() - anchor.z();
      if ((dx * dx) + (dy * dy) + (dz * dz) > effectiveFrameMapPushRadiusSq()) {
        return false;
      }
    }

    return true;
  }

  // ---- items --------------------------------------------------------------------------------

  public boolean isReactMap(MapItemView view) {
    if (view == null) {
      return false;
    }

    MapItemTags tags = view.tags();
    return tags.reactFlag()
        || MapRendererRegistry.isSpecific(tags.rendererId())
        || MapRendererRegistry.isSpecific(MapRendererRegistry.rendererIdFromLore(view.legacyLore()));
  }

  public boolean isReactMapItem(I item) {
    return item != null && isReactMap(host.readMapItem(item));
  }

  public String storedRendererId(MapItemView view) {
    if (view == null) {
      return RendererUnknown.ID;
    }

    String tagId = view.tags().rendererId();
    if (MapRendererRegistry.isSpecific(tagId)) {
      return tagId;
    }

    String loreId = MapRendererRegistry.rendererIdFromLore(view.legacyLore());
    if (MapRendererRegistry.isSpecific(loreId)) {
      return loreId;
    }

    if (MapRendererRegistry.isUnknown(tagId)) {
      return RendererUnknown.ID;
    }
    return tagId;
  }

  public ReactRenderer rendererOf(I item) {
    MapItemView view = item == null ? null : host.readMapItem(item);
    if (!isReactMap(view)) {
      return registry.resolve(RendererUnknown.ID);
    }
    return registry.resolve(storedRendererId(view));
  }

  public ItemMetadata metadataFor(MapItemView view, ReactRenderer renderer) {
    MapItemTags tags = view == null ? MapItemTags.NONE : view.tags();
    List<String> currentLore = view == null ? List.of() : view.legacyLore();
    String storedId = storedRendererId(view);
    String resolvedId = Objects.toString(renderer.getId(), RendererUnknown.ID);
    String rendererId = resolvedId;
    if (MapRendererRegistry.isUnknown(resolvedId) && MapRendererRegistry.isSpecific(storedId)) {
      rendererId = storedId;
    }

    MapRendererRegistry.Metadata metadata = registry.metadataFor(rendererId);
    String previousToken = tags.token();
    String token = previousToken;
    if (token == null || token.isBlank()) {
      token = MapRendererRegistry.mapTokenFromLore(currentLore);
    }
    if (token == null || token.isBlank()) {
      token = UUID.randomUUID().toString();
    }

    List<String> lore = List.of(metadata.rendererLine(), metadata.scopeLine(), metadata.idLine(), "Tag: " + token);
    String storedTag = tags.rendererId();
    boolean changed = (view != null && view.hasCustomName())
        || !lore.equals(currentLore)
        || !Objects.equals(previousToken, token)
        || !tags.reactFlag()
        || !rendererId.equalsIgnoreCase(Objects.toString(storedTag, ""))
        || storedTag == null
        || storedTag.isBlank();
    return new ItemMetadata(new MapItemTags(true, rendererId, token), lore, changed);
  }

  public I createMap(UUID worldId, ReactRenderer renderer) {
    int mapId = allocate(worldId, renderer);
    ItemMetadata metadata = metadataFor(null, renderer);
    return host.newMapItem(mapId, metadata.tags(), metadata.lore());
  }

  /**
   * Rebinds a React map item to a renderer. Returns the stack to store back, or null when the item is not a React map.
   */
  public I setRenderer(I item, ReactRenderer renderer, UUID worldHint) {
    MapItemView view = item == null ? null : host.readMapItem(item);
    if (renderer == null || !isReactMap(view)) {
      return null;
    }

    int mapId = view.mapId();
    UUID mapWorld = mapId >= 0 ? host.mapWorld(mapId) : null;
    int targetId = mapId;
    if (mapWorld != null) {
      bind(mapId, mapWorld, renderer);
    } else {
      UUID world = worldHint != null ? worldHint : host.defaultWorld();
      if (world != null) {
        targetId = allocate(world, renderer);
        release(mapId);
      }
    }

    ItemMetadata metadata = metadataFor(view, renderer);
    return host.writeMapItem(item, targetId, metadata.tags(), metadata.lore());
  }

  public void giveMap(UUID viewerId, ReactRenderer renderer) {
    if (renderer == null || viewerId == null) {
      return;
    }
    host.giveToMainHand(viewerId, createMap(host.viewerWorld(viewerId), renderer));
  }

  public void giveMapToInventory(UUID viewerId, ReactRenderer renderer) {
    if (renderer == null || viewerId == null) {
      return;
    }
    host.giveToInventory(viewerId, createMap(host.viewerWorld(viewerId), renderer));
  }

  public boolean switchToMap(UUID viewerId) {
    return viewerId != null && host.moveToMainHand(viewerId, this::isReactMapItem);
  }

  public void repairInventory(UUID viewerId, UUID worldHint, boolean force) {
    if (viewerId == null || !active) {
      return;
    }
    host.rewriteInventoryMaps(viewerId, item -> repairInventoryItem(item, worldHint, force));
  }

  private I repairInventoryItem(I item, UUID worldHint, boolean force) {
    MapItemView view = item == null ? null : host.readMapItem(item);
    if (!isReactMap(view)) {
      return null;
    }

    I current = item;
    MapItemView working = view;
    boolean rekeyed = false;
    int rekeyedId = rekeyWallAssignedMapId(view, worldHint);
    if (rekeyedId >= 0) {
      ItemMetadata metadata = metadataFor(view, registry.resolve(storedRendererId(view)));
      current = host.writeMapItem(current, rekeyedId, metadata.tags(), metadata.lore());
      working = host.readMapItem(current);
      rekeyed = true;
    }

    I repaired = repairItem(current, working, worldHint, force, false);
    if (repaired != null) {
      return repaired;
    }
    return rekeyed ? current : null;
  }

  // An inventory copy of a map id that is tiling a wall would show that wall's tile fragment in hand;
  // re-key the copy to its own fresh id so held maps always render as a full standalone dashboard.
  private int rekeyWallAssignedMapId(MapItemView view, UUID worldHint) {
    if (!config.isMegamapEnabled() || !config.isMegamapSplitDuplicates()) {
      return -1;
    }

    MegamapGrid.MegamapSolution solution = megamapSolution;
    int mapId = view.mapId();
    if (solution == null || solution.tiles().isEmpty() || mapId < 0 || solution.tiles().get(mapId) == null) {
      return -1;
    }

    String storedRendererId = storedRendererId(view);
    ReactRenderer renderer = registry.resolve(storedRendererId);
    if (renderer == null) {
      return -1;
    }

    // Never re-key behind an unresolved renderer; that would swap the dashboard.
    if (MapRendererRegistry.isSpecific(storedRendererId) && MapRendererRegistry.isUnknown(renderer.getId())) {
      return -1;
    }

    UUID world = worldHint;
    if (world == null) {
      world = host.mapWorld(mapId);
    }
    if (world == null) {
      world = host.defaultWorld();
    }
    if (world == null) {
      return -1;
    }

    int fresh = allocate(world, renderer);
    if (fresh >= 0) {
      verbose(() -> "Megamap re-keyed inventory copy of wall map " + mapId + " -> " + fresh);
    }
    return fresh;
  }

  /**
   * Repairs renderer binding, map id and metadata. Returns the stack to store back, or null when nothing changed.
   */
  private I repairItem(I item, MapItemView view, UUID worldHint, boolean force, boolean remintOnWorldMismatch) {
    boolean changed = false;
    int mapId = view.mapId();
    MapItemView working = view;
    String storedRendererId = storedRendererId(view);
    if (MapRendererRegistry.isUnknown(storedRendererId) && mapId >= 0) {
      String liveRendererId = liveRendererId(mapId);
      if (MapRendererRegistry.isSpecific(liveRendererId)) {
        MapItemTags tags = view.tags();
        working = new MapItemView(mapId, new MapItemTags(tags.reactFlag(), liveRendererId, tags.token()), view.legacyLore(), view.hasCustomName());
        storedRendererId = liveRendererId;
        changed = true;
      }
    }

    ReactRenderer renderer = registry.resolve(storedRendererId);
    if (renderer == null) {
      renderer = registry.unknown();
    }
    if (renderer == null) {
      return null;
    }

    UUID mapWorld = mapId >= 0 ? host.mapWorld(mapId) : null;
    UUID world = worldHint;
    if (world == null) {
      world = mapWorld;
    }
    if (world == null) {
      world = host.defaultWorld();
    }

    if (mapId >= 0 && mapWorld == null && !host.mapIdsAreWorldBound() && world != null) {
      host.ensureMapData(mapId, world);
      mapWorld = world;
    }

    int targetId = mapId;
    boolean idChanged = mapId < 0
        || mapWorld == null
        || (remintOnWorldMismatch && host.mapIdsAreWorldBound() && world != null && !mapWorld.equals(world));
    if (idChanged) {
      if (world != null) {
        int fresh = allocate(world, renderer);
        if (fresh >= 0) {
          targetId = fresh;
          release(mapId);
        }
      }
    } else if (force || !hasExpectedBinding(mapId, renderer)) {
      bind(mapId, mapWorld, renderer);
    }

    ItemMetadata metadata = metadataFor(working, renderer);
    if (!idChanged && !metadata.changed() && !changed) {
      return null;
    }
    return host.writeMapItem(item, targetId, metadata.tags(), metadata.lore());
  }

  // ---- frames ------------------------------------------------------------------------------

  public void refreshFrame(FrameSnapshot<I> frame, boolean force, Supplier<ViewerIndex> viewers) {
    if (frame == null || !active) {
      return;
    }

    MapItemView view = frame.item() == null ? null : host.readMapItem(frame.item());
    if (!isReactMap(view)) {
      // The frame no longer holds a React map; drop stale tracking so the wall re-solves.
      untrackFrameMap(frame.frameId());
      return;
    }

    I repaired = repairItem(frame.item(), view, frame.worldId(), force, true);
    if (repaired != null) {
      host.setFrameItemQuietly(frame, repaired);
      view = host.readMapItem(repaired);
    }

    if (view == null || !view.hasMapId()) {
      return;
    }

    trackFrameMap(frame, view.mapId());
    pushMapToNearbyViewers(frame.frameId(), frame.worldId(), frame.x(), frame.y(), frame.z(), view.mapId(), viewers);
  }

  public void scheduleFrameRefresh(UUID frameId) {
    if (frameId == null || !active) {
      return;
    }

    // Frame change events fire before the item lands, so re-read the frame one tick later.
    host.runOnFrameOwner(frameId, 1L, () -> refreshFrame(host.frame(frameId, true), false, memoizedViewers()), null);
  }

  private void trackFrameMap(FrameSnapshot<I> frame, int mapId) {
    if (frame.worldId() == null || mapId < 0) {
      return;
    }

    long now = System.currentTimeMillis();
    UUID frameId = frame.frameId();
    UUID worldId = frame.worldId();
    frameMapLastSeenByMapId.put(mapId, now);
    AtomicBoolean created = new AtomicBoolean(false);
    ActiveFrameMap tracked = activeFrameMaps.compute(frameId, (id, existing) -> {
      if (existing == null || !existing.worldId.equals(worldId)) {
        created.set(true);
        return new ActiveFrameMap(frameId, worldId, mapId, now);
      }

      existing.mapId = mapId;
      existing.lastSeenMs = now;
      return existing;
    });
    tracked.position(frame);
    if (created.get()) {
      activeFrameMapQueue.offer(tracked);
    }
    tracked.nextPushDispatchMs = now + effectiveFrameMapPushIntervalMs();
    frameIdsByMapId.put(mapId, frameId);
    if (tracked.gridMapId < 0) {
      invalidateMegamapGrid();
    }
    captureFrameGridState(tracked, frame);
  }

  private void captureFrameGridState(ActiveFrameMap tracked, FrameSnapshot<I> frame) {
    Facing facing = frame.facing();
    boolean rotationAligned = frame.rotation() == 0;
    String rendererId = liveRendererId(tracked.mapId);
    if (tracked.gridMapId == tracked.mapId
        && tracked.facing == facing
        && tracked.rotationAligned == rotationAligned
        && tracked.blockX == frame.blockX()
        && tracked.blockY == frame.blockY()
        && tracked.blockZ == frame.blockZ()
        && Objects.equals(tracked.rendererId, rendererId)) {
      return;
    }

    tracked.gridMapId = tracked.mapId;
    tracked.facing = facing;
    tracked.rotationAligned = rotationAligned;
    tracked.blockX = frame.blockX();
    tracked.blockY = frame.blockY();
    tracked.blockZ = frame.blockZ();
    tracked.rendererId = rendererId;
    invalidateMegamapGrid();
  }

  private void invalidateMegamapGrid() {
    megamapStateVersion.incrementAndGet();
  }

  public void untrackFrameMap(UUID frameId) {
    if (frameId == null) {
      return;
    }

    ActiveFrameMap removed = activeFrameMaps.remove(frameId);
    if (removed == null) {
      return;
    }

    frameIdsByMapId.remove(removed.mapId, frameId);
    // A split dispatched to a region that never ran must not pin this frame out of future splits.
    megamapSplitsInFlight.remove(frameId);
    invalidateMegamapGrid();
  }

  public int trackedFrameCount() {
    return activeFrameMaps.size();
  }

  public FrameAnchor frameAnchor(int mapId) {
    if (mapId < 0 || activeFrameMaps.isEmpty()) {
      return null;
    }

    MapComposer composer = composers.get(mapId);
    UUID mapWorld = composer == null ? null : composer.worldId();
    UUID frameId = frameIdsByMapId.get(mapId);
    if (frameId != null) {
      FrameAnchor anchor = anchorOf(activeFrameMaps.get(frameId), mapId, mapWorld);
      if (anchor != null) {
        return anchor;
      }
    }

    for (ActiveFrameMap tracked : activeFrameMaps.values()) {
      FrameAnchor anchor = anchorOf(tracked, mapId, mapWorld);
      if (anchor != null) {
        return anchor;
      }
    }
    return null;
  }

  private FrameAnchor anchorOf(ActiveFrameMap tracked, int mapId, UUID mapWorld) {
    if (tracked == null || tracked.mapId != mapId || !tracked.positioned) {
      return null;
    }
    if (mapWorld != null && !mapWorld.equals(tracked.worldId)) {
      return null;
    }
    return new FrameAnchor(tracked.worldId, tracked.x, tracked.y, tracked.z);
  }

  // ---- megamap -----------------------------------------------------------------------------

  private boolean rebuildMegamapTiles() {
    int version = megamapStateVersion.get();
    boolean enabled = config.isMegamapEnabled();
    if (version == megamapSolvedVersion && enabled == megamapSolvedEnabled) {
      return false;
    }

    megamapSolvedVersion = version;
    megamapSolvedEnabled = enabled;
    if (!enabled || activeFrameMaps.isEmpty()) {
      megamapSolution = MegamapGrid.MegamapSolution.EMPTY;
      return true;
    }

    List<MegamapGrid.FrameCell> cells = new ArrayList<>(activeFrameMaps.size());
    for (ActiveFrameMap tracked : activeFrameMaps.values()) {
      if (tracked == null || tracked.facing == null || tracked.rendererId == null) {
        continue;
      }

      cells.add(new MegamapGrid.FrameCell(
          tracked.mapId,
          tracked.worldId,
          tracked.facing,
          tracked.blockX,
          tracked.blockY,
          tracked.blockZ,
          tracked.rendererId,
          tracked.rotationAligned
      ));
    }

    megamapSolution = MegamapGrid.analyze(cells);
    return true;
  }

  private void reconcileMegamapDefects() {
    MegamapGrid.MegamapSolution solution = megamapSolution;
    if (!config.isMegamapEnabled() || solution == null || solution.defects().isEmpty() || activeFrameMaps.isEmpty()) {
      return;
    }

    splitDuplicateFrameMaps(solution);
    resetRotatedFrameMaps(solution);
  }

  private void splitDuplicateFrameMaps(MegamapGrid.MegamapSolution solution) {
    if (!config.isMegamapSplitDuplicates()) {
      return;
    }

    List<MegamapDuplicateSplitter.DuplicateFrame> duplicates = null;
    for (ActiveFrameMap tracked : activeFrameMaps.values()) {
      if (tracked == null || tracked.rendererId == null) {
        continue;
      }

      MegamapGrid.MegamapDefect defect = solution.defects().get(tracked.mapId);
      if (defect == null || defect.reason() != MegamapGrid.DefectReason.DUPLICATE_MAP_ID) {
        continue;
      }

      if (duplicates == null) {
        duplicates = new ArrayList<>();
      }
      duplicates.add(new MegamapDuplicateSplitter.DuplicateFrame(tracked.frameId, tracked.mapId, tracked.rendererId, tracked.firstSeenMs));
    }

    if (duplicates == null) {
      return;
    }

    int remaining = MEGAMAP_SPLITS_PER_PASS;
    for (UUID frameId : MegamapDuplicateSplitter.plan(duplicates)) {
      if (remaining <= 0) {
        return;
      }
      if (splitDuplicateFrameMap(frameId)) {
        remaining--;
      }
    }
  }

  private boolean splitDuplicateFrameMap(UUID frameId) {
    ActiveFrameMap tracked = activeFrameMaps.get(frameId);
    if (tracked == null || tracked.rendererId == null || !host.frameExists(frameId)) {
      return false;
    }

    ReactRenderer renderer = registry.resolve(tracked.rendererId);
    if (renderer == null || !host.isWorldLoaded(tracked.worldId)) {
      return false;
    }

    // Never mint a fresh id behind an unresolved renderer; that would swap the dashboard out from under the player.
    if (MapRendererRegistry.isSpecific(tracked.rendererId) && MapRendererRegistry.isUnknown(renderer.getId())) {
      return false;
    }

    if (!megamapSplitsInFlight.add(frameId)) {
      return false;
    }

    // Map ids are global, so mint here on the maintenance thread and dispatch only the item write.
    int duplicateMapId = tracked.mapId;
    int freshMapId = allocate(tracked.worldId, renderer);
    if (freshMapId < 0) {
      megamapSplitsInFlight.remove(frameId);
      return false;
    }

    boolean scheduled = host.runOnFrameOwner(frameId, 0L, () -> {
      try {
        applyDuplicateSplit(tracked, duplicateMapId, freshMapId, renderer);
      } finally {
        megamapSplitsInFlight.remove(frameId);
      }
    }, () -> megamapSplitsInFlight.remove(frameId));
    if (!scheduled) {
      megamapSplitsInFlight.remove(frameId);
    }
    return true;
  }

  private void applyDuplicateSplit(ActiveFrameMap tracked, int duplicateMapId, int freshMapId, ReactRenderer renderer) {
    FrameSnapshot<I> frame = host.frame(tracked.frameId, true);
    if (frame == null || frame.item() == null) {
      return;
    }

    MapItemView view = host.readMapItem(frame.item());
    if (!isReactMap(view) || view.mapId() != duplicateMapId) {
      return;
    }

    ItemMetadata metadata = metadataFor(view, renderer);
    I written = host.writeMapItem(frame.item(), freshMapId, metadata.tags(), metadata.lore());
    host.setFrameItemQuietly(frame, written);

    long now = System.currentTimeMillis();
    tracked.mapId = freshMapId;
    tracked.position(frame);
    tracked.validated = true;
    tracked.lastSeenMs = now;
    tracked.lastValidatedMs = now;
    frameIdsByMapId.remove(duplicateMapId, tracked.frameId);
    frameIdsByMapId.put(freshMapId, tracked.frameId);
    captureFrameGridState(tracked, frame);
    invalidateMegamapGrid();
    verbose(() -> "Megamap split duplicate map " + duplicateMapId + " -> " + freshMapId + " frame=" + tracked.frameId);
  }

  private void resetRotatedFrameMaps(MegamapGrid.MegamapSolution solution) {
    int remaining = MEGAMAP_ROTATION_RESETS_PER_PASS;
    for (ActiveFrameMap tracked : activeFrameMaps.values()) {
      if (remaining <= 0) {
        return;
      }
      if (tracked == null || tracked.rotationAligned) {
        continue;
      }

      MegamapGrid.MegamapDefect defect = solution.defects().get(tracked.mapId);
      if (defect == null || defect.reason() != MegamapGrid.DefectReason.ROTATED || !host.frameExists(tracked.frameId)) {
        continue;
      }

      remaining--;
      host.runOnFrameOwner(tracked.frameId, 0L, () -> applyRotationReset(tracked), null);
    }
  }

  private void applyRotationReset(ActiveFrameMap tracked) {
    FrameSnapshot<I> frame = host.frame(tracked.frameId, false);
    if (frame == null) {
      return;
    }

    if (frame.rotation() != 0) {
      host.setFrameRotation(tracked.frameId, 0);
      verbose(() -> "Megamap rotation reset map=" + tracked.mapId + " frame=" + tracked.frameId);
    }

    if (!tracked.rotationAligned) {
      tracked.rotationAligned = true;
      invalidateMegamapGrid();
    }
  }

  public MegamapGrid.MegamapTile megamapTileFor(int mapId) {
    if (!config.isMegamapEnabled()) {
      return null;
    }
    MegamapGrid.MegamapSolution solution = megamapSolution;
    return solution == null || solution.tiles().isEmpty() ? null : solution.tiles().get(mapId);
  }

  public MegamapGrid.MegamapDefect megamapDefectFor(int mapId) {
    if (!config.isMegamapEnabled()) {
      return null;
    }
    MegamapGrid.MegamapSolution solution = megamapSolution;
    return solution == null || solution.defects().isEmpty() ? null : solution.defects().get(mapId);
  }

  public Map<String, MegamapStatus> megamapStatusByRenderer() {
    MegamapGrid.MegamapSolution solution = megamapSolution;
    if (!config.isMegamapEnabled() || solution == null || solution.isEmpty()) {
      return Map.of();
    }

    Map<String, MegamapStatus> status = new LinkedHashMap<>();
    for (ActiveFrameMap tracked : activeFrameMaps.values()) {
      if (tracked == null || tracked.rendererId == null) {
        continue;
      }

      MegamapGrid.MegamapTile tile = solution.tiles().get(tracked.mapId);
      MegamapGrid.MegamapDefect defect = solution.defects().get(tracked.mapId);
      if (tile == null && defect == null) {
        continue;
      }

      status.merge(
          MapRendererRegistry.normalize(tracked.rendererId),
          new MegamapStatus(
              tile == null ? 0 : tile.gridWidth(),
              tile == null ? 0 : tile.gridHeight(),
              1,
              defect == null ? null : defect.reason()
          ),
          MegamapStatus::mergeWith
      );
    }
    return status;
  }

  // ---- maintenance -------------------------------------------------------------------------

  /**
   * One maintenance pass: inventory repair, frame pushes, chunk repair and megamap solving. Main or global thread.
   */
  public void maintenance() {
    if (!active) {
      return;
    }

    lastMaintenanceMs = System.currentTimeMillis();
    Supplier<ViewerIndex> viewers = memoizedViewers();
    repairOneOnlineViewerInventory(viewers);
    pushTrackedFrameMaps(viewers);
    repairOneLoadedChunkItemFrames(viewers);
    if (rebuildMegamapTiles()) {
      reconcileMegamapDefects();
    }
  }

  @Override
  public void onTick(long nowMs) {
    if (!active) {
      return;
    }

    if (nowMs - lastMaintenanceMs >= Math.max(50L, config.getMaintenanceTickIntervalMs())) {
      maintenance();
    }

    if (!host.pullsHeldMaps()) {
      deliverHeldMaps(nowMs);
    }
  }

  private void deliverHeldMaps(long now) {
    for (ViewerSnapshot viewer : host.viewers()) {
      deliverHeld(viewer, viewer.mainHandMapId(), now);
      if (viewer.offHandMapId() != viewer.mainHandMapId()) {
        deliverHeld(viewer, viewer.offHandMapId(), now);
      }
    }
  }

  private void deliverHeld(ViewerSnapshot viewer, int mapId, long now) {
    if (mapId < 0 || !composers.containsKey(mapId) || !shouldRenderForViewer(mapId, viewer)) {
      return;
    }
    deliver(viewer.viewerId(), mapId, now, true);
  }

  public Supplier<ViewerIndex> memoizedViewers() {
    return new Supplier<>() {
      private ViewerIndex index;

      @Override
      public ViewerIndex get() {
        if (index == null) {
          index = ViewerIndex.of(host.viewers());
        }
        return index;
      }
    };
  }

  private void repairOneOnlineViewerInventory(Supplier<ViewerIndex> viewers) {
    long now = System.currentTimeMillis();
    if (now - lastInventoryRepairMs < effectiveInventoryRepairCadenceMs()) {
      return;
    }
    lastInventoryRepairMs = now;

    List<ViewerSnapshot> online = viewers.get().all();
    if (online.isEmpty()) {
      inventoryRepairCursor = 0;
      return;
    }

    if (inventoryRepairCursor >= online.size()) {
      inventoryRepairCursor = 0;
    }

    int passes = Math.min(effectiveInventoryBatchSize(), online.size());
    boolean force = inStartupBoost();
    for (int i = 0; i < passes; i++) {
      if (inventoryRepairCursor >= online.size()) {
        inventoryRepairCursor = 0;
      }

      UUID viewerId = online.get(inventoryRepairCursor++).viewerId();
      host.runOnViewerOwner(viewerId, 0L, () -> repairInventory(viewerId, host.viewerWorld(viewerId), force));
    }
  }

  private void pushTrackedFrameMaps(Supplier<ViewerIndex> viewers) {
    if (activeFrameMaps.isEmpty() || activeFrameMapQueue.isEmpty()) {
      pruneFramePushState();
      return;
    }

    long now = System.currentTimeMillis();
    int remaining = Math.max(1, config.getFrameMapPushBatchSize());
    while (remaining-- > 0) {
      ActiveFrameMap tracked = activeFrameMapQueue.poll();
      if (tracked == null) {
        break;
      }
      if (activeFrameMaps.get(tracked.frameId) != tracked) {
        continue;
      }

      activeFrameMapQueue.offer(tracked);
      scheduleTrackedFrameMaintenance(tracked, viewers, now);
    }

    pruneFramePushState();
  }

  private void scheduleTrackedFrameMaintenance(ActiveFrameMap tracked, Supplier<ViewerIndex> viewers, long now) {
    if (tracked.worldId == null || now < tracked.nextPushDispatchMs) {
      return;
    }

    if (!tracked.pushDispatchInFlight.compareAndSet(false, true)) {
      return;
    }

    tracked.nextPushDispatchMs = now + effectiveFrameMapPushIntervalMs();
    if (!host.isWorldLoaded(tracked.worldId) || !host.frameExists(tracked.frameId)) {
      tracked.pushDispatchInFlight.set(false);
      untrackFrameMap(tracked.frameId);
      return;
    }

    Runnable completion = () -> tracked.pushDispatchInFlight.set(false);
    Runnable retirement = () -> {
      tracked.nextPushDispatchMs = now;
      completion.run();
    };
    Runnable work = () -> {
      try {
        if (activeFrameMaps.get(tracked.frameId) == tracked && active) {
          validateAndPushTrackedFrame(tracked, viewers);
        }
      } finally {
        completion.run();
      }
    };

    if (!host.runOnFrameOwner(tracked.frameId, 0L, work, retirement)) {
      tracked.nextPushDispatchMs = now;
      completion.run();
    }
  }

  private void validateAndPushTrackedFrame(ActiveFrameMap tracked, Supplier<ViewerIndex> viewers) {
    long now = System.currentTimeMillis();
    // Reading the frame item copies the stack; only revalidate on a cadence and push the cached id between.
    boolean cached = tracked.validated && now - tracked.lastValidatedMs < Math.max(0L, config.getFrameMapValidateIntervalMs());
    FrameSnapshot<I> frame = host.frame(tracked.frameId, !cached);
    if (frame == null || !tracked.worldId.equals(frame.worldId())) {
      untrackFrameMap(tracked.frameId);
      return;
    }

    if (cached) {
      tracked.lastSeenMs = now;
      tracked.position(frame);
      frameMapLastSeenByMapId.put(tracked.mapId, now);
      pushMapToNearbyViewers(tracked.frameId, frame.worldId(), frame.x(), frame.y(), frame.z(), tracked.mapId, viewers);
      return;
    }

    MapItemView view = frame.item() == null ? null : host.readMapItem(frame.item());
    if (!isReactMap(view) || !view.hasMapId()) {
      untrackFrameMap(tracked.frameId);
      return;
    }

    int mapId = view.mapId();
    int previousMapId = tracked.mapId;
    tracked.mapId = mapId;
    tracked.position(frame);
    tracked.lastSeenMs = now;
    tracked.lastValidatedMs = now;
    tracked.validated = true;
    if (previousMapId != mapId) {
      frameIdsByMapId.remove(previousMapId, tracked.frameId);
    }
    frameIdsByMapId.put(mapId, tracked.frameId);
    captureFrameGridState(tracked, frame);
    frameMapLastSeenByMapId.put(mapId, now);
    pushMapToNearbyViewers(tracked.frameId, frame.worldId(), frame.x(), frame.y(), frame.z(), mapId, viewers);
  }

  private void pruneFramePushState() {
    long now = System.currentTimeMillis();
    long retentionMs = effectiveFrameMapPushStateRetentionMs();
    long pruneIntervalMs = Math.max(5_000L, Math.min(60_000L, retentionMs / 4L));
    if (now - lastFramePushStatePruneMs < pruneIntervalMs) {
      return;
    }
    lastFramePushStatePruneMs = now;
    pushMsByViewerKey.entrySet().removeIf(entry -> now - entry.getValue() > retentionMs);
    frameMapLastSeenByMapId.entrySet().removeIf(entry -> now - entry.getValue() > retentionMs);
    delivery.prune(now, retentionMs);
  }

  private void repairOneLoadedChunkItemFrames(Supplier<ViewerIndex> viewers) {
    long now = System.currentTimeMillis();
    if (now - lastItemFrameRepairMs < effectiveItemFrameRepairCadenceMs()) {
      return;
    }
    lastItemFrameRepairMs = now;

    int pollBudget = effectiveItemFrameChunkBatchSize();
    seedFrameRepairQueue(pollBudget);
    if (frameChunkRepairQueue.isEmpty()) {
      return;
    }

    boolean force = inStartupBoost();
    List<FrameChunkRepair> requeue = new ArrayList<>(pollBudget);
    for (int polled = 0; polled < pollBudget; polled++) {
      FrameChunkRepair repair = frameChunkRepairQueue.poll();
      if (repair == null) {
        break;
      }
      if (!isFrameChunkRepairActive(repair)) {
        continue;
      }

      requeue.add(repair);
      scheduleFrameChunkRepair(repair, force, viewers);
    }

    for (FrameChunkRepair repair : requeue) {
      if (isFrameChunkRepairActive(repair)) {
        frameChunkRepairQueue.offer(repair);
      }
    }
  }

  private void seedFrameRepairQueue(int seedBudget) {
    if (frameChunkSeedQueue.isEmpty() || seedBudget <= 0) {
      return;
    }

    int remaining = seedBudget;
    List<FrameChunkSeed> requeue = new ArrayList<>();
    while (remaining > 0) {
      FrameChunkSeed seed = frameChunkSeedQueue.poll();
      if (seed == null) {
        break;
      }
      if (!host.isWorldLoaded(seed.worldId)) {
        continue;
      }

      int start = seed.nextIndex;
      int end = start + Math.min(remaining, seed.chunks.size() - start);
      for (int index = start; index < end; index++) {
        ChunkRef chunk = seed.chunks.get(index);
        trackFrameRepairChunk(seed.worldId, chunk.chunkX(), chunk.chunkZ());
      }
      seed.nextIndex = end;
      remaining -= end - start;
      if (seed.nextIndex < seed.chunks.size()) {
        requeue.add(seed);
      }
    }

    frameChunkSeedQueue.addAll(requeue);
  }

  private void trackFrameRepairChunk(UUID worldId, int chunkX, int chunkZ) {
    if (!active || worldId == null) {
      return;
    }

    Map<Long, FrameChunkRepair> worldRepairs = frameChunkRepairsByWorld.computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>());
    long key = chunkKey(chunkX, chunkZ);
    FrameChunkRepair repair = new FrameChunkRepair(worldId, key, chunkX, chunkZ);
    if (worldRepairs.putIfAbsent(key, repair) == null) {
      frameChunkRepairQueue.offer(repair);
    }
  }

  private void removeFrameRepairChunk(FrameChunkRepair repair) {
    Map<Long, FrameChunkRepair> worldRepairs = frameChunkRepairsByWorld.get(repair.worldId);
    if (worldRepairs != null) {
      worldRepairs.remove(repair.chunkKey, repair);
    }
  }

  private boolean isFrameChunkRepairActive(FrameChunkRepair repair) {
    if (repair == null) {
      return false;
    }
    Map<Long, FrameChunkRepair> worldRepairs = frameChunkRepairsByWorld.get(repair.worldId);
    return worldRepairs != null && worldRepairs.get(repair.chunkKey) == repair;
  }

  public int pendingChunkRepairs() {
    int total = 0;
    for (Map<Long, FrameChunkRepair> repairs : frameChunkRepairsByWorld.values()) {
      total += repairs.size();
    }
    return total;
  }

  private void scheduleFrameChunkRepair(FrameChunkRepair repair, boolean force, Supplier<ViewerIndex> viewers) {
    if (!isFrameChunkRepairActive(repair) || !repair.inFlight.compareAndSet(false, true)) {
      return;
    }

    if (!host.isWorldLoaded(repair.worldId)) {
      repair.inFlight.set(false);
      removeFrameRepairChunk(repair);
      return;
    }

    Runnable work = () -> {
      try {
        if (!isFrameChunkRepairActive(repair)) {
          return;
        }
        List<FrameSnapshot<I>> frames = new ArrayList<>();
        if (!host.framesInChunk(repair.worldId, repair.chunkX, repair.chunkZ, frames::add)) {
          removeFrameRepairChunk(repair);
          return;
        }
        for (FrameSnapshot<I> frame : frames) {
          refreshFrame(frame, force, viewers);
        }
      } finally {
        repair.inFlight.set(false);
      }
    };

    if (!host.runOnChunkOwner(repair.worldId, repair.chunkX, repair.chunkZ, work)) {
      repair.inFlight.set(false);
    }
  }

  // ---- frame push ----------------------------------------------------------------------------

  private void pushMapToNearbyViewers(
      UUID frameId,
      UUID worldId,
      double sourceX,
      double sourceY,
      double sourceZ,
      int mapId,
      Supplier<ViewerIndex> viewers
  ) {
    if (worldId == null || mapId < 0 || !active) {
      return;
    }

    ViewerIndex index = viewers.get();
    if (index == null || index.isEmpty()) {
      return;
    }

    boolean outsideRangeWhenHolding = config.isFrameMapPushOutsideRangeWhenHolding();
    double radius = Math.max(1D, config.getFrameMapPushRadiusBlocks());
    List<ViewerSnapshot> candidates = index.candidates(worldId, sourceX, sourceZ, radius, mapId, outsideRangeWhenHolding);
    if (candidates.isEmpty()) {
      return;
    }

    long now = System.currentTimeMillis();
    double radiusSq = radius * radius;
    long activeInterval = effectiveFrameMapPushIntervalMs();
    long idleInterval = effectiveFrameMapIdlePushIntervalMs();
    boolean requireLineOfSight = config.isFrameMapRequireLineOfSight();
    double lookDotThreshold = effectiveFrameMapLookDotThreshold();
    for (ViewerSnapshot viewer : candidates) {
      UUID viewerId = viewer.viewerId();
      if (!host.isOnline(viewerId) || !Objects.equals(host.viewerWorld(viewerId), viewer.worldId())) {
        continue;
      }

      boolean holding = viewer.isHolding(mapId);
      boolean holdingBypassesRange = holding && outsideRangeWhenHolding;
      boolean sameWorld = worldId.equals(viewer.worldId());
      if (!holdingBypassesRange && !sameWorld) {
        continue;
      }

      boolean withinRadius = false;
      if (sameWorld) {
        double dx = viewer.x() - sourceX;
        double dy = viewer.y() - sourceY;
        double dz = viewer.z() - sourceZ;
        withinRadius = ((dx * dx) + (dy * dy) + (dz * dz)) <= radiusSq;
      }
      if (!holdingBypassesRange && !withinRadius) {
        continue;
      }

      boolean watching = holding || isLikelyLookingAt(viewer, sourceX, sourceY, sourceZ, lookDotThreshold);
      long requiredInterval = watching ? activeInterval : idleInterval;
      FramePushKey pushKey = new FramePushKey(mapId, viewerId);
      long seededLastPush = now - requiredInterval + initialPushOffsetMs(pushKey, requiredInterval);
      // The jitter seed is stored on first sight; recomputing it from now every pass would never come due.
      Long seen = pushMsByViewerKey.putIfAbsent(pushKey, seededLastPush);
      long lastPush = seen == null ? seededLastPush : seen;
      if (now - lastPush < requiredInterval) {
        continue;
      }

      boolean checkSight = !holdingBypassesRange && requireLineOfSight;
      host.runOnViewerOwner(viewerId, 0L, () -> {
        if (!active || (checkSight && !host.hasLineOfSight(viewerId, frameId, worldId, sourceX, sourceY, sourceZ))) {
          return;
        }

        try {
          deliver(viewerId, mapId, now, false);
          pushMsByViewerKey.put(pushKey, now);
        } catch (Throwable ex) {
          verbose(() -> "Failed to push map " + mapId + " to viewer " + viewerId + ": " + ex.getClass().getSimpleName()
              + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
        }
      });
    }
  }

  public static boolean isLikelyLookingAt(ViewerSnapshot viewer, double sourceX, double sourceY, double sourceZ, double lookDotThreshold) {
    if (viewer == null) {
      return false;
    }

    double toFrameX = sourceX - viewer.eyeX();
    double toFrameY = sourceY - viewer.eyeY();
    double toFrameZ = sourceZ - viewer.eyeZ();
    double toFrameLengthSquared = (toFrameX * toFrameX) + (toFrameY * toFrameY) + (toFrameZ * toFrameZ);
    if (toFrameLengthSquared <= 1.0E-6D) {
      return true;
    }
    if (!viewer.hasDirection()) {
      return false;
    }

    double inverseToFrameLength = 1.0D / Math.sqrt(toFrameLengthSquared);
    double dot = (viewer.dirX() * toFrameX * inverseToFrameLength)
        + (viewer.dirY() * toFrameY * inverseToFrameLength)
        + (viewer.dirZ() * toFrameZ * inverseToFrameLength);
    return dot >= lookDotThreshold;
  }

  static int chunkCoordinate(double blockCoordinate) {
    return (int) Math.floor(blockCoordinate / 16.0D);
  }

  static long chunkKey(int chunkX, int chunkZ) {
    return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
  }

  private static long initialPushOffsetMs(FramePushKey pushKey, long intervalMs) {
    if (intervalMs <= 1L) {
      return 0L;
    }
    return Integer.toUnsignedLong(pushKey.hashCode()) % intervalMs;
  }

  private boolean isKnownFrameMap(int mapId, long now) {
    Long seenAt = frameMapLastSeenByMapId.get(mapId);
    return seenAt != null && now - seenAt <= effectiveFrameMapPushStateRetentionMs();
  }

  // ---- listener ------------------------------------------------------------------------------

  @Override
  public void onViewerJoin(UUID viewerId) {
    repairInventory(viewerId, host.viewerWorld(viewerId), true);
  }

  @Override
  public void onViewerChangedWorld(UUID viewerId, UUID toWorld) {
    repairInventory(viewerId, toWorld, false);
  }

  @Override
  public void onFramesLoaded(UUID worldId, int chunkX, int chunkZ, List<FrameSnapshot<I>> frames) {
    trackFrameRepairChunk(worldId, chunkX, chunkZ);
    Supplier<ViewerIndex> viewers = memoizedViewers();
    for (FrameSnapshot<I> frame : frames) {
      refreshFrame(frame, true, viewers);
    }
  }

  @Override
  public void onFrameUnloaded(UUID frameId) {
    untrackFrameMap(frameId);
  }

  @Override
  public void onFrameChanged(UUID frameId) {
    scheduleFrameRefresh(frameId);
  }

  @Override
  public void onFrameBroken(UUID frameId) {
    untrackFrameMap(frameId);
  }

  @Override
  public void onChunkLoaded(UUID worldId, int chunkX, int chunkZ) {
    trackFrameRepairChunk(worldId, chunkX, chunkZ);
  }

  @Override
  public void onChunkUnloaded(UUID worldId, int chunkX, int chunkZ) {
    Map<Long, FrameChunkRepair> worldRepairs = worldId == null ? null : frameChunkRepairsByWorld.get(worldId);
    if (worldRepairs != null) {
      worldRepairs.remove(chunkKey(chunkX, chunkZ));
    }
  }

  @Override
  public void onWorldUnloaded(UUID worldId) {
    if (worldId != null) {
      frameChunkRepairsByWorld.remove(worldId);
    }
  }

  // ---- cadence -------------------------------------------------------------------------------

  private long effectiveFrameMapPushIntervalMs() {
    long baseInterval = Math.max(50L, config.getFrameMapPushIntervalMs());
    if (!inStartupBoost()) {
      return baseInterval;
    }
    return Math.min(baseInterval, Math.max(25L, config.getStartupFrameMapPushIntervalMs()));
  }

  private long effectiveFrameMapPushStateRetentionMs() {
    return Math.max(effectiveFrameMapIdlePushIntervalMs() * 2L, config.getFrameMapPushStateRetentionMs());
  }

  private long effectiveFrameMapIdlePushIntervalMs() {
    return Math.max(Math.max(1_000L, config.getFrameMapIdlePushIntervalMs()), effectiveFrameMapPushIntervalMs());
  }

  private long effectiveInventoryRepairCadenceMs() {
    long baseCadence = Math.max(50L, config.getInventoryRepairCadenceMs());
    return inStartupBoost() ? Math.min(baseCadence, 100L) : baseCadence;
  }

  private long effectiveItemFrameRepairCadenceMs() {
    long baseCadence = Math.max(50L, config.getItemFrameRepairCadenceMs());
    return inStartupBoost() ? Math.min(baseCadence, 100L) : baseCadence;
  }

  private int effectiveInventoryBatchSize() {
    int configured = Math.max(1, config.getInventoryRepairBatchSize());
    return inStartupBoost() ? Math.max(configured, Math.max(1, config.getStartupBoostInventoryBatchSize())) : configured;
  }

  private int effectiveItemFrameChunkBatchSize() {
    int configured = Math.max(1, config.getItemFrameChunkBatchSize());
    return inStartupBoost() ? Math.max(configured, Math.max(1, config.getStartupBoostItemFrameChunkBatchSize())) : configured;
  }

  private boolean inStartupBoost() {
    return System.currentTimeMillis() < startupBoostUntilMs;
  }

  private double effectiveFrameMapPushRadiusSq() {
    double radius = Math.max(1D, config.getFrameMapPushRadiusBlocks());
    return radius * radius;
  }

  private double effectiveFrameMapLookDotThreshold() {
    return Math.max(0D, Math.min(0.999D, config.getFrameMapLookDotThreshold()));
  }

  private static void verbose(Supplier<String> message) {
    if (ReactPlatforms.isBound()) {
      ReactPlatforms.get().verbose(message);
    }
  }

  // ---- state types ---------------------------------------------------------------------------

  public record ItemMetadata(MapItemTags tags, List<String> lore, boolean changed) {
  }

  public record FrameAnchor(UUID worldId, double x, double y, double z) {
  }

  private record FramePushKey(int mapId, UUID playerId) {
  }

  /**
   * Online viewers bucketed by world chunk and by held map id, built once per maintenance pass.
   */
  public static final class ViewerIndex {
    private static final ViewerIndex EMPTY = new ViewerIndex(List.of(), Map.of(), Map.of());

    private final List<ViewerSnapshot> all;
    private final Map<UUID, Map<Long, List<ViewerSnapshot>>> byWorldChunk;
    private final Map<Integer, List<ViewerSnapshot>> holding;

    private ViewerIndex(
        List<ViewerSnapshot> all,
        Map<UUID, Map<Long, List<ViewerSnapshot>>> byWorldChunk,
        Map<Integer, List<ViewerSnapshot>> holding
    ) {
      this.all = all;
      this.byWorldChunk = byWorldChunk;
      this.holding = holding;
    }

    public static ViewerIndex of(List<ViewerSnapshot> viewers) {
      if (viewers == null || viewers.isEmpty()) {
        return EMPTY;
      }

      Map<UUID, Map<Long, List<ViewerSnapshot>>> byWorldChunk = new HashMap<>();
      Map<Integer, List<ViewerSnapshot>> holding = new HashMap<>();
      List<ViewerSnapshot> all = new ArrayList<>(viewers.size());
      for (ViewerSnapshot viewer : viewers) {
        if (viewer == null || viewer.worldId() == null) {
          continue;
        }

        all.add(viewer);
        byWorldChunk.computeIfAbsent(viewer.worldId(), ignored -> new HashMap<>())
            .computeIfAbsent(chunkKey(chunkCoordinate(viewer.x()), chunkCoordinate(viewer.z())), ignored -> new ArrayList<>())
            .add(viewer);
        if (viewer.mainHandMapId() >= 0) {
          holding.computeIfAbsent(viewer.mainHandMapId(), ignored -> new ArrayList<>()).add(viewer);
        }
        if (viewer.offHandMapId() >= 0 && viewer.offHandMapId() != viewer.mainHandMapId()) {
          holding.computeIfAbsent(viewer.offHandMapId(), ignored -> new ArrayList<>()).add(viewer);
        }
      }
      return new ViewerIndex(all, byWorldChunk, holding);
    }

    public List<ViewerSnapshot> all() {
      return all;
    }

    public boolean isEmpty() {
      return all.isEmpty();
    }

    public List<ViewerSnapshot> candidates(
        UUID worldId,
        double sourceX,
        double sourceZ,
        double radius,
        int mapId,
        boolean includeHolders
    ) {
      Map<UUID, ViewerSnapshot> candidates = new LinkedHashMap<>();
      Map<Long, List<ViewerSnapshot>> buckets = byWorldChunk.get(worldId);
      if (buckets != null && !buckets.isEmpty()) {
        int minChunkX = chunkCoordinate(sourceX - radius);
        int maxChunkX = chunkCoordinate(sourceX + radius);
        int minChunkZ = chunkCoordinate(sourceZ - radius);
        int maxChunkZ = chunkCoordinate(sourceZ + radius);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
          for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            List<ViewerSnapshot> bucket = buckets.get(chunkKey(chunkX, chunkZ));
            if (bucket != null) {
              for (ViewerSnapshot viewer : bucket) {
                candidates.put(viewer.viewerId(), viewer);
              }
            }
          }
        }
      }

      if (includeHolders) {
        List<ViewerSnapshot> holders = holding.get(mapId);
        if (holders != null) {
          for (ViewerSnapshot viewer : holders) {
            candidates.put(viewer.viewerId(), viewer);
          }
        }
      }
      return new ArrayList<>(candidates.values());
    }
  }

  // Mutated from region threads and read from the maintenance thread, so every mutable field is volatile.
  private static final class ActiveFrameMap {
    private final UUID frameId;
    private final UUID worldId;
    private final long firstSeenMs;
    private final AtomicBoolean pushDispatchInFlight = new AtomicBoolean(false);
    private volatile int mapId;
    private volatile int gridMapId = -1;
    private volatile boolean positioned;
    private volatile double x;
    private volatile double y;
    private volatile double z;
    private volatile long lastSeenMs;
    private volatile long lastValidatedMs;
    private volatile long nextPushDispatchMs;
    private volatile boolean validated;
    private volatile Facing facing;
    private volatile boolean rotationAligned;
    private volatile int blockX;
    private volatile int blockY;
    private volatile int blockZ;
    private volatile String rendererId;

    private ActiveFrameMap(UUID frameId, UUID worldId, int mapId, long now) {
      this.frameId = frameId;
      this.worldId = worldId;
      this.mapId = mapId;
      this.lastSeenMs = now;
      this.firstSeenMs = now;
    }

    private void position(FrameSnapshot<?> frame) {
      x = frame.x();
      y = frame.y();
      z = frame.z();
      positioned = true;
    }
  }

  private static final class FrameChunkRepair {
    private final UUID worldId;
    private final long chunkKey;
    private final int chunkX;
    private final int chunkZ;
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    private FrameChunkRepair(UUID worldId, long chunkKey, int chunkX, int chunkZ) {
      this.worldId = worldId;
      this.chunkKey = chunkKey;
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
    }
  }

  private static final class FrameChunkSeed {
    private final UUID worldId;
    private final List<ChunkRef> chunks;
    private int nextIndex;

    private FrameChunkSeed(UUID worldId, List<ChunkRef> chunks) {
      this.worldId = worldId;
      this.chunks = chunks;
    }
  }
}
