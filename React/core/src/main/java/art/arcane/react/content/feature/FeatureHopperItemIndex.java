package art.arcane.react.content.feature;

import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.core.controller.HopperItemIndex;
import art.arcane.react.core.controller.HopperPositionIndex;
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.HopperHost;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@ConfigDescription("Maintains spatial indices of dropped items and hopper positions. Used by TweakHopperIndex to short-circuit vanilla hopper AABB scans.")
public class FeatureHopperItemIndex extends ReactFeature implements HopperHost.IndexListener {
    public static final String ID = "hopper-item-index";
    private static final int MAX_FOLIA_PLAYER_ANCHORS = 16;
    private static final int MAX_FOLIA_CHUNKS_PER_SWEEP = 64;
    private static final int MAX_PAPER_CHUNKS_PER_SWEEP = 64;
    private static final int MAX_PAPER_ENTITY_SEED_INSPECTIONS = 256;
    private static final int MAX_PAPER_HOPPER_SEEDS = 256;
    private static final int MAX_ITEMS_PER_RECONCILE = 256;
    private static final int MAX_INDEXED_HOPPERS_PER_CHUNK = 256;
    private static final int MAX_PENDING_FOLIA_CHUNKS = 8192;
    private static final int FOLIA_CHUNK_RADIUS = 4;
    private static final long RECONCILE_FAILURE_REPORT_INTERVAL_MS = 60_000L;

    private transient final AtomicBoolean itemReconcileQueued = new AtomicBoolean(false);
    private transient final AtomicBoolean hopperReconcileQueued = new AtomicBoolean(false);
    private transient final AtomicInteger nextFoliaPlayer = new AtomicInteger(0);
    private transient final AtomicInteger nextFoliaChunkOffset = new AtomicInteger(0);
    private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
    private transient final Map<ReconcileFailureKey, ReconcileFailureWindow> reconcileFailureWindows =
        new ConcurrentHashMap<>();
    private transient final Map<UUID, WeakReference<Object>> trackedItems = new ConcurrentHashMap<>();
    private transient final Queue<UUID> pendingItems = new ConcurrentLinkedQueue<>();
    private transient final Set<UUID> pendingItemIds = ConcurrentHashMap.newKeySet();
    private transient volatile FoliaChunkWorkState foliaChunkWorkState = new FoliaChunkWorkState(null, null);
    private transient volatile boolean active;

    @ConfigDoc(value = "Interval in milliseconds between reconciliation sweeps that correct index drift.", impact = "Lower values catch drift faster at slightly higher overhead. Default matches 40 server ticks at 20 TPS.")
    private int reconcileIntervalMs = 2000;

    private transient HopperItemIndex itemIndex;
    private transient HopperPositionIndex positionIndex;
    private transient volatile HopperHost hoppers;
    private transient volatile BlockHost blocks;
    private transient volatile ChunkHost chunkHost;
    private transient volatile WorldHost worlds;
    private transient Subscription subscriptions = Subscription.NONE;

    public FeatureHopperItemIndex() {
        super(ID);
    }

    @Override
    public void onActivate() {
        active = false;
        lifecycleGeneration.incrementAndGet();
        itemReconcileQueued.set(false);
        hopperReconcileQueued.set(false);
        nextFoliaPlayer.set(0);
        nextFoliaChunkOffset.set(0);
        trackedItems.clear();
        pendingItems.clear();
        pendingItemIds.clear();
        itemIndex = new HopperItemIndex();
        positionIndex = new HopperPositionIndex();
        foliaChunkWorkState = new FoliaChunkWorkState(itemIndex, positionIndex);
        ReactPlatform platform = ReactPlatforms.get();
        hoppers = platform.hopperHost().orElse(null);
        blocks = platform.blockHost().orElse(null);
        chunkHost = platform.chunkHost().orElse(null);
        worlds = platform.worldHost().orElse(null);
        active = true;
        subscribe();
    }

    private void subscribe() {
        HopperHost hopperHost = hoppers;
        BlockHost blockHost = blocks;
        List<Subscription> opened = new ArrayList<>();
        if (hopperHost != null) {
            opened.add(hopperHost.onIndexChange(this));
        }
        if (blockHost != null) {
            ChunkHost chunks = chunkHost;
            if (chunks != null) {
                opened.add(chunks.onChunkLoad(ListenerPriority.MONITOR, this::onChunkLoad));
                opened.add(chunks.onChunkUnload(ListenerPriority.MONITOR, this::onChunkUnload));
                opened.add(chunks.onWorldUnload(ListenerPriority.MONITOR, true, event -> onWorldUnload(event.worldId())));
            }
        }
        subscriptions = () -> opened.forEach(Subscription::cancel);
    }

    @Override
    public void onDeactivate() {
        active = false;
        subscriptions.cancel();
        subscriptions = Subscription.NONE;
        lifecycleGeneration.incrementAndGet();
        itemReconcileQueued.set(false);
        hopperReconcileQueued.set(false);
        trackedItems.clear();
        pendingItems.clear();
        pendingItemIds.clear();
        foliaChunkWorkState = new FoliaChunkWorkState(null, null);
        if (itemIndex != null) {
            itemIndex.clear();
            itemIndex = null;
        }
        if (positionIndex != null) {
            positionIndex.clear();
            positionIndex = null;
        }
    }

