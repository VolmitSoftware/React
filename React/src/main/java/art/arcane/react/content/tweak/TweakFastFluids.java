/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.content.tweak;

import art.arcane.react.React;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.monitor.NativeWorldAccess;
import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;

import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Fast Fluids tweak. Fast-forwards water and lava spread chains into bounded burst updates to reduce repeated per-step fluid churn.")
public class TweakFastFluids extends ReactTweak implements Listener {
  public static final String ID = "fast-fluids";
  private static final BlockFace[] DRAIN_NEIGHBORS = new BlockFace[]{
      BlockFace.DOWN,
      BlockFace.UP,
      BlockFace.NORTH,
      BlockFace.EAST,
      BlockFace.SOUTH,
      BlockFace.WEST
  };
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast fluids applies water acceleration.", impact = "Enable to accelerate water flow chains; disable to leave water at vanilla timing.")
  private boolean accelerateWater = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast fluids applies lava acceleration.", impact = "Enable to accelerate lava flow chains; disable to leave lava at vanilla timing.")
  private boolean accelerateLava = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Additional vanilla fluid ticks queued per fluid flow event in fast fluids.", impact = "Higher values compress more fluid updates into fewer server ticks but can increase per-tick fluid work.")
  private int extraVanillaTicksPerEvent = 2;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum extra vanilla fluid ticks allowed per server tick in fast fluids.", impact = "Higher values allow stronger acceleration bursts; lower values cap fluid burst cost more aggressively.")
  private int maxExtraVanillaTicksPerServerTick = 256;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum queued extra fluid ticks consumed for one block location during a single server tick flush.", impact = "Higher values collapse fluid chains faster into one tick for each location; lower values spread work more evenly across ticks.")
  private int maxBurstTicksPerLocationPerServerTick = 16;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast fluids applies draining acceleration around active flow events.", impact = "Enable to accelerate fluid retract and empty behavior near flow updates; disable to accelerate only direct flow ticks.")
  private boolean accelerateDrain = true;
  private transient Map<FluidPulseKey, FluidPulse> pendingPulses;
  private transient Queue<FluidPulseKey> pulseOrder;
  private transient NativeWorldAccess nativeAccess;
  private transient boolean fluidBridgesAvailable;
  private transient BridgeFailureGate bridgeFailureGate;
  private transient int pulseTaskId;

  public TweakFastFluids() {
    super(ID);
  }

  public boolean isAccelerateWater() {
    return accelerateWater;
  }

  public boolean isAccelerateLava() {
    return accelerateLava;
  }

