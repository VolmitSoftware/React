package art.arcane.react.content.tweak;

import art.arcane.react.React;
import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.content.feature.FeatureHopperContainerThroughputMap;
import art.arcane.react.content.feature.FeatureHopperItemIndex;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.NMS;
import art.arcane.react.core.bridge.BridgeKind;
import art.arcane.react.core.bridge.NmsBridgeDescriptor;
import art.arcane.react.core.bridge.NmsBridgeHandle;
import art.arcane.react.core.controller.HopperItemIndex;
import art.arcane.react.core.controller.HopperPositionIndex;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.config.ConfigDescription;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@ConfigDescription("Pre-ticks hoppers using a spatial item index to short-circuit vanilla's per-tick AABB entity scan. Fails closed to vanilla if any required NMS bridge is unavailable.")
public class TweakHopperIndex extends ReactTweak {
    public static final String ID = "hopper-index";
    public static final String BRIDGE_ADD_ITEM = "HopperBlockEntity.addItem";
    public static final String BRIDGE_COOLDOWN_TIME = "HopperBlockEntity.cooldownTime";
    public static final String BRIDGE_GET_BLOCK_ENTITY = "Level.getBlockEntity";
    public static final String BRIDGE_BLOCK_POS_CTOR = "BlockPos.constructor";
    public static final String BRIDGE_IS_EMPTY = "HopperBlockEntity.isEmpty";

    private static final int HOPPER_COOLDOWN_TICKS = 8;
    private static final double COLLECTION_HALF_EXTENT_H = 0.6;
    private static final double COLLECTION_Y_MIN_OFFSET = 0.9;
    private static final double COLLECTION_Y_MAX_OFFSET = 2.1;
    private static final int MAX_IDLE_STRETCH_PROBES_PER_TICK = 256;
    private static final int MAX_ITEM_CHUNK_BUDGET = 4096;
    private static final Object UNAVAILABLE_ITEM_HANDLE = new Object();

    @art.arcane.react.util.project.config.ConfigDoc(value = "Stretches the transfer cooldown of empty, idle hoppers while the server is under load so vanilla skips re-polling them.", impact = "Enable to shed idle hopper tick cost on hopper-heavy servers; item pickup through the index fast-path stays instant.")
    private boolean idleStretch = true;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Cooldown in ticks applied to empty idle hoppers; bounds the worst-case delay before an idle hopper resumes pulling from a container.", impact = "Higher values skip more idle hopper work but delay transfer resumption longer.")
    private int idleStretchTicks = 40;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Number of tick passes each idle hopper probe is spread across.", impact = "Higher values probe each hopper less often (cheaper, slower to stretch); lower values stretch idle hoppers sooner.")
    private int idleStretchSpreadPasses = 40;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds required before idle hopper stretching engages.", impact = "Lower values stretch idle hoppers earlier; higher values reserve it for heavier load.")
    private double idleStretchMinTickMs = 45;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum item-bearing chunks inspected across all worlds per tick.", impact = "Higher values revisit indexed chunks faster but can schedule more region work; values clamp to 1..4096.")
    private int itemChunkBudgetPerTick = 64;

    private transient final Map<UUID, ItemChunkCursor> itemChunkCursors = new HashMap<>();
    private transient final Map<WorldChunkKey, ItemChunkClaim> itemChunksInFlight = new ConcurrentHashMap<>();
    private transient final AtomicInteger nextWorld = new AtomicInteger(0);
    private transient final AtomicLong itemChunkPass = new AtomicLong(0L);
    private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
    private transient final AtomicBoolean idleStretchFailureReported = new AtomicBoolean();
    private transient NmsBridgeHandle bridgeAddItem;
    private transient NmsBridgeHandle bridgeCooldownTime;
    private transient NmsBridgeHandle bridgeGetBlockEntity;
    private transient NmsBridgeHandle bridgeBlockPosCtor;
    private transient NmsBridgeHandle bridgeIsEmpty;
    private transient boolean bridgesAvailable;
    private transient volatile boolean active = true;
    private transient int tickTaskId;
    private transient FeatureHopperItemIndex indexFeature;
    private transient HopperItemIndex cursorSource;