    @Override
    public int getTickInterval() {
        return reconcileIntervalMs;
    }

    @Override
    public void onTick() {
        if (J.isFoliaThreading()) {
            reconcileFoliaItems();
            reconcileFoliaRegions();
            return;
        }

        if (itemReconcileQueued.compareAndSet(false, true)) {
            J.s(() -> {
                try {
                    reconcileItems();
                } finally {
                    itemReconcileQueued.set(false);
                }
            });
        }

        if (hopperReconcileQueued.compareAndSet(false, true)) {
            J.s(() -> {
                try {
                    reconcilePaperRegions();
                } finally {
                    hopperReconcileQueued.set(false);
                }
            });
        }
    }

    public HopperItemIndex getItemIndex() {
        return itemIndex;
    }

    public HopperPositionIndex getPositionIndex() {
        return positionIndex;
    }

    @Override
    public void itemSeen(Object item) {
        trackItem(item);
    }

    @Override
    public void itemGone(UUID itemId) {
        removeItem(itemId);
    }

    @Override
    public void hopperPlaced(UUID worldId, int x, int y, int z) {
        HopperPositionIndex currentPositionIndex = positionIndex;
        if (currentPositionIndex != null) {
            currentPositionIndex.addHopper(worldId, x, y, z);
        }
    }

    @Override
    public void hopperGone(UUID worldId, int x, int y, int z) {
        HopperPositionIndex currentPositionIndex = positionIndex;
        if (currentPositionIndex != null) {
            currentPositionIndex.removeHopper(worldId, x, y, z);
        }
    }

    void onChunkLoad(ChunkRef chunk) {
        if (itemIndex == null || positionIndex == null) {
            return;
        }
        enqueueFoliaChunk(worldHandle(chunk.worldId()), chunk.worldId(), chunk.chunkX(), chunk.chunkZ());
    }

    void onChunkUnload(ChunkRef chunk) {
        UUID worldId = chunk.worldId();
        int cx = chunk.chunkX();
        int cz = chunk.chunkZ();
        ChunkKey key = new ChunkKey(worldId, cx, cz);
        FoliaChunkWorkState workState = foliaChunkWorkState;
        workState.retireChunk(key);
        if (itemIndex != null) {
            UUID[] itemIds = itemIndex.itemIdsInChunk(worldId, cx, cz);
            for (UUID itemId : itemIds) {
                trackedItems.remove(itemId);
                pendingItemIds.remove(itemId);
            }
            itemIndex.removeChunk(worldId, cx, cz);
        }
        if (positionIndex != null) {
            positionIndex.removeChunk(worldId, cx, cz);
        }
    }

    void onWorldUnload(UUID worldId) {
        FoliaChunkWorkState workState = foliaChunkWorkState;
        workState.removeWorld(worldId);
        HopperItemIndex currentItemIndex = itemIndex;
        if (currentItemIndex != null) {
            currentItemIndex.removeWorld(worldId);
        }
        HopperPositionIndex currentPositionIndex = positionIndex;
        if (currentPositionIndex != null) {
            currentPositionIndex.removeWorld(worldId);
        }
    }

    private Object worldHandle(UUID worldId) {
        WorldHost worldHost = worlds;
        return worldHost == null ? null : worldHost.world(worldId).map(ReactWorld::handle).orElse(null);
    }

    private void reconcilePaperRegions() {
        if (!active || itemIndex == null || positionIndex == null) {
            return;
        }

        long generation = lifecycleGeneration.get();
        FoliaChunkWorkState workState = foliaChunkWorkState;
        importPaperCoordinateWindow(workState);
        PaperReconcileBudget budget = new PaperReconcileBudget();
        int attempts = Math.min(MAX_PAPER_CHUNKS_PER_SWEEP, workState.pendingSize());
        for (int attempt = 0; attempt < attempts && isCurrent(generation); attempt++) {
            FoliaChunkTarget target = pollPendingFoliaChunk(workState);
            if (target == null) {
                break;
            }

            boolean needsMore = false;
            try {
                needsMore = reconcilePaperChunk(target, workState, generation, budget);
            } catch (Throwable throwable) {
                reportReconcileFailure("paper chunk reconcile", throwable);
                needsMore = true;
            }
            if (needsMore && isCurrent(generation) && workState == foliaChunkWorkState) {
                enqueueFoliaChunk(workState, target);
            }
        }
    }

    private void importPaperCoordinateWindow(FoliaChunkWorkState workState) {
        BlockHost blockHost = blocks;
        if (blockHost == null || workState != foliaChunkWorkState) {
            return;
        }

        List<ChunkRef> coordinates = nextLoadedChunks(MAX_PAPER_CHUNKS_PER_SWEEP);
        for (ChunkRef coordinate : coordinates) {
            Object world = worldHandle(coordinate.worldId());
            if (world != null) {
                enqueueFoliaChunk(workState, new FoliaChunkTarget(
                    world,
                    new ChunkKey(coordinate.worldId(), coordinate.chunkX(), coordinate.chunkZ())));
            }
        }
    }

