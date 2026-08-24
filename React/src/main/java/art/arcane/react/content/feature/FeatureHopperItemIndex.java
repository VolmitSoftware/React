package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.core.controller.HopperItemIndex;
import art.arcane.react.core.controller.HopperPositionIndex;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;
import java.util.Collection;
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
public class FeatureHopperItemIndex extends ReactFeature implements Listener {
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

    private transient final AtomicBoolean itemReconcileQueued = new AtomicBoolean(false);
    private transient final AtomicBoolean hopperReconcileQueued = new AtomicBoolean(false);
    private transient final AtomicInteger nextFoliaPlayer = new AtomicInteger(0);
    private transient final AtomicInteger nextFoliaChunkOffset = new AtomicInteger(0);
    private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
    private transient final Map<UUID, Item> trackedItems = new ConcurrentHashMap<>();
    private transient final Queue<UUID> pendingItems = new ConcurrentLinkedQueue<>();
    private transient final Set<UUID> pendingItemIds = ConcurrentHashMap.newKeySet();
    private transient volatile FoliaChunkWorkState foliaChunkWorkState = new FoliaChunkWorkState(null, null);
    private transient volatile boolean active;

    @ConfigDoc(value = "Interval in milliseconds between reconciliation sweeps that correct index drift.", impact = "Lower values catch drift faster at slightly higher overhead. Default matches 40 server ticks at 20 TPS.")
    private int reconcileIntervalMs = 2000;