  @Override
  public void onActivate() {
    extraVanillaTicksPerEvent = clampInt(extraVanillaTicksPerEvent, 0, 4);
    maxExtraVanillaTicksPerServerTick = clampInt(maxExtraVanillaTicksPerServerTick, 16, 4096);
    maxBurstTicksPerLocationPerServerTick = clampInt(maxBurstTicksPerLocationPerServerTick, 1, 16);
    pendingPulses = new ConcurrentHashMap<>();
    pulseOrder = new ConcurrentLinkedQueue<>();
    bridgeFailureGate = new BridgeFailureGate(
        clampInt(Integer.getInteger("react.fastfluids.bridgeFailureThreshold", 8), 1, 64));
    nativeAccess = NativeAdapters.find(NativeWorldAccess.class).orElse(null);
    fluidBridgesAvailable = nativeAccess != null;
    if (!fluidBridgesAvailable) {
      React.warn("Fast Fluids acceleration is passive: no usable fluid bridge resolved");
    }
    pulseTaskId = J.sr(this::flushPulseQueue, 1);
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void on(BlockFromToEvent event) {
    if (extraVanillaTicksPerEvent <= 0) {
      return;
    }

    if (!fluidBridgesAvailable) {
      return;
    }

    Block sourceBlock = event.getBlock();
    Block targetBlock = event.getToBlock();
    Material sourceMaterial = sourceBlock.getType();
    Material targetMaterial = targetBlock.getType();
    if (!isSupportedFluid(sourceMaterial) && !isSupportedFluid(targetMaterial)) {
      return;
    }

    int extraTicks = clampInt(extraVanillaTicksPerEvent, 0, 4);
    enqueueForTicks(sourceBlock, extraTicks);
    enqueueForTicks(targetBlock, extraTicks);

    if (accelerateDrain) {
      enqueueNeighbors(sourceBlock, extraTicks);
      if (!targetBlock.equals(sourceBlock)) {
        enqueueNeighbors(targetBlock, extraTicks);
      }
    }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void on(FluidLevelChangeEvent event) {
    if (extraVanillaTicksPerEvent <= 0) {
      return;
    }

    if (!fluidBridgesAvailable) {
      return;
    }

    Block block = event.getBlock();
    Material currentMaterial = block.getType();
    BlockData currentData = block.getBlockData();
    BlockData newData = event.getNewData();
    Material newMaterial = newData == null ? currentMaterial : newData.getMaterial();
    if (!isSupportedFluid(currentMaterial) && !isSupportedFluid(newMaterial)) {
      return;
    }

    int extraTicks = clampInt(extraVanillaTicksPerEvent, 0, 4);
    enqueueForTicks(block, extraTicks);
    if (accelerateDrain && isLikelyDrainTransition(currentMaterial, currentData, newMaterial, newData)) {
      enqueueNeighbors(block, extraTicks);
    }
  }

  @Override
  public void onDeactivate() {
    if (pulseTaskId != 0) {
      J.csr(pulseTaskId);
      pulseTaskId = 0;
    }

    if (pendingPulses != null) {
      pendingPulses.clear();
    }
    if (pulseOrder != null) {
      pulseOrder.clear();
    }
    fluidBridgesAvailable = false;
    bridgeFailureGate = null;
  }

  private void flushPulseQueue() {
    if (!fluidBridgesAvailable) {
      return;
    }

    if (pendingPulses == null || pendingPulses.isEmpty()) {
      return;
    }
    int budget = clampInt(maxExtraVanillaTicksPerServerTick, 16, 4096);
    int maxBurst = clampInt(maxBurstTicksPerLocationPerServerTick, 1, 16);
    int scanLimit = Math.max(budget * 8, 128);
    int scanned = 0;
    while (budget > 0 && scanned < scanLimit) {
      FluidPulseKey key = pulseOrder.poll();
      if (key == null) {
        return;
      }

      scanned++;
      FluidPulse pulse = pendingPulses.get(key);
      if (pulse == null) {
        continue;
      }

      int burstTicks = pulse.consumeUpTo(Math.min(maxBurst, budget));
      if (burstTicks <= 0) {
        pendingPulses.remove(key, pulse);
        continue;
      }

      budget -= burstTicks;
      schedulePulse(key, pulse, burstTicks);

      if (pulse.hasRemaining()) {
        pulseOrder.offer(key);
      } else {
        pendingPulses.remove(key, pulse);
      }
    }
  }

  private void schedulePulse(FluidPulseKey key, FluidPulse pulse, int burstTicks) {
    World world = Bukkit.getWorld(pulse.getWorldId());
    if (world == null) {
      pendingPulses.remove(key, pulse);
      return;
    }

    Location location = new Location(world, pulse.getX(), pulse.getY(), pulse.getZ());
    J.s(location, () -> runPulse(key, pulse, burstTicks), 0);
  }

  private void runPulse(FluidPulseKey key, FluidPulse pulse, int burstTicks) {
    if (!fluidBridgesAvailable) {
      return;
    }

    World world = Bukkit.getWorld(pulse.getWorldId());
    if (world == null) {
      pendingPulses.remove(key, pulse);
      return;
    }

    if (!accelerateWater && !accelerateLava) {
      resetBridgeFailures();
      return;
    }

    int x = pulse.getX();
    int y = pulse.getY();
    int z = pulse.getZ();
    if (!isChunkNeighborhoodReady(world, x, z)) {
      resetBridgeFailures();
      return;
    }

    try {
      for (int i = 0; i < Math.max(1, burstTicks); i++) {
        if (!nativeAccess.tickFluid(world, x, y, z, accelerateWater, accelerateLava)) {
          break;
        }
      }
    } catch (Throwable failure) {
      reportBridgeThrowable(failure);
      recordBridgeFailure();
      return;
    }

    resetBridgeFailures();
  }

  private void resetBridgeFailures() {
    BridgeFailureGate gate = bridgeFailureGate;
    if (gate != null) {
      gate.reset();
    }
  }

  private void recordBridgeFailure() {
    BridgeFailureGate gate = bridgeFailureGate;
    if (gate == null || !gate.incrementAndCheckThreshold()) {
      return;
    }

    fluidBridgesAvailable = false;
    if (gate.isWarned()) {
      return;
    }

    gate.markWarned();
    React.warn("Fast Fluids acceleration is passive: consecutive runtime bridge failures exceeded threshold");
  }

  private void reportBridgeThrowable(Throwable throwable) {
    BridgeFailureGate gate = bridgeFailureGate;
    if (gate == null || !gate.shouldReport(throwable)) {
      return;
    }

    React.reportError("Fast Fluids runtime bridge failure on the fluid tick path: "
        + throwable.getClass().getName()
        + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage()), throwable);
  }

  private void enqueueForTicks(Block block, int extraTicks) {
    if (block == null || block.getWorld() == null || extraTicks <= 0) {
      return;
    }

    enqueueForTicks(block.getWorld(), block.getX(), block.getY(), block.getZ(), extraTicks);
  }

  private void enqueueForTicks(World world, int x, int y, int z, int extraTicks) {
    if (world == null || extraTicks <= 0) {
      return;
    }

    if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
      return;
    }

    int chunkX = x >> 4;
    int chunkZ = z >> 4;
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      return;
    }