    private boolean reconcilePaperChunk(
            FoliaChunkTarget target,
            FoliaChunkWorkState workState,
            long generation,
            PaperReconcileBudget budget) {
        if (!isCurrent(generation) || workState != foliaChunkWorkState) {
            return false;
        }

        ChunkKey key = target.key();
        if (worldHandle(key.worldId()) == null || !isChunkLoaded(key)) {
            workState.retireChunk(key);
            return false;
        }

        Object chunk = hoppers.chunk(key.worldId(), key.chunkX(), key.chunkZ());
        HopperPositionIndex currentPositionIndex = workState.positionIndex;
        if (currentPositionIndex == null || !isCurrent(generation)) {
            return false;
        }

        ChunkReconcileCursor cursor = workState.cursors.computeIfAbsent(
            key,
            ignored -> new ChunkReconcileCursor());
        if (!workState.seededChunks.contains(key)) {
            boolean needsMore = seedPaperChunkWindow(
                chunk,
                key,
                cursor,
                currentPositionIndex,
                generation,
                budget);
            if (needsMore) {
                return true;
            }
            workState.seededChunks.add(key);
            workState.cursors.remove(key, cursor);
            return false;
        }

        boolean needsMore = reconcilePaperIndexedHoppers(
            key,
            cursor,
            currentPositionIndex,
            generation,
            budget);
        if (!needsMore) {
            workState.cursors.remove(key, cursor);
        }
        return needsMore;
    }

    private boolean seedPaperChunkWindow(
            Object chunk,
            ChunkKey key,
            ChunkReconcileCursor cursor,
            HopperPositionIndex currentPositionIndex,
            long generation,
            PaperReconcileBudget budget) {
        try {
            if (!cursor.paperEntitySeedComplete()) {
                int maximum = budget.remainingEntityInspections();
                if (maximum <= 0) {
                    return true;
                }
                EntitySeedWindow window = cursor.nextPaperEntitySeedWindow(hoppers, chunk, maximum);
                int end = window.start() + window.count();
                for (int index = window.start(); index < end; index++) {
                    Object entity = window.entities()[index];
                    if (hoppers.isItem(entity) && isCurrent(generation)) {
                        trackItem(entity);
                    }
                }
                budget.consumeEntityInspections(window.count());
                if (!window.completed()) {
                    return true;
                }
            }

            int maximum = budget.remainingHopperSeeds();
            if (maximum <= 0) {
                return true;
            }
            int processed = 0;
            Iterator<HopperHost.HopperBlock> hopperStates = cursor.paperHopperSeedIterator(hoppers, chunk);
            while (processed < maximum && hopperStates.hasNext()) {
                HopperHost.HopperBlock state = hopperStates.next();
                if (isCurrent(generation)) {
                    currentPositionIndex.addHopper(
                        key.worldId(),
                        state.x(),
                        state.y(),
                        state.z());
                }
                processed++;
            }
            budget.consumeHopperSeeds(processed);
            if (hopperStates.hasNext()) {
                return true;
            }
            cursor.finishPaperSeed();
            return false;
        } catch (Throwable throwable) {
            cursor.abortPaperSeed();
            throw throwable;
        }
    }

    private boolean reconcilePaperIndexedHoppers(
            ChunkKey key,
            ChunkReconcileCursor cursor,
            HopperPositionIndex currentPositionIndex,
            long generation,
            PaperReconcileBudget budget) {
        int maximum = budget.remainingHopperChecks();
        if (maximum <= 0) {
            return true;
        }
        ScanWindow window = cursor.nextHopperWindow(
            currentPositionIndex.hoppersInChunk(key.worldId(), key.chunkX(), key.chunkZ()),
            maximum);
        budget.consumeHopperChecks(window.count());

        try {
            int end = window.start() + window.count();
            for (int index = window.start(); index < end; index++) {
                long packed = cursor.hopperAt(index);
                int x = HopperPositionIndex.unpackX(packed);
                int y = HopperPositionIndex.unpackY(packed);
                int z = HopperPositionIndex.unpackZ(packed);
                if (isCurrent(generation) && !"HOPPER".equals(hoppers.blockType(key.worldId(), x, y, z))) {
                    currentPositionIndex.removeHopper(key.worldId(), x, y, z);
                }
            }
        } catch (Throwable throwable) {
            cursor.abortHopperCycle();
            reportReconcileFailure("paper hopper check", throwable);
            return true;
        }
        return !window.completedCycle();
    }

    private void reconcileFoliaRegions() {
        if (!active || itemIndex == null || positionIndex == null
                || !itemReconcileQueued.compareAndSet(false, true)) {
            return;
        }

        long generation = lifecycleGeneration.get();
        FoliaPlanFlight flight = new FoliaPlanFlight(generation, foliaChunkWorkState);
        try {
            drainPendingFoliaChunks(flight);
            if (flight.remainingChunks() == 0) {
                return;
            }

            Object[] players = hoppers == null ? null : hoppers.regionPlayers();
            if (players == null || players.length == 0) {
                return;
            }

            int anchorCount = Math.min(MAX_FOLIA_PLAYER_ANCHORS, players.length);
            int start = Math.floorMod(nextFoliaPlayer.getAndAdd(anchorCount), players.length);
            int chunksPerAnchor = Math.max(1,
                (flight.remainingChunks() + anchorCount - 1) / anchorCount);
            for (int i = 0; i < anchorCount; i++) {
                Object player = players[(start + i) % players.length];
                if (player != null) {
                    scheduleFoliaPlayerPlan(player, flight, chunksPerAnchor);
                }
            }
        } catch (Throwable throwable) {
            reportReconcileFailure("folia reconcile planning", throwable);
        } finally {
            finishFoliaPlanTask(flight);
        }
    }