    private transient HopperItemIndex itemIndex;
    private transient HopperPositionIndex positionIndex;

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
        active = true;
    }

    @Override
    public void onDeactivate() {
        active = false;
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ItemSpawnEvent event) {
        trackItem(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ItemDespawnEvent event) {
        removeItem(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityPickupItemEvent event) {
        removeItem(event.getItem().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(InventoryPickupItemEvent event) {
        removeItem(event.getItem().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Item item) {
                trackItem(item);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Item) {
                removeItem(entity.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(BlockPlaceEvent event) {
        if (positionIndex == null) {
            return;
        }
        Block block = event.getBlock();
        if (block.getType() != Material.HOPPER) {
            return;
        }
        UUID worldId = block.getWorld().getUID();
        positionIndex.addHopper(worldId, block.getX(), block.getY(), block.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(BlockBreakEvent event) {
        if (positionIndex == null) {
            return;
        }
        Block block = event.getBlock();
        if (block.getType() != Material.HOPPER) {
            return;
        }
        UUID worldId = block.getWorld().getUID();
        positionIndex.removeHopper(worldId, block.getX(), block.getY(), block.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(BlockExplodeEvent event) {
        if (positionIndex == null) {
            return;
        }
        UUID worldId = event.getBlock().getWorld().getUID();
        for (Block block : event.blockList()) {
            if (block.getType() == Material.HOPPER) {
                positionIndex.removeHopper(worldId, block.getX(), block.getY(), block.getZ());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityExplodeEvent event) {
        if (positionIndex == null) {
            return;
        }
        UUID worldId = event.getLocation().getWorld().getUID();
        for (Block block : event.blockList()) {
            if (block.getType() == Material.HOPPER) {
                positionIndex.removeHopper(worldId, block.getX(), block.getY(), block.getZ());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(BlockPistonExtendEvent event) {
        if (positionIndex == null) {
            return;
        }
        BlockFace direction = event.getDirection();
        UUID worldId = event.getBlock().getWorld().getUID();
        for (Block moved : event.getBlocks()) {
            if (moved.getType() == Material.HOPPER) {
                positionIndex.removeHopper(worldId, moved.getX(), moved.getY(), moved.getZ());
                positionIndex.addHopper(worldId,
                    moved.getX() + direction.getModX(),
                    moved.getY() + direction.getModY(),
                    moved.getZ() + direction.getModZ());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(BlockPistonRetractEvent event) {
        if (positionIndex == null) {
            return;
        }
        BlockFace direction = event.getDirection();
        UUID worldId = event.getBlock().getWorld().getUID();
        for (Block moved : event.getBlocks()) {
            if (moved.getType() == Material.HOPPER) {
                positionIndex.removeHopper(worldId, moved.getX(), moved.getY(), moved.getZ());
                positionIndex.addHopper(worldId,
                    moved.getX() - direction.getModX(),
                    moved.getY() - direction.getModY(),
                    moved.getZ() - direction.getModZ());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ChunkLoadEvent event) {
        if (itemIndex == null || positionIndex == null) {
            return;
        }
        Chunk chunk = event.getChunk();
        enqueueFoliaChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        UUID worldId = chunk.getWorld().getUID();
        int cx = chunk.getX();
        int cz = chunk.getZ();
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(WorldUnloadEvent event) {
        UUID worldId = event.getWorld().getUID();
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
                React.reportError(throwable);
                needsMore = true;
            }
            if (needsMore && isCurrent(generation) && workState == foliaChunkWorkState) {
                enqueueFoliaChunk(workState, target);
            }
        }
    }

    private void importPaperCoordinateWindow(FoliaChunkWorkState workState) {
        ObserverController observer = React.controller(ObserverController.class);
        if (observer == null || workState != foliaChunkWorkState) {
            return;
        }

        List<ObserverController.LoadedChunkTarget> coordinates = observer.nextLoadedChunkCoordinateBatch(
            MAX_PAPER_CHUNKS_PER_SWEEP);
        for (ObserverController.LoadedChunkTarget coordinate : coordinates) {
            World world = Bukkit.getWorld(coordinate.worldId());
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
        World world = Bukkit.getWorld(key.worldId());
        if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
            workState.retireChunk(key);
            return false;
        }

        Chunk chunk = world.getChunkAt(key.chunkX(), key.chunkZ(), false);
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
            world,
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
            Chunk chunk,
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
                EntitySeedWindow window = cursor.nextPaperEntitySeedWindow(chunk, maximum);
                int end = window.start() + window.count();
                for (int index = window.start(); index < end; index++) {
                    Entity entity = window.entities()[index];
                    if (entity instanceof Item item && isCurrent(generation)) {
                        trackItem(item);
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
            Iterator<BlockState> hopperStates = cursor.paperHopperSeedIterator(chunk);
            while (processed < maximum && hopperStates.hasNext()) {
                BlockState state = hopperStates.next();
                if (isCurrent(generation)) {
                    currentPositionIndex.addHopper(
                        key.worldId(),
                        state.getX(),
                        state.getY(),
                        state.getZ());
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
            World world,
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
                if (isCurrent(generation) && world.getBlockAt(x, y, z).getType() != Material.HOPPER) {
                    currentPositionIndex.removeHopper(key.worldId(), x, y, z);
                }
            }
        } catch (Throwable throwable) {
            cursor.abortHopperCycle();
            React.reportError(throwable);
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

            EntityController controller = React.controller(EntityController.class);
            Player[] players = controller == null ? null : controller.getFoliaPlayers();
            if (players == null || players.length == 0) {
                return;
            }

            int anchorCount = Math.min(MAX_FOLIA_PLAYER_ANCHORS, players.length);
            int start = Math.floorMod(nextFoliaPlayer.getAndAdd(anchorCount), players.length);
            int chunksPerAnchor = Math.max(1,
                (flight.remainingChunks() + anchorCount - 1) / anchorCount);
            for (int i = 0; i < anchorCount; i++) {
                Player player = players[(start + i) % players.length];
                if (player != null) {
                    scheduleFoliaPlayerPlan(player, flight, chunksPerAnchor);
                }
            }
        } catch (Throwable throwable) {
            React.reportError(throwable);
        } finally {
            finishFoliaPlanTask(flight);
        }
    }

    private void scheduleFoliaPlayerPlan(Player player, FoliaPlanFlight flight, int chunkQuota) {
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
                React.reportError(throwable);
            } finally {
                completion.run();
            }
        };

        boolean scheduled = false;
        try {
            scheduled = J.runEntity(player, task, 0, completion);
        } catch (Throwable throwable) {
            React.reportError(throwable);
        }
        if (!scheduled) {
            completion.run();
        }
    }

    private void planFoliaChunksAroundPlayer(Player player, FoliaPlanFlight flight, int chunkQuota) {
        if (!isCurrent(flight.generation()) || !player.isOnline() || !J.isOwnedByCurrentRegion(player)) {
            return;
        }

        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        int side = FOLIA_CHUNK_RADIUS * 2 + 1;
        int cellCount = side * side;
        int start = Math.floorMod(nextFoliaChunkOffset.getAndAdd(chunkQuota), cellCount);
        int scheduled = 0;
        for (int i = 0; i < cellCount && scheduled < chunkQuota && flight.remainingChunks() > 0; i++) {
            int offset = (start + i) % cellCount;
            int chunkX = (location.getBlockX() >> 4) + offset % side - FOLIA_CHUNK_RADIUS;
            int chunkZ = (location.getBlockZ() >> 4) + offset / side - FOLIA_CHUNK_RADIUS;
            FoliaChunkTarget target = new FoliaChunkTarget(
                world,
                new ChunkKey(world.getUID(), chunkX, chunkZ));
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
                React.reportError(throwable);
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
            React.reportError(throwable);
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
        World world = target.world();
        if (!world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
            workState.cursors.remove(key);
            return false;
        }

        Chunk chunk = world.getChunkAt(key.chunkX(), key.chunkZ(), false);
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
            world,
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
            Chunk chunk,
            ChunkKey key,
            HopperPositionIndex currentPositionIndex,
            long generation) {
        Entity[] entities = chunk.getEntities();
        for (Entity entity : entities) {
            if (entity instanceof Item item
                    && J.isOwnedByCurrentRegion(item)
                    && isCurrent(generation)) {
                trackItem(item);
            }
        }

        Collection<BlockState> states = chunk.getTileEntities(
            block -> block.getType() == Material.HOPPER,
            false);
        for (BlockState state : states) {
            if (isCurrent(generation)) {
                currentPositionIndex.addHopper(
                    key.worldId(),
                    state.getX(),
                    state.getY(),
                    state.getZ());
            }
        }
    }

    private boolean reconcileFoliaIndexedHoppers(
            World world,
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
                if (isCurrent(generation) && world.getBlockAt(x, y, z).getType() != Material.HOPPER) {
                    currentPositionIndex.removeHopper(key.worldId(), x, y, z);
                }
            }
        } catch (Throwable throwable) {
            cursor.abortHopperCycle();
            React.reportError(throwable);
            return true;
        }
        return !window.completedCycle();
    }

    private void enqueueFoliaChunk(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return;
        }
        enqueueFoliaChunk(foliaChunkWorkState, new FoliaChunkTarget(
            world,
            new ChunkKey(world.getUID(), chunkX, chunkZ)));
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
                    React.reportError(throwable);
                    keep = true;
                } finally {
                    finishItemReconcile(target, generation, keep, completed);
                }
            };

            boolean scheduled = false;
            try {
                scheduled = J.runEntity(target.item(), task, 0, retired);
            } catch (Throwable throwable) {
                React.reportError(throwable);
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
            Item item = trackedItems.get(itemId);
            if (item != null) {
                targets.add(new ItemReconcileTarget(itemId, item));
            }
        }
        return targets;
    }

    private boolean reconcileTrackedItem(ItemReconcileTarget target, long generation) {
        if (!isCurrent(generation) || trackedItems.get(target.itemId()) != target.item()) {
            return false;
        }

        Item item = target.item();
        if (!item.isValid() || item.isDead()) {
            removeItem(target.itemId());
            return false;
        }

        Location location = item.getLocation();
        World world = location.getWorld();
        HopperItemIndex currentIndex = itemIndex;
        if (world == null || currentIndex == null || !isCurrent(generation)) {
            return true;
        }

        currentIndex.addItem(
            world.getUID(),
            location.getBlockX() >> 4,
            location.getBlockZ() >> 4,
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
        if (keep && trackedItems.get(target.itemId()) == target.item()) {
            enqueueItem(target.itemId());
            return;
        }
        if (trackedItems.remove(target.itemId(), target.item())) {
            HopperItemIndex currentIndex = itemIndex;
            if (currentIndex != null) {
                currentIndex.removeItem(target.itemId());
            }
        }
    }

    private void trackItem(Item item) {
        HopperItemIndex currentIndex = itemIndex;
        if (item == null || currentIndex == null) {
            return;
        }

        UUID itemId = item.getUniqueId();
        if (!item.isValid() || item.isDead()) {
            removeItem(itemId);
            return;
        }

        Location location = item.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        currentIndex.addItem(
            world.getUID(),
            location.getBlockX() >> 4,
            location.getBlockZ() >> 4,
            itemId);
        trackedItems.put(itemId, item);
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
        private Entity[] paperEntitySeedSnapshot;
        private int paperEntitySeedOffset;
        private boolean paperEntitySeedComplete;
        private Iterator<BlockState> paperHopperSeedIterator;
        private long[] hopperSnapshot = new long[0];
        private int hopperOffset;

        private boolean paperEntitySeedComplete() {
            return paperEntitySeedComplete;
        }

        private EntitySeedWindow nextPaperEntitySeedWindow(Chunk chunk, int limit) {
            if (paperEntitySeedSnapshot == null) {
                paperEntitySeedSnapshot = chunk.getEntities();
                paperEntitySeedOffset = 0;
            }
            Entity[] snapshot = paperEntitySeedSnapshot;
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

        private Iterator<BlockState> paperHopperSeedIterator(Chunk chunk) {
            if (paperHopperSeedIterator == null) {
                paperHopperSeedIterator = chunk.getTileEntities(
                    block -> block.getType() == Material.HOPPER,
                    false).iterator();
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

    private record EntitySeedWindow(Entity[] entities, int start, int count, boolean completed) {
    }

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {
    }

    private record FoliaChunkTarget(World world, ChunkKey key) {
    }

    private record FoliaChunkClaim(long generation, FoliaChunkWorkState workState) {
    }

    private record ItemReconcileTarget(UUID itemId, Item item) {
    }
}