    public TweakHopperIndex() {
        super(ID);
    }

    public static List<NmsBridgeDescriptor> hopperBridgeDescriptors() {
        List<String> hopperClasses = List.of(
            "net.minecraft.world.level.block.entity.HopperBlockEntity",
            "net.minecraft.world.level.block.entity.TileEntityHopper");
        List<String> levelClasses = List.of(
            "net.minecraft.world.level.Level",
            "net.minecraft.world.level.World");
        List<String> blockPosClasses = List.of(
            "net.minecraft.core.BlockPos",
            "net.minecraft.core.BlockPosition");
        return List.of(
            new NmsBridgeDescriptor(
                BRIDGE_ADD_ITEM, BridgeKind.STATIC_METHOD, hopperClasses, "addItem",
                List.of(
                    List.of("net.minecraft.world.Container", "net.minecraft.world.entity.item.ItemEntity"),
                    List.of("net.minecraft.world.IInventory", "net.minecraft.world.entity.item.EntityItem")),
                "boolean",
                Optional.of("HopperBlockEntity.addItem")),
            new NmsBridgeDescriptor(
                BRIDGE_COOLDOWN_TIME, BridgeKind.FIELD, hopperClasses, "cooldownTime",
                List.of(),
                "int",
                Optional.of("HopperBlockEntity.cooldownTime")),
            new NmsBridgeDescriptor(
                BRIDGE_GET_BLOCK_ENTITY, BridgeKind.METHOD, levelClasses, "getBlockEntity",
                List.of(
                    List.of("net.minecraft.core.BlockPos"),
                    List.of("net.minecraft.core.BlockPosition")),
                "net.minecraft.world.level.block.entity.BlockEntity",
                Optional.of("Level.getBlockEntity")),
            new NmsBridgeDescriptor(
                BRIDGE_BLOCK_POS_CTOR, BridgeKind.CONSTRUCTOR, blockPosClasses, "<init>",
                List.of(List.of("int", "int", "int")),
                blockPosClasses.get(0),
                Optional.empty()),
            new NmsBridgeDescriptor(
                BRIDGE_IS_EMPTY, BridgeKind.METHOD, hopperClasses, "isEmpty",
                List.of(List.of()),
                "boolean",
                Optional.of("HopperBlockEntity.isEmpty"))
        );
    }

    @Override
    public void onActivate() {
        active = false;
        lifecycleGeneration.incrementAndGet();
        idleStretchFailureReported.set(false);
        resetItemChunkState(null);
        List<NmsBridgeDescriptor> descriptors = hopperBridgeDescriptors();
        bridgeAddItem = React.bridgeRegistry().resolve(descriptors.get(0));
        bridgeCooldownTime = React.bridgeRegistry().resolve(descriptors.get(1));
        bridgeGetBlockEntity = React.bridgeRegistry().resolve(descriptors.get(2));
        bridgeBlockPosCtor = React.bridgeRegistry().resolve(descriptors.get(3));
        bridgeIsEmpty = React.bridgeRegistry().resolve(descriptors.get(4));
        bridgesAvailable = checkBridgesAvailable();
        if (!bridgesAvailable) {
            logUnavailableBridge();
            return;
        }
        active = true;
        tickTaskId = J.sr(this::tickAllWorlds, 1);
    }

    @Override
    public void onDeactivate() {
        active = false;
        lifecycleGeneration.incrementAndGet();
        if (tickTaskId != 0) {
            J.csr(tickTaskId);
            tickTaskId = 0;
        }
        bridgesAvailable = false;
        indexFeature = null;
        resetItemChunkState(null);
    }