    private void scheduleFoliaPlayerPlan(Object player, FoliaPlanFlight flight, int chunkQuota) {
        flight.startTask();
        AtomicBoolean completed = new AtomicBoolean(false);
        Runnable completion = () -> {
            if (completed.compareAndSet(false, true)) {
                finishFoliaPlanTask(flight);
            }
        };
        Runnable task = () -> {
            try {
                planFoliaChunksAroundPlayer(player, flight, chunkQuota);
            } catch (Throwable throwable) {
                reportReconcileFailure("folia player planning", throwable);
            } finally {
                completion.run();
            }
        };

        boolean scheduled = false;
        try {
            scheduled = J.runEntity(player, task, 0, completion);
        } catch (Throwable throwable) {
            reportReconcileFailure("folia player plan scheduling", throwable);
        }
        if (!scheduled) {
            completion.run();
        }
    }

    private void planFoliaChunksAroundPlayer(Object player, FoliaPlanFlight flight, int chunkQuota) {
        if (!isCurrent(flight.generation())) {
            return;
        }

        GameLocation location = hoppers.playerLocation(player);
        if (location == null) {
            return;
        }
        UUID worldId = location.worldId();
        Object world = worldHandle(worldId);
        if (world == null) {
            return;
        }

        int side = FOLIA_CHUNK_RADIUS * 2 + 1;
        int cellCount = side * side;
        int start = Math.floorMod(nextFoliaChunkOffset.getAndAdd(chunkQuota), cellCount);
        int scheduled = 0;
        for (int i = 0; i < cellCount && scheduled < chunkQuota && flight.remainingChunks() > 0; i++) {
            int offset = (start + i) % cellCount;
            int chunkX = (location.blockX() >> 4) + offset % side - FOLIA_CHUNK_RADIUS;
            int chunkZ = (location.blockZ() >> 4) + offset / side - FOLIA_CHUNK_RADIUS;
            FoliaChunkTarget target = new FoliaChunkTarget(
                world,
                new ChunkKey(worldId, chunkX, chunkZ));
            if (scheduleFoliaChunk(target, flight)) {
                scheduled++;
            }
        }
    }

    private void drainPendingFoliaChunks(FoliaPlanFlight flight) {
        FoliaChunkWorkState workState = flight.workState();
        int attempts = Math.min(MAX_FOLIA_CHUNKS_PER_SWEEP, workState.pendingSize());
        for (int i = 0; i < attempts && flight.remainingChunks() > 0; i++) {
            FoliaChunkTarget target = pollPendingFoliaChunk(workState);
            if (target == null) {
                return;
            }
            if (!scheduleFoliaChunk(target, flight)) {
                enqueueFoliaChunk(workState, target);
            }
        }
    }

    private boolean scheduleFoliaChunk(FoliaChunkTarget target, FoliaPlanFlight flight) {
        ChunkKey key = target.key();
        if (!flight.markUnique(key)) {
            return false;
        }

        FoliaChunkClaim claim = claimFoliaChunk(flight.workState(), key, flight.generation());
        if (claim == null) {
            return false;
        }
        if (!flight.reserveChunk()) {
            releaseFoliaChunk(key, claim);
            return false;
        }

        Runnable task = () -> {
            boolean needsMore = false;
            try {
                if (isCurrentClaim(key, claim)) {
                    needsMore = reconcileFoliaChunk(target, claim);
                }
            } catch (Throwable throwable) {
                reportReconcileFailure("folia chunk reconcile", throwable);
                needsMore = true;
            } finally {
                releaseFoliaChunk(key, claim);
                if (needsMore && isCurrent(claim.generation())) {
                    enqueueFoliaChunk(claim.workState(), target);
                }
            }
        };

        boolean scheduled = false;
        try {
            scheduled = J.runChunk(
                target.world(),
                key.chunkX(),
                key.chunkZ(),
                task);
        } catch (Throwable throwable) {
            reportReconcileFailure("folia chunk scheduling", throwable);
        }
        if (!scheduled) {
            releaseFoliaChunk(key, claim);
            enqueueFoliaChunk(claim.workState(), target);
        }
        return scheduled;
    }

    private FoliaChunkClaim claimFoliaChunk(
            FoliaChunkWorkState workState,
            ChunkKey key,
            long generation) {
        while (isCurrent(generation) && workState == foliaChunkWorkState) {
            int current = workState.inFlightCount.get();
            if (current >= MAX_FOLIA_CHUNKS_PER_SWEEP
                    || !workState.inFlightCount.compareAndSet(current, current + 1)) {
                if (current >= MAX_FOLIA_CHUNKS_PER_SWEEP) {
                    return null;
                }
                continue;
            }

            FoliaChunkClaim claim = new FoliaChunkClaim(generation, workState);
            FoliaChunkClaim existing = workState.claims.putIfAbsent(key, claim);
            if (existing != null) {
                workState.inFlightCount.decrementAndGet();
                return null;
            }
            if (!isCurrent(generation) || workState != foliaChunkWorkState) {
                releaseFoliaChunk(key, claim);
                return null;
            }
            return claim;
        }
        return null;
    }

