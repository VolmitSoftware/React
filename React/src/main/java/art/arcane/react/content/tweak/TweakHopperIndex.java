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
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    @art.arcane.react.util.project.config.ConfigDoc(value = "Stretches the transfer cooldown of empty, idle hoppers while the server is under load so vanilla skips re-polling them.", impact = "Enable to shed idle hopper tick cost on hopper-heavy servers; item pickup through the index fast-path stays instant.")
    private boolean idleStretch = true;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Cooldown in ticks applied to empty idle hoppers; bounds the worst-case delay before an idle hopper resumes pulling from a container.", impact = "Higher values skip more idle hopper work but delay transfer resumption longer.")
    private int idleStretchTicks = 40;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Number of tick passes each idle hopper probe is spread across.", impact = "Higher values probe each hopper less often (cheaper, slower to stretch); lower values stretch idle hoppers sooner.")
    private int idleStretchSpreadPasses = 40;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds required before idle hopper stretching engages.", impact = "Lower values stretch idle hoppers earlier; higher values reserve it for heavier load.")
    private double idleStretchMinTickMs = 45;

    private transient NmsBridgeHandle bridgeAddItem;
    private transient NmsBridgeHandle bridgeCooldownTime;
    private transient NmsBridgeHandle bridgeGetBlockEntity;
    private transient NmsBridgeHandle bridgeBlockPosCtor;
    private transient NmsBridgeHandle bridgeIsEmpty;
    private transient boolean bridgesAvailable;
    private transient int tickTaskId;
    private transient int stretchPassCounter;
    private transient FeatureHopperItemIndex indexFeature;

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
        List<NmsBridgeDescriptor> descriptors = hopperBridgeDescriptors();
        bridgeAddItem = React.bridgeRegistry().resolve(descriptors.get(0));
        bridgeCooldownTime = React.bridgeRegistry().resolve(descriptors.get(1));
        bridgeGetBlockEntity = React.bridgeRegistry().resolve(descriptors.get(2));
        bridgeBlockPosCtor = React.bridgeRegistry().resolve(descriptors.get(3));
        bridgeIsEmpty = React.bridgeRegistry().resolve(descriptors.get(4));
        stretchPassCounter = 0;
        bridgesAvailable = checkBridgesAvailable();
        if (!bridgesAvailable) {
            logUnavailableBridge();
            return;
        }
        tickTaskId = J.sr(this::tickAllWorlds, 1);
    }

    @Override
    public void onDeactivate() {
        if (tickTaskId != 0) {
            J.csr(tickTaskId);
            tickTaskId = 0;
        }
        bridgesAvailable = false;
        indexFeature = null;
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

    private void tickAllWorlds() {
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
        stretchPassCounter++;
        int spread = Math.max(1, idleStretchSpreadPasses);
        int stretchSlot = stretchPassCounter % spread;
        boolean stretchEligible = idleStretch
            && idleStretchTicks > HOPPER_COOLDOWN_TICKS
            && bridgeIsEmpty.available()
            && sample(SamplerTickTime.ID) >= idleStretchMinTickMs;
        for (World world : Bukkit.getWorlds()) {
            UUID worldId = world.getUID();
            positionIndex.forEachHopperInWorld(worldId, packed -> {
                int x = HopperPositionIndex.unpackX(packed);
                int y = HopperPositionIndex.unpackY(packed);
                int z = HopperPositionIndex.unpackZ(packed);
                int cx = x >> 4;
                int cz = z >> 4;
                if (!itemIndex.hasItemsAbove(worldId, cx, cz)) {
                    FeatureHopperContainerThroughputMap.suckInItemsInvocations.incrementAndGet();
                    if (stretchEligible && Math.floorMod((int) (packed ^ (packed >>> 32)), spread) == stretchSlot) {
                        if (!J.isFoliaThreading()) {
                            stretchIdleHopper(world, x, y, z);
                        } else {
                            J.runChunk(world, cx, cz, () -> stretchIdleHopper(world, x, y, z));
                        }
                    }
                    return;
                }
                if (!J.isFoliaThreading()) {
                    tryConsumeItemsAboveHopper(world, worldId, x, y, z, itemIndex);
                } else {
                    J.runChunk(world, cx, cz, () -> tryConsumeItemsAboveHopper(world, worldId, x, y, z, itemIndex));
                }
            });
        }
    }

    private void stretchIdleHopper(World world, int x, int y, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return;
        }
        try {
            Object worldHandle = NMS.getWorldServer(world);
            if (worldHandle == null) {
                return;
            }
            Object blockPos = bridgeBlockPosCtor.methodHandle().invokeWithArguments(x, y, z);
            Object blockEntity = bridgeGetBlockEntity.methodHandle().invokeWithArguments(worldHandle, blockPos);
            if (blockEntity == null) {
                return;
            }
            Boolean empty = (Boolean) bridgeIsEmpty.methodHandle().invokeWithArguments(blockEntity);
            if (!Boolean.TRUE.equals(empty)) {
                return;
            }
            int cooldown = (int) bridgeCooldownTime.varHandle().get(blockEntity);
            if (cooldown < idleStretchTicks) {
                bridgeCooldownTime.varHandle().set(blockEntity, idleStretchTicks);
            }
        } catch (Throwable ignored) {
            // Fail open: a hopper we cannot inspect simply keeps vanilla ticking.
        }
    }

    private void tryConsumeItemsAboveHopper(World world, UUID worldId, int x, int y, int z,
            HopperItemIndex itemIndex) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            FeatureHopperContainerThroughputMap.suckInItemsInvocations.incrementAndGet();
            return;
        }
        try {
            Object worldHandle = NMS.getWorldServer(world);
            if (worldHandle == null) {
                FeatureHopperContainerThroughputMap.suckInItemsInvocations.incrementAndGet();
                return;
            }
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
            Set<UUID> itemIds = itemIndex.itemsInChunk(worldId, x >> 4, z >> 4);
            double centerX = x + 0.5;
            double centerZ = z + 0.5;
            boolean anyConsumed = false;
            for (UUID itemId : itemIds) {
                Entity entity = Bukkit.getEntity(itemId);
                if (!(entity instanceof Item item) || !item.isValid() || item.isDead()) {
                    continue;
                }
                org.bukkit.Location itemLoc = item.getLocation();
                double ix = itemLoc.getX();
                double iy = itemLoc.getY();
                double iz = itemLoc.getZ();
                if (Math.abs(ix - centerX) > COLLECTION_HALF_EXTENT_H
                        || iy < y + COLLECTION_Y_MIN_OFFSET
                        || iy > y + COLLECTION_Y_MAX_OFFSET
                        || Math.abs(iz - centerZ) > COLLECTION_HALF_EXTENT_H) {
                    continue;
                }
                Object nmsItemEntity = getNmsHandle(entity);
                if (nmsItemEntity == null) {
                    continue;
                }
                Boolean consumed = (Boolean) bridgeAddItem.methodHandle()
                    .invokeWithArguments(blockEntity, nmsItemEntity);
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

    private Object getNmsHandle(Entity entity) {
        try {
            return entity.getClass().getMethod("getHandle").invoke(entity);
        } catch (Throwable t) {
            return null;
        }
    }
}