    private boolean checkBridgesAvailable() {
        return bridgeAddItem.available()
            && bridgeCooldownTime.available()
            && bridgeGetBlockEntity.available()
            && bridgeBlockPosCtor.available();
    }

    private void logUnavailableBridge() {
        String failing = "";
        if (!bridgeAddItem.available()) {
            failing = BRIDGE_ADD_ITEM;
        } else if (!bridgeCooldownTime.available()) {
            failing = BRIDGE_COOLDOWN_TIME;
        } else if (!bridgeGetBlockEntity.available()) {
            failing = BRIDGE_GET_BLOCK_ENTITY;
        } else if (!bridgeBlockPosCtor.available()) {
            failing = BRIDGE_BLOCK_POS_CTOR;
        }
        React.warn("Hopper index fast-path disabled: NMS bridge unavailable (" + failing + ")");
    }

    void tickAllWorlds() {
        if (!bridgesAvailable) {
            return;
        }
        FeatureHopperItemIndex feature = indexFeature;
        if (feature == null) {
            feature = React.feature(FeatureHopperItemIndex.class);
            indexFeature = feature;
        }
        if (feature == null) {
            return;
        }
        HopperItemIndex itemIndex = feature.getItemIndex();
        HopperPositionIndex positionIndex = feature.getPositionIndex();
        if (itemIndex == null || positionIndex == null) {
            return;
        }
        if (cursorSource != itemIndex) {
            lifecycleGeneration.incrementAndGet();
            resetItemChunkState(itemIndex);
        }
        int spread = Math.max(1, idleStretchSpreadPasses);
        int stretchCooldown = effectiveIdleStretchCooldown(idleStretchTicks, spread);
        boolean stretchEligible = idleStretch
            && stretchCooldown > HOPPER_COOLDOWN_TICKS
            && bridgeIsEmpty.available()
            && sample(SamplerTickTime.ID) >= idleStretchMinTickMs;
        boolean foliaThreading = J.isFoliaThreading();
        List<World> worlds = Bukkit.getWorlds();
        Map<UUID, Long> claimedHoppersByWorld = processItemChunkBudget(
            worlds,
            itemIndex,
            positionIndex,
            foliaThreading);
        finishWorldTick(worlds, itemIndex, positionIndex, foliaThreading, spread, stretchCooldown,
            stretchEligible, claimedHoppersByWorld);
    }