    private void releaseFoliaChunk(ChunkKey key, FoliaChunkClaim claim) {
        FoliaChunkWorkState workState = claim.workState();
        if (workState.claims.remove(key, claim)) {
            workState.inFlightCount.decrementAndGet();
        }
    }

    private boolean isCurrentClaim(ChunkKey key, FoliaChunkClaim claim) {
        FoliaChunkWorkState workState = claim.workState();
        return isCurrent(claim.generation())
            && workState == foliaChunkWorkState
            && workState.claims.get(key) == claim;
    }

    private boolean isCurrent(long generation) {
        return active && generation == lifecycleGeneration.get();
    }

    private void finishFoliaPlanTask(FoliaPlanFlight flight) {
        if (flight.finishTask() == 0 && flight.generation() == lifecycleGeneration.get()) {
            itemReconcileQueued.set(false);
        }
    }

    private boolean reconcileFoliaChunk(FoliaChunkTarget target, FoliaChunkClaim claim) {
        long generation = claim.generation();
        FoliaChunkWorkState workState = claim.workState();
        if (!isCurrent(generation) || workState != foliaChunkWorkState) {
            return false;
        }

        ChunkKey key = target.key();
        if (!isChunkLoaded(key)) {
            workState.cursors.remove(key);
            return false;
        }

        Object chunk = hoppers.chunk(key.worldId(), key.chunkX(), key.chunkZ());
        HopperItemIndex currentItemIndex = workState.itemIndex;
        HopperPositionIndex currentPositionIndex = workState.positionIndex;
        if (currentItemIndex == null || currentPositionIndex == null || !isCurrent(generation)) {
            return false;
        }

        ChunkReconcileCursor cursor = workState.cursors.computeIfAbsent(
            key,
            ignored -> new ChunkReconcileCursor());
        if (workState.seededChunks.add(key)) {
            try {
                seedFoliaChunkOnce(
                    chunk,
                    key,
                    currentPositionIndex,
                    generation);
            } catch (Throwable throwable) {
                workState.seededChunks.remove(key);
                throw throwable;
            }
            workState.cursors.remove(key, cursor);
            return false;
        }

        boolean needsMore = reconcileFoliaIndexedHoppers(
            key,
            cursor,
            currentPositionIndex,
            generation);
        if (!needsMore) {
            workState.cursors.remove(key, cursor);
        }
        return needsMore;
    }

    private void seedFoliaChunkOnce(
            Object chunk,
            ChunkKey key,
            HopperPositionIndex currentPositionIndex,
            long generation) {
        Object[] entities = hoppers.chunkEntities(chunk);
        for (Object entity : entities) {
            if (hoppers.isItem(entity)
                    && J.isOwnedByCurrentRegion(entity)
                    && isCurrent(generation)) {
                trackItem(entity);
            }
        }

        List<HopperHost.HopperBlock> states = hoppers.hoppers(chunk);
        for (HopperHost.HopperBlock state : states) {
            if (isCurrent(generation)) {
                currentPositionIndex.addHopper(
                    key.worldId(),
                    state.x(),
                    state.y(),
                    state.z());
            }
        }
    }

    private boolean reconcileFoliaIndexedHoppers(
            ChunkKey key,
            ChunkReconcileCursor cursor,
            HopperPositionIndex currentPositionIndex,
            long generation) {
        ScanWindow window = cursor.nextHopperWindow(
            currentPositionIndex.hoppersInChunk(
                key.worldId(),
                key.chunkX(),
                key.chunkZ()),
            MAX_INDEXED_HOPPERS_PER_CHUNK);

        try {
            int end = window.start() + window.count();
            for (int i = window.start(); i < end; i++) {
                long packed = cursor.hopperAt(i);
                int x = HopperPositionIndex.unpackX(packed);
                int y = HopperPositionIndex.unpackY(packed);
                int z = HopperPositionIndex.unpackZ(packed);
                if (isCurrent(generation) && !"HOPPER".equals(hoppers.blockType(key.worldId(), x, y, z))) {
                    currentPositionIndex.removeHopper(key.worldId(), x, y, z);
                }
            }
        } catch (Throwable throwable) {
            cursor.abortHopperCycle();
            reportReconcileFailure("folia hopper check", throwable);
            return true;
        }
        return !window.completedCycle();
    }

    private void enqueueFoliaChunk(Object world, UUID worldId, int chunkX, int chunkZ) {
        if (world == null) {
            return;
        }
        enqueueFoliaChunk(foliaChunkWorkState, new FoliaChunkTarget(
            world,
            new ChunkKey(worldId, chunkX, chunkZ)));
    }

    private boolean isChunkLoaded(ChunkKey key) {
        return isChunkLoaded(key.worldId(), key.chunkX(), key.chunkZ());
    }