    FluidPulseKey key = FluidPulseKey.of(world.getUID(), x, y, z);
    pendingPulses.compute(key, (ignored, existing) -> {
      if (existing == null) {
        FluidPulse created = new FluidPulse(key.getWorldId(), key.getX(), key.getY(), key.getZ(), extraTicks);
        pulseOrder.offer(key);
        return created;
      }

      existing.addTicks(extraTicks);
      return existing;
    });
  }

  private void enqueueNeighbors(Block block, int extraTicks) {
    if (block == null || block.getWorld() == null || extraTicks <= 0) {
      return;
    }

    World world = block.getWorld();
    int minHeight = world.getMinHeight();
    int maxHeight = world.getMaxHeight();
    int originX = block.getX();
    int originY = block.getY();
    int originZ = block.getZ();
    for (BlockFace face : DRAIN_NEIGHBORS) {
      int x = originX + face.getModX();
      int y = originY + face.getModY();
      int z = originZ + face.getModZ();
      if (y < minHeight || y >= maxHeight) {
        continue;
      }
      if (!isChunkNeighborhoodReady(world, x, z)) {
        continue;
      }
      enqueueForTicks(world, x, y, z, extraTicks);
    }
  }

  private boolean isSupportedFluid(Material material) {
    if (material == Material.WATER) {
      return accelerateWater;
    }

    if (material == Material.LAVA) {
      return accelerateLava;
    }

    return false;
  }

  private boolean isLikelyDrainTransition(Material currentMaterial, BlockData currentData, Material newMaterial, BlockData newData) {
    if (!isSupportedFluid(currentMaterial)) {
      return false;
    }

    if (!isSupportedFluid(newMaterial)) {
      return true;
    }

    if (newData == null || currentData == null) {
      return false;
    }

    if (!(currentData instanceof Levelled) || !(newData instanceof Levelled)) {
      return false;
    }

    Levelled currentLevelled = (Levelled) currentData;
    Levelled newLevelled = (Levelled) newData;
    return newLevelled.getLevel() > currentLevelled.getLevel();
  }

  private boolean isChunkNeighborhoodReady(World world, int x, int z) {
    if (world == null) {
      return false;
    }

    int chunkX = x >> 4;
    int chunkZ = z >> 4;
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      return false;
    }

    int localX = x & 15;
    int localZ = z & 15;
    if (localX == 0 && !world.isChunkLoaded(chunkX - 1, chunkZ)) {
      return false;
    }
    if (localX == 15 && !world.isChunkLoaded(chunkX + 1, chunkZ)) {
      return false;
    }
    if (localZ == 0 && !world.isChunkLoaded(chunkX, chunkZ - 1)) {
      return false;
    }
    if (localZ == 15 && !world.isChunkLoaded(chunkX, chunkZ + 1)) {
      return false;
    }
    if (localX == 0 && localZ == 0 && !world.isChunkLoaded(chunkX - 1, chunkZ - 1)) {
      return false;
    }
    if (localX == 0 && localZ == 15 && !world.isChunkLoaded(chunkX - 1, chunkZ + 1)) {
      return false;
    }
    if (localX == 15 && localZ == 0 && !world.isChunkLoaded(chunkX + 1, chunkZ - 1)) {
      return false;
    }
    if (localX == 15 && localZ == 15 && !world.isChunkLoaded(chunkX + 1, chunkZ + 1)) {
      return false;
    }

    return true;
  }

  private int clampInt(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static final class BridgeFailureGate {
    private static final int MAX_REPORTED_FAILURE_KINDS = 4;

    private final Set<String> reportedFailureKinds;
    private final AtomicInteger count;
    private final int threshold;
    private volatile boolean warned;

    BridgeFailureGate(int threshold) {
      this.reportedFailureKinds = ConcurrentHashMap.newKeySet();
      this.warned = false;
      this.count = new AtomicInteger(0);
      this.threshold = threshold;
    }

    void reset() {
      count.set(0);
    }

    boolean incrementAndCheckThreshold() {
      return count.incrementAndGet() >= Math.max(1, threshold);
    }

    boolean shouldReport(Throwable throwable) {
      if (reportedFailureKinds.size() >= MAX_REPORTED_FAILURE_KINDS) {
        return false;
      }

      return reportedFailureKinds.add(throwable.getClass().getName());
    }

    boolean isWarned() {
      return warned;
    }

    void markWarned() {
      warned = true;
    }
  }

  private static final class FluidPulseKey {
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;

    private FluidPulseKey(UUID worldId, int x, int y, int z) {
      this.worldId = worldId;
      this.x = x;
      this.y = y;
      this.z = z;
    }

    private static FluidPulseKey of(UUID worldId, int x, int y, int z) {
      return new FluidPulseKey(worldId, x, y, z);
    }

    private UUID getWorldId() {
      return worldId;
    }

    private int getX() {
      return x;
    }

    private int getY() {
      return y;
    }

    private int getZ() {
      return z;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (!(object instanceof FluidPulseKey)) {
        return false;
      }
      FluidPulseKey other = (FluidPulseKey) object;

      return x == other.x
          && y == other.y
          && z == other.z
          && Objects.equals(worldId, other.worldId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(worldId, x, y, z);
    }
  }

  private static final class FluidPulse {
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final AtomicInteger remainingTicks;

    private FluidPulse(UUID worldId, int x, int y, int z, int ticks) {
      this.worldId = worldId;
      this.x = x;
      this.y = y;
      this.z = z;
      this.remainingTicks = new AtomicInteger(Math.max(0, ticks));
    }

    private UUID getWorldId() {
      return worldId;
    }

    private int getX() {
      return x;
    }

    private int getY() {
      return y;
    }

    private int getZ() {
      return z;
    }

    private void addTicks(int ticks) {
      int safeTicks = Math.max(0, ticks);
      if (safeTicks == 0) {
        return;
      }

      remainingTicks.updateAndGet(value -> clamp(value + safeTicks, 0, 16));
    }

    private int consumeUpTo(int maxTicks) {
      int safeMaxTicks = Math.max(0, maxTicks);
      if (safeMaxTicks <= 0) {
        return 0;
      }

      while (true) {
        int current = remainingTicks.get();
        if (current <= 0) {
          return 0;
        }

        int consume = Math.min(current, safeMaxTicks);
        if (remainingTicks.compareAndSet(current, current - consume)) {
          return consume;
        }
      }
    }

    private boolean hasRemaining() {
      return remainingTicks.get() > 0;
    }

    private int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
    }
  }
}