    private Map<UUID, Long> processItemChunkBudget(
            List<World> worlds,
            HopperItemIndex itemIndex,
            HopperPositionIndex positionIndex,
            boolean foliaThreading) {
        Map<UUID, Long> claimedHoppersByWorld = new HashMap<>();
        if (worlds.isEmpty()) {
            return claimedHoppersByWorld;
        }

        int budget = Math.max(1, Math.min(MAX_ITEM_CHUNK_BUDGET, itemChunkBudgetPerTick));
        int worldIndex = Math.floorMod(nextWorld.get(), worlds.size());
        int emptyWorlds = 0;
        int scheduledChunks = 0;
        long pass = itemChunkPass.incrementAndGet();
        Set<WorldChunkKey> scheduledThisTick = new HashSet<>();
        for (int inspected = 0; inspected < budget
                && scheduledChunks < budget
                && emptyWorlds < worlds.size(); inspected++) {
            World world = worlds.get(worldIndex);
            UUID worldId = world.getUID();
            ItemChunkCursor cursor = itemChunkCursors.computeIfAbsent(worldId, ignored -> new ItemChunkCursor());
            Long chunkKey = cursor.next(itemIndex, worldId, pass);
            worldIndex = (worldIndex + 1) % worlds.size();
            if (chunkKey == null) {
                emptyWorlds++;
                continue;
            }

            emptyWorlds = 0;
            int itemChunkX = HopperPositionIndex.unpackChunkX(chunkKey);
            int itemChunkZ = HopperPositionIndex.unpackChunkZ(chunkKey);
            for (int offsetX = -1; offsetX <= 1 && scheduledChunks < budget; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1 && scheduledChunks < budget; offsetZ++) {
                    int hopperChunkX = itemChunkX + offsetX;
                    int hopperChunkZ = itemChunkZ + offsetZ;
                    long hopperChunkKey = HopperPositionIndex.chunkKey(hopperChunkX, hopperChunkZ);
                    WorldChunkKey workKey = new WorldChunkKey(worldId, hopperChunkKey);
                    if (!scheduledThisTick.add(workKey)) {
                        continue;
                    }

                    long[] hoppers = positionIndex.hoppersInChunk(worldId, hopperChunkX, hopperChunkZ);
                    if (hoppers.length == 0) {
                        continue;
                    }

                    ItemChunkClaim claim = claimItemChunk(workKey);
                    if (claim == null) {
                        continue;
                    }

                    scheduledChunks++;
                    claimedHoppersByWorld.merge(worldId, (long) hoppers.length, Long::sum);
                    processClaimedItemChunk(
                        world,
                        worldId,
                        hopperChunkX,
                        hopperChunkZ,
                        hoppers,
                        itemIndex,
                        foliaThreading,
                        workKey,
                        claim);
                }
            }
        }
        nextWorld.set(worldIndex);
        return claimedHoppersByWorld;
    }

    private void finishWorldTick(
            List<World> worlds,
            HopperItemIndex itemIndex,
            HopperPositionIndex positionIndex,
            boolean foliaThreading,
            int spread,
            int stretchCooldown,
            boolean stretchEligible,
            Map<UUID, Long> claimedHoppersByWorld) {
        for (World world : worlds) {
            UUID worldId = world.getUID();
            long claimedHoppers = claimedHoppersByWorld.getOrDefault(worldId, 0L);
            long vanillaHoppers = Math.max(0L, (long) positionIndex.hopperCount(worldId) - claimedHoppers);
            if (vanillaHoppers > 0L) {
                FeatureHopperContainerThroughputMap.suckInItemsInvocations.addAndGet(vanillaHoppers);
            }
            if (stretchEligible) {
                stretchIdleBatch(
                    world,
                    worldId,
                    itemIndex,
                    positionIndex,
                    foliaThreading,
                    spread,
                    stretchCooldown);
            }
        }
    }

    private ItemChunkClaim claimItemChunk(WorldChunkKey key) {
        long generation = lifecycleGeneration.get();
        if (!active || generation != lifecycleGeneration.get()) {
            return null;
        }
        ItemChunkClaim offered = new ItemChunkClaim(generation);
        return itemChunksInFlight.putIfAbsent(key, offered) == null ? offered : null;
    }

    private void processClaimedItemChunk(
            World world,
            UUID worldId,
            int chunkX,
            int chunkZ,
            long[] hoppers,
            HopperItemIndex itemIndex,
            boolean foliaThreading,
            WorldChunkKey workKey,
            ItemChunkClaim claim) {
        if (!foliaThreading) {
            try {
                if (isCurrent(claim.generation)) {
                    processItemChunk(world, worldId, chunkX, chunkZ, hoppers, itemIndex, false);
                }
            } finally {
                itemChunksInFlight.remove(workKey, claim);
            }
            return;
        }

        Runnable task = () -> {
            try {
                if (isCurrentClaim(workKey, claim)) {
                    processItemChunk(world, worldId, chunkX, chunkZ, hoppers, itemIndex, true);
                }
            } finally {
                itemChunksInFlight.remove(workKey, claim);
            }
        };
        boolean scheduled = false;
        try {
            scheduled = J.runChunk(world, chunkX, chunkZ, task);
        } catch (Throwable throwable) {
            React.reportError(throwable);
        }
        if (!scheduled) {
            itemChunksInFlight.remove(workKey, claim);
            FeatureHopperContainerThroughputMap.suckInItemsInvocations.addAndGet(hoppers.length);
        }
    }

    private boolean isCurrent(long generation) {
        return active && generation == lifecycleGeneration.get();
    }

    private boolean isCurrentClaim(WorldChunkKey key, ItemChunkClaim claim) {
        return isCurrent(claim.generation) && itemChunksInFlight.get(key) == claim;
    }

    private void resetItemChunkState(HopperItemIndex source) {
        itemChunkCursors.clear();
        itemChunksInFlight.clear();
        nextWorld.set(0);
        itemChunkPass.set(0L);
        cursorSource = source;
    }

    private void processItemChunk(World world, UUID worldId, int chunkX, int chunkZ, long[] hoppers,
            HopperItemIndex itemIndex, boolean requireRegionOwnership) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            FeatureHopperContainerThroughputMap.suckInItemsInvocations.addAndGet(hoppers.length);
            return;
        }
        Object worldHandle;
        try {
            worldHandle = NMS.getWorldServer(world);
        } catch (Throwable throwable) {
            FeatureHopperContainerThroughputMap.suckInItemsInvocations.addAndGet(hoppers.length);
            return;
        }
        if (worldHandle == null) {
            FeatureHopperContainerThroughputMap.suckInItemsInvocations.addAndGet(hoppers.length);
            return;
        }

        UUID[] itemIds = adjacentItemIds(itemIndex, worldId, chunkX, chunkZ, hoppers);
        if (itemIds.length == 0) {
            FeatureHopperContainerThroughputMap.suckInItemsInvocations.addAndGet(hoppers.length);
            return;
        }
        Item[] items = new Item[itemIds.length];
        Location[] itemLocations = new Location[itemIds.length];
        Object[] itemHandles = new Object[itemIds.length];
        Long2ObjectOpenHashMap<IntArrayList> itemIndicesByHopper = createPickupCells(hoppers);
        int itemCount = 0;
        for (int i = 0; i < itemIds.length; i++) {
            try {
                Entity entity = Bukkit.getEntity(itemIds[i]);
                if (!(entity instanceof Item item)
                        || (requireRegionOwnership && !J.isOwnedByCurrentRegion(entity))
                        || !item.isValid()
                        || item.isDead()) {
                    continue;
                }
                Location itemLocation = item.getLocation();
                if (itemLocation == null) {
                    continue;
                }
                items[itemCount] = item;
                itemLocations[itemCount] = itemLocation;
                indexPickupCells(itemIndicesByHopper, itemLocation, itemCount);
                itemCount++;
            } catch (Throwable throwable) {
                itemIndex.removeItem(itemIds[i]);
                React.reportError(throwable);
            }
        }

        for (int i = 0; i < hoppers.length; i++) {
            long packed = hoppers[i];
            tryConsumeItemsAboveHopper(
                worldHandle,
                HopperPositionIndex.unpackX(packed),
                HopperPositionIndex.unpackY(packed),
                HopperPositionIndex.unpackZ(packed),
                items,
                itemLocations,
                itemHandles,
                itemIndicesByHopper.get(packed));
        }
    }

    private void tryConsumeItemsAboveHopper(Object worldHandle, int x, int y, int z, Item[] items,
            Location[] itemLocations, Object[] itemHandles, IntArrayList itemIndices) {
        try {
            Object blockPos = bridgeBlockPosCtor.methodHandle().invokeWithArguments(x, y, z);
            if (blockPos == null) {
                FeatureHopperContainerThroughputMap.suckInItemsInvocations.incrementAndGet();
                return;
            }
            Object blockEntity = bridgeGetBlockEntity.methodHandle().invokeWithArguments(worldHandle, blockPos);
            if (blockEntity == null) {
                FeatureHopperContainerThroughputMap.suckInItemsInvocations.incrementAndGet();
                return;
            }
            boolean anyConsumed = false;
            int candidateCount = itemIndices == null ? 0 : itemIndices.size();
            for (int candidate = 0; candidate < candidateCount; candidate++) {
                int i = itemIndices.getInt(candidate);
                Item item = items[i];
                if (!item.isValid() || item.isDead()) {
                    continue;
                }
                Location itemLocation = itemLocations[i];
                if (!isWithinPickupBounds(itemLocation.getX(), itemLocation.getY(), itemLocation.getZ(), x, y, z)) {
                    continue;
                }
                Object itemHandle = itemHandles[i];
                if (itemHandle == null) {
                    try {
                        itemHandle = NMS.getHandle(item);
                    } catch (Throwable throwable) {
                        itemHandle = UNAVAILABLE_ITEM_HANDLE;
                    }
                    if (itemHandle == null) {
                        itemHandle = UNAVAILABLE_ITEM_HANDLE;
                    }
                    itemHandles[i] = itemHandle;
                }
                if (itemHandle == UNAVAILABLE_ITEM_HANDLE) {
                    continue;
                }
                Boolean consumed = (Boolean) bridgeAddItem.methodHandle()
                    .invokeWithArguments(blockEntity, itemHandle);
                if (Boolean.TRUE.equals(consumed)) {
                    anyConsumed = true;
                }
            }
            if (anyConsumed) {
                bridgeCooldownTime.varHandle().set(blockEntity, HOPPER_COOLDOWN_TICKS);
            } else {
                FeatureHopperContainerThroughputMap.suckInItemsInvocations.incrementAndGet();
            }
        } catch (Throwable t) {
            FeatureHopperContainerThroughputMap.suckInItemsInvocations.incrementAndGet();
        }
    }

    private Long2ObjectOpenHashMap<IntArrayList> createPickupCells(long[] hoppers) {
        Long2ObjectOpenHashMap<IntArrayList> cells = new Long2ObjectOpenHashMap<>(hoppers.length);
        for (int i = 0; i < hoppers.length; i++) {
            cells.put(hoppers[i], null);
        }
        return cells;
    }

    private void indexPickupCells(
            Long2ObjectOpenHashMap<IntArrayList> cells,
            Location itemLocation,
            int itemIndex) {
        double itemX = itemLocation.getX();
        double itemY = itemLocation.getY();
        double itemZ = itemLocation.getZ();
        if (!Double.isFinite(itemX) || !Double.isFinite(itemY) || !Double.isFinite(itemZ)) {
            return;
        }

        int minimumX = (int) Math.ceil(itemX - 0.5D - COLLECTION_HALF_EXTENT_H);
        int maximumX = (int) Math.floor(itemX - 0.5D + COLLECTION_HALF_EXTENT_H);
        int minimumY = (int) Math.ceil(itemY - COLLECTION_Y_MAX_OFFSET);
        int maximumY = (int) Math.floor(itemY - COLLECTION_Y_MIN_OFFSET);
        int minimumZ = (int) Math.ceil(itemZ - 0.5D - COLLECTION_HALF_EXTENT_H);
        int maximumZ = (int) Math.floor(itemZ - 0.5D + COLLECTION_HALF_EXTENT_H);
        for (long x = minimumX; x <= maximumX; x++) {
            for (long y = minimumY; y <= maximumY; y++) {
                for (long z = minimumZ; z <= maximumZ; z++) {
                    addPickupCell(cells, HopperPositionIndex.packPos((int) x, (int) y, (int) z), itemIndex);
                }
            }
        }
    }

    private void addPickupCell(
            Long2ObjectOpenHashMap<IntArrayList> cells,
            long hopperPosition,
            int itemIndex) {
        if (!cells.containsKey(hopperPosition)) {
            return;
        }
        IntArrayList indices = cells.get(hopperPosition);
        if (indices == null) {
            indices = new IntArrayList();
            cells.put(hopperPosition, indices);
        }
        indices.add(itemIndex);
    }

    static boolean isWithinPickupBounds(double itemX, double itemY, double itemZ,
            int hopperX, int hopperY, int hopperZ) {
        double centerX = hopperX + 0.5;
        double centerZ = hopperZ + 0.5;
        return Math.abs(itemX - centerX) <= COLLECTION_HALF_EXTENT_H
            && itemY >= hopperY + COLLECTION_Y_MIN_OFFSET
            && itemY <= hopperY + COLLECTION_Y_MAX_OFFSET
            && Math.abs(itemZ - centerZ) <= COLLECTION_HALF_EXTENT_H;
    }

    static int effectiveIdleStretchCooldown(int configuredCooldown, int spreadPasses) {
        if (configuredCooldown <= 0 || spreadPasses <= 1) {
            return 0;
        }
        return Math.min(configuredCooldown, spreadPasses - 1);
    }

    private UUID[] adjacentItemIds(
            HopperItemIndex itemIndex,
            UUID worldId,
            int chunkX,
            int chunkZ,
            long[] hoppers) {
        boolean west = false;
        boolean east = false;
        boolean north = false;
        boolean south = false;
        for (int i = 0; i < hoppers.length; i++) {
            int localX = HopperPositionIndex.unpackX(hoppers[i]) & 15;
            int localZ = HopperPositionIndex.unpackZ(hoppers[i]) & 15;
            west |= localX == 0;
            east |= localX == 15;
            north |= localZ == 0;
            south |= localZ == 15;
        }

        int minimumX = west ? -1 : 0;
        int maximumX = east ? 1 : 0;
        int minimumZ = north ? -1 : 0;
        int maximumZ = south ? 1 : 0;
        List<UUID[]> buckets = new ArrayList<>((maximumX - minimumX + 1) * (maximumZ - minimumZ + 1));
        int total = 0;
        for (int offsetX = minimumX; offsetX <= maximumX; offsetX++) {
            for (int offsetZ = minimumZ; offsetZ <= maximumZ; offsetZ++) {
                UUID[] bucket = itemIndex.itemIdsInChunk(worldId, chunkX + offsetX, chunkZ + offsetZ);
                if (bucket.length > 0) {
                    buckets.add(bucket);
                    total += bucket.length;
                }
            }
        }
        if (total == 0) {
            return new UUID[0];
        }

        UUID[] combined = new UUID[total];
        int destination = 0;
        for (UUID[] bucket : buckets) {
            System.arraycopy(bucket, 0, combined, destination, bucket.length);
            destination += bucket.length;
        }
        return combined;
    }

    private void stretchIdleBatch(World world, UUID worldId, HopperItemIndex itemIndex,
            HopperPositionIndex positionIndex, boolean foliaThreading, int spread, int stretchCooldown) {
        long[] candidates = positionIndex.nextHopperBatch(
            worldId,
            spread,
            MAX_IDLE_STRETCH_PROBES_PER_TICK);
        if (candidates.length == 0) {
            return;
        }
        Long2ObjectOpenHashMap<LongArrayList> positionsByChunk = new Long2ObjectOpenHashMap<>();
        for (int i = 0; i < candidates.length; i++) {
            long packed = candidates[i];
            int x = HopperPositionIndex.unpackX(packed);
            int z = HopperPositionIndex.unpackZ(packed);
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (hasIndexedItemsNearHopper(itemIndex, worldId, x, z)) {
                continue;
            }
            long chunkKey = HopperPositionIndex.chunkKey(chunkX, chunkZ);
            LongArrayList positions = positionsByChunk.get(chunkKey);
            if (positions == null) {
                positions = new LongArrayList();
                positionsByChunk.put(chunkKey, positions);
            }
            positions.add(packed);
        }

        long[] chunkKeys = positionsByChunk.keySet().toLongArray();
        for (int i = 0; i < chunkKeys.length; i++) {
            long chunkKey = chunkKeys[i];
            int chunkX = HopperPositionIndex.unpackChunkX(chunkKey);
            int chunkZ = HopperPositionIndex.unpackChunkZ(chunkKey);
            LongArrayList positions = positionsByChunk.get(chunkKey);
            if (!foliaThreading) {
                stretchIdleHoppers(world, chunkX, chunkZ, positions, stretchCooldown);
                continue;
            }
            Runnable task = () -> stretchIdleHoppers(world, chunkX, chunkZ, positions, stretchCooldown);
            J.runChunk(world, chunkX, chunkZ, task);
        }
    }

    private boolean hasIndexedItemsNearHopper(
            HopperItemIndex itemIndex,
            UUID worldId,
            int hopperX,
            int hopperZ) {
        int chunkX = hopperX >> 4;
        int chunkZ = hopperZ >> 4;
        int localX = hopperX & 15;
        int localZ = hopperZ & 15;
        int minimumX = localX == 0 ? -1 : 0;
        int maximumX = localX == 15 ? 1 : 0;
        int minimumZ = localZ == 0 ? -1 : 0;
        int maximumZ = localZ == 15 ? 1 : 0;
        for (int offsetX = minimumX; offsetX <= maximumX; offsetX++) {
            for (int offsetZ = minimumZ; offsetZ <= maximumZ; offsetZ++) {
                if (itemIndex.hasItemsAbove(worldId, chunkX + offsetX, chunkZ + offsetZ)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void stretchIdleHoppers(
            World world,
            int chunkX,
            int chunkZ,
            LongArrayList positions,
            int stretchCooldown) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        Object worldHandle;
        try {
            worldHandle = NMS.getWorldServer(world);
        } catch (Throwable throwable) {
            reportIdleStretchFailure(throwable);
            return;
        }
        if (worldHandle == null) {
            return;
        }
        for (int i = 0; i < positions.size(); i++) {
            long packed = positions.getLong(i);
            int x = HopperPositionIndex.unpackX(packed);
            int y = HopperPositionIndex.unpackY(packed);
            int z = HopperPositionIndex.unpackZ(packed);
            try {
                Object blockPos = bridgeBlockPosCtor.methodHandle().invokeWithArguments(x, y, z);
                Object blockEntity = bridgeGetBlockEntity.methodHandle().invokeWithArguments(worldHandle, blockPos);
                if (blockEntity == null) {
                    continue;
                }
                Boolean empty = (Boolean) bridgeIsEmpty.methodHandle().invokeWithArguments(blockEntity);
                if (!Boolean.TRUE.equals(empty)) {
                    continue;
                }
                int cooldown = (int) bridgeCooldownTime.varHandle().get(blockEntity);
                if (cooldown < stretchCooldown) {
                    bridgeCooldownTime.varHandle().set(blockEntity, stretchCooldown);
                }
            } catch (Throwable throwable) {
                reportIdleStretchFailure(throwable);
            }
        }
    }

    private void reportIdleStretchFailure(Throwable throwable) {
        if (!idleStretchFailureReported.compareAndSet(false, true)) {
            return;
        }
        React.reportError("Hopper index idle-cooldown inspection failed; affected hoppers will keep vanilla ticking.", throwable);
    }

    private static final class ItemChunkCursor {
        private long[] chunks = new long[0];
        private int next;
        private long exhaustedPass = Long.MIN_VALUE;

        private Long next(HopperItemIndex itemIndex, UUID worldId, long pass) {
            if (next >= chunks.length) {
                if (exhaustedPass == pass) {
                    return null;
                }
                chunks = itemIndex.itemChunkKeys(worldId);
                next = 0;
            }
            if (chunks.length == 0) {
                exhaustedPass = pass;
                return null;
            }
            long chunk = chunks[next++];
            if (next >= chunks.length) {
                exhaustedPass = pass;
            }
            return chunk;
        }
    }

    private record WorldChunkKey(UUID worldId, long chunkKey) {
    }

    private record ItemChunkClaim(long generation) {
    }
}