    private void enqueueFoliaChunk(FoliaChunkWorkState workState, FoliaChunkTarget target) {
        if (!active || workState != foliaChunkWorkState) {
            return;
        }
        workState.offerPending(target, MAX_PENDING_FOLIA_CHUNKS);
    }

    private FoliaChunkTarget pollPendingFoliaChunk(FoliaChunkWorkState workState) {
        return workState.pollPending();
    }

    private void reconcileItems() {
        if (!active || itemIndex == null) {
            return;
        }

        long generation = lifecycleGeneration.get();
        List<ItemReconcileTarget> targets = drainItemBatch();
        for (ItemReconcileTarget target : targets) {
            boolean keep = reconcileTrackedItem(target, generation);
            finishItemReconcile(target, generation, keep, new AtomicBoolean(false));
        }
    }

    private void reconcileFoliaItems() {
        if (!active || itemIndex == null) {
            return;
        }

        long generation = lifecycleGeneration.get();
        List<ItemReconcileTarget> targets = drainItemBatch();
        for (ItemReconcileTarget target : targets) {
            AtomicBoolean completed = new AtomicBoolean(false);
            Runnable retired = () -> finishItemReconcile(target, generation, false, completed);
            Runnable task = () -> {
                boolean keep = true;
                try {
                    if (isCurrent(generation) && J.isOwnedByCurrentRegion(target.item())) {
                        keep = reconcileTrackedItem(target, generation);
                    }
                } catch (Throwable throwable) {
                    reportReconcileFailure("folia item reconcile", throwable);
                    keep = true;
                } finally {
                    finishItemReconcile(target, generation, keep, completed);
                }
            };

            boolean scheduled = false;
            try {
                scheduled = J.runEntity(target.item(), task, 0, retired);
            } catch (Throwable throwable) {
                reportReconcileFailure("folia item scheduling", throwable);
            }
            if (!scheduled) {
                finishItemReconcile(target, generation, true, completed);
            }
        }
    }

    private List<ItemReconcileTarget> drainItemBatch() {
        List<ItemReconcileTarget> targets = new ArrayList<>(MAX_ITEMS_PER_RECONCILE);
        for (int i = 0; i < MAX_ITEMS_PER_RECONCILE; i++) {
            UUID itemId = pendingItems.poll();
            if (itemId == null) {
                break;
            }
            if (!pendingItemIds.remove(itemId)) {
                continue;
            }
            WeakReference<Object> reference = trackedItems.get(itemId);
            if (reference == null) {
                continue;
            }
            Object item = reference.get();
            if (item == null) {
                forgetItem(itemId, reference);
                continue;
            }
            targets.add(new ItemReconcileTarget(itemId, reference, item));
        }
        return targets;
    }

    private boolean reconcileTrackedItem(ItemReconcileTarget target, long generation) {
        if (!isCurrent(generation) || trackedItems.get(target.itemId()) != target.reference()) {
            return false;
        }

        Object item = target.item();
        if (!hoppers.isLiveItem(item)) {
            removeItem(target.itemId());
            return false;
        }

        GameLocation location = hoppers.itemLocation(item);
        HopperItemIndex currentIndex = itemIndex;
        if (location == null || currentIndex == null || !isCurrent(generation)) {
            return true;
        }

        currentIndex.addItem(
            location.worldId(),
            location.blockX() >> 4,
            location.blockZ() >> 4,
            target.itemId());
        return true;
    }

    private void finishItemReconcile(
            ItemReconcileTarget target,
            long generation,
            boolean keep,
            AtomicBoolean completed) {
        if (!completed.compareAndSet(false, true) || !isCurrent(generation)) {
            return;
        }
        if (keep && trackedItems.get(target.itemId()) == target.reference()) {
            enqueueItem(target.itemId());
            return;
        }
        forgetItem(target.itemId(), target.reference());
    }

    private void forgetItem(UUID itemId, WeakReference<Object> reference) {
        if (trackedItems.remove(itemId, reference)) {
            HopperItemIndex currentIndex = itemIndex;
            if (currentIndex != null) {
                currentIndex.removeItem(itemId);
            }
        }
    }

    private void trackItem(Object item) {
        HopperItemIndex currentIndex = itemIndex;
        HopperHost host = hoppers;
        if (item == null || currentIndex == null || host == null) {
            return;
        }

        UUID itemId = host.itemId(item);
        if (!host.isLiveItem(item)) {
            removeItem(itemId);
            return;
        }

        GameLocation location = host.itemLocation(item);
        if (location == null) {
            return;
        }
        currentIndex.addItem(
            location.worldId(),
            location.blockX() >> 4,
            location.blockZ() >> 4,
            itemId);
        WeakReference<Object> tracked = trackedItems.get(itemId);
        if (tracked == null || tracked.get() != item) {
            trackedItems.put(itemId, new WeakReference<>(item));
        }
        enqueueItem(itemId);
    }

    private void removeItem(UUID itemId) {
        trackedItems.remove(itemId);
        pendingItemIds.remove(itemId);
        HopperItemIndex currentIndex = itemIndex;
        if (currentIndex != null) {
            currentIndex.removeItem(itemId);
        }
    }

    private void reportReconcileFailure(String site, Throwable failure) {
        ReconcileFailureWindow window = reconcileFailureWindows.computeIfAbsent(
            ReconcileFailureKey.of(site, failure),
            ignored -> new ReconcileFailureWindow());
        window.report(site, failure, System.currentTimeMillis());
    }

    private void enqueueItem(UUID itemId) {
        if (itemId != null && trackedItems.containsKey(itemId) && pendingItemIds.add(itemId)) {
            pendingItems.offer(itemId);
        }
    }

    private static final class FoliaPlanFlight {
        private final long generation;
        private final FoliaChunkWorkState workState;
        private final AtomicInteger pendingTasks;
        private final AtomicInteger remainingChunks;
        private final Set<ChunkKey> uniqueChunks;

        private FoliaPlanFlight(long generation, FoliaChunkWorkState workState) {
            this.generation = generation;
            this.workState = workState;
            pendingTasks = new AtomicInteger(1);
            remainingChunks = new AtomicInteger(MAX_FOLIA_CHUNKS_PER_SWEEP);
            uniqueChunks = ConcurrentHashMap.newKeySet();
        }

        private long generation() {
            return generation;
        }

        private FoliaChunkWorkState workState() {
            return workState;
        }

        private void startTask() {
            pendingTasks.incrementAndGet();
        }

        private int finishTask() {
            return pendingTasks.decrementAndGet();
        }

        private int remainingChunks() {
            return Math.max(0, remainingChunks.get());
        }

        private boolean markUnique(ChunkKey key) {
            return uniqueChunks.add(key);
        }

        private boolean reserveChunk() {
            while (true) {
                int current = remainingChunks.get();
                if (current <= 0) {
                    return false;
                }
                if (remainingChunks.compareAndSet(current, current - 1)) {
                    return true;
                }
            }
        }
    }

    private static final class FoliaChunkWorkState {
        private final HopperItemIndex itemIndex;
        private final HopperPositionIndex positionIndex;
        private final Map<ChunkKey, ChunkReconcileCursor> cursors;
        private final Set<ChunkKey> seededChunks;
        private final Map<ChunkKey, FoliaChunkClaim> claims;
        private final PendingChunkRotation pendingChunks;
        private final AtomicInteger inFlightCount;

        private FoliaChunkWorkState(HopperItemIndex itemIndex, HopperPositionIndex positionIndex) {
            this.itemIndex = itemIndex;
            this.positionIndex = positionIndex;
            cursors = new ConcurrentHashMap<>();
            seededChunks = ConcurrentHashMap.newKeySet();
            claims = new ConcurrentHashMap<>();
            pendingChunks = new PendingChunkRotation();
            inFlightCount = new AtomicInteger(0);
        }

        private int pendingSize() {
            return pendingChunks.size();
        }

        private void offerPending(FoliaChunkTarget target, int maximum) {
            pendingChunks.offer(target, maximum);
        }

        private FoliaChunkTarget pollPending() {
            return pendingChunks.poll();
        }

        private void retireChunk(ChunkKey key) {
            cursors.remove(key);
            seededChunks.remove(key);
            pendingChunks.remove(key);
            FoliaChunkClaim claim = claims.remove(key);
            if (claim != null) {
                inFlightCount.decrementAndGet();
            }
        }

        private void removeWorld(UUID worldId) {
            cursors.keySet().removeIf(key -> key.worldId().equals(worldId));
            seededChunks.removeIf(key -> key.worldId().equals(worldId));
            pendingChunks.removeWorld(worldId);
            for (Map.Entry<ChunkKey, FoliaChunkClaim> entry : claims.entrySet()) {
                if (entry.getKey().worldId().equals(worldId)
                        && claims.remove(entry.getKey(), entry.getValue())) {
                    inFlightCount.decrementAndGet();
                }
            }
        }
    }

    private static final class ChunkReconcileCursor {
        private Object[] paperEntitySeedSnapshot;
        private int paperEntitySeedOffset;
        private boolean paperEntitySeedComplete;
        private Iterator<HopperHost.HopperBlock> paperHopperSeedIterator;
        private long[] hopperSnapshot = new long[0];
        private int hopperOffset;

        private boolean paperEntitySeedComplete() {
            return paperEntitySeedComplete;
        }

        private EntitySeedWindow nextPaperEntitySeedWindow(HopperHost host, Object chunk, int limit) {
            if (paperEntitySeedSnapshot == null) {
                paperEntitySeedSnapshot = host.chunkEntities(chunk);
                paperEntitySeedOffset = 0;
            }
            Object[] snapshot = paperEntitySeedSnapshot;
            int start = Math.min(paperEntitySeedOffset, snapshot.length);
            int count = Math.min(Math.max(0, limit), snapshot.length - start);
            boolean completed = start + count >= snapshot.length;
            paperEntitySeedOffset = completed ? 0 : start + count;
            if (completed) {
                paperEntitySeedSnapshot = null;
                paperEntitySeedComplete = true;
            }
            return new EntitySeedWindow(snapshot, start, count, completed);
        }

        private Iterator<HopperHost.HopperBlock> paperHopperSeedIterator(HopperHost host, Object chunk) {
            if (paperHopperSeedIterator == null) {
                paperHopperSeedIterator = host.hoppers(chunk).iterator();
            }
            return paperHopperSeedIterator;
        }

        private void finishPaperSeed() {
            paperEntitySeedSnapshot = null;
            paperEntitySeedOffset = 0;
            paperEntitySeedComplete = false;
            paperHopperSeedIterator = null;
        }

        private void abortPaperSeed() {
            finishPaperSeed();
        }

        private ScanWindow nextHopperWindow(long[] current, int limit) {
            if (hopperOffset == 0) {
                hopperSnapshot = current;
            }
            if (hopperSnapshot.length == 0) {
                return new ScanWindow(0, 0, true);
            }
            int start = hopperOffset;
            int count = Math.min(limit, hopperSnapshot.length - start);
            boolean completed = start + count >= hopperSnapshot.length;
            hopperOffset = completed ? 0 : start + count;
            return new ScanWindow(start, count, completed);
        }

        private long hopperAt(int index) {
            return hopperSnapshot[index];
        }

        private void abortHopperCycle() {
            hopperSnapshot = new long[0];
            hopperOffset = 0;
        }
    }

    private static final class PaperReconcileBudget {
        private int remainingEntityInspections = MAX_PAPER_ENTITY_SEED_INSPECTIONS;
        private int remainingHopperSeeds = MAX_PAPER_HOPPER_SEEDS;
        private int remainingHopperChecks = MAX_INDEXED_HOPPERS_PER_CHUNK;

        private int remainingEntityInspections() {
            return remainingEntityInspections;
        }

        private int remainingHopperSeeds() {
            return remainingHopperSeeds;
        }

        private int remainingHopperChecks() {
            return remainingHopperChecks;
        }

        private void consumeEntityInspections(int count) {
            remainingEntityInspections = Math.max(0, remainingEntityInspections - Math.max(0, count));
        }

        private void consumeHopperSeeds(int count) {
            remainingHopperSeeds = Math.max(0, remainingHopperSeeds - Math.max(0, count));
        }

        private void consumeHopperChecks(int count) {
            remainingHopperChecks = Math.max(0, remainingHopperChecks - Math.max(0, count));
        }
    }

    private static final class PendingChunkRotation {
        private final Map<ChunkKey, FoliaChunkTarget> targets = new LinkedHashMap<>();

        private synchronized void offer(FoliaChunkTarget target, int maximum) {
            if (target == null || targets.containsKey(target.key()) || targets.size() >= maximum) {
                return;
            }
            targets.put(target.key(), target);
        }

        private synchronized FoliaChunkTarget poll() {
            if (targets.isEmpty()) {
                return null;
            }
            Iterator<Map.Entry<ChunkKey, FoliaChunkTarget>> iterator = targets.entrySet().iterator();
            FoliaChunkTarget target = iterator.next().getValue();
            iterator.remove();
            return target;
        }

        private synchronized void remove(ChunkKey key) {
            targets.remove(key);
        }

        private synchronized void removeWorld(UUID worldId) {
            targets.keySet().removeIf(key -> key.worldId().equals(worldId));
        }

        private synchronized int size() {
            return targets.size();
        }
    }

    private record ScanWindow(int start, int count, boolean completedCycle) {
    }

    private record EntitySeedWindow(Object[] entities, int start, int count, boolean completed) {
    }

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {
    }

    private record FoliaChunkTarget(Object world, ChunkKey key) {
    }

    private record FoliaChunkClaim(long generation, FoliaChunkWorkState workState) {
    }

    private record ItemReconcileTarget(UUID itemId, WeakReference<Object> reference, Object item) {
    }

    private record ReconcileFailureKey(String site, Class<? extends Throwable> type, StackTraceElement origin) {
        private static ReconcileFailureKey of(String site, Throwable failure) {
            StackTraceElement[] trace = failure.getStackTrace();
            return new ReconcileFailureKey(site, failure.getClass(), trace.length == 0 ? null : trace[0]);
        }
    }

    private static final class ReconcileFailureWindow {
        private final AtomicLong lastReportMs = new AtomicLong(0L);
        private final AtomicInteger suppressedReports = new AtomicInteger(0);

        private void report(String site, Throwable failure, long now) {
            long last = lastReportMs.get();
            if (now - last < RECONCILE_FAILURE_REPORT_INTERVAL_MS || !lastReportMs.compareAndSet(last, now)) {
                suppressedReports.incrementAndGet();
                return;
            }
            int suppressed = suppressedReports.getAndSet(0);
            String context = suppressed == 0
                ? "Hopper item index " + site + " failed"
                : "Hopper item index " + site + " failed (" + suppressed + " repeats suppressed since last report)";
            ReactPlatforms.get().reportError(context, failure);
        }
    }

    private boolean isChunkLoaded(UUID worldId, int chunkX, int chunkZ) {
        ChunkHost host = chunkHost;
        return host != null && host.isLoaded(worldId, chunkX, chunkZ);
    }

    private List<ChunkRef> nextLoadedChunks(int maximum) {
        ChunkHost host = chunkHost;
        return host == null ? List.of() : host.nextLoaded(maximum);
    }
}
