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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
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
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast fluids applies draining acceleration around active flow events.", impact = "Enable to accelerate fluid retract and empty behavior near flow updates; disable to accelerate only direct flow ticks.")
  private boolean accelerateDrain = true;
  private transient Map<FluidPulseKey, FluidPulse> pendingPulses;
  private transient Queue<FluidPulseKey> pulseOrder;
  private transient Set<FluidPulseKey> processedThisTick;
  private transient VanillaFluidTickBridge fluidTickBridge;
  private transient int pulseTaskId;
  private transient boolean runtimeBridgeFailureWarned;
  private transient AtomicInteger bridgeFailureCount;
  private transient int bridgeFailureThreshold;

  public TweakFastFluids() {
    super(ID);
  }

  @Override
  public void onActivate() {
    extraVanillaTicksPerEvent = clampInt(extraVanillaTicksPerEvent, 0, 4);
    maxExtraVanillaTicksPerServerTick = clampInt(maxExtraVanillaTicksPerServerTick, 16, 4096);
    pendingPulses = new ConcurrentHashMap<>();
    pulseOrder = new ConcurrentLinkedQueue<>();
    processedThisTick = ConcurrentHashMap.newKeySet();
    runtimeBridgeFailureWarned = false;
    bridgeFailureCount = new AtomicInteger(0);
    bridgeFailureThreshold = clampInt(Integer.getInteger("react.fastfluids.bridgeFailureThreshold", 8), 1, 64);
    fluidTickBridge = VanillaFluidTickBridge.resolve();
    if (!fluidTickBridge.isAvailable()) {
      React.warn("Fast Fluids acceleration is passive: " + fluidTickBridge.getFailureReason()
          + " | " + fluidTickBridge.getResolutionSummary());
    }

    pulseTaskId = J.sr(this::flushPulseQueue, 1);
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void on(BlockFromToEvent event) {
    if (extraVanillaTicksPerEvent <= 0) {
      return;
    }

    if (fluidTickBridge == null || !fluidTickBridge.isAvailable()) {
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

    if (fluidTickBridge == null || !fluidTickBridge.isAvailable()) {
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
    if (processedThisTick != null) {
      processedThisTick.clear();
    }
    if (bridgeFailureCount != null) {
      bridgeFailureCount.set(0);
    }
    bridgeFailureThreshold = 0;

    fluidTickBridge = VanillaFluidTickBridge.unavailable("Fast Fluids bridge deactivated.");
  }

  @Override
  public int getTickInterval() {
    return -1;
  }

  @Override
  public void onTick() {

  }

  private void flushPulseQueue() {
    if (fluidTickBridge == null || !fluidTickBridge.isAvailable()) {
      return;
    }

    if (pendingPulses == null || pendingPulses.isEmpty()) {
      return;
    }

    if (processedThisTick == null) {
      return;
    }

    processedThisTick.clear();
    int budget = clampInt(maxExtraVanillaTicksPerServerTick, 16, 4096);
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

      if (!processedThisTick.add(key)) {
        pulseOrder.offer(key);
        continue;
      }

      if (!pulse.consumeOne()) {
        pendingPulses.remove(key, pulse);
        continue;
      }

      budget--;
      schedulePulse(key, pulse);

      if (pulse.hasRemaining()) {
        pulseOrder.offer(key);
      } else {
        pendingPulses.remove(key, pulse);
      }
    }
  }

  private void schedulePulse(FluidPulseKey key, FluidPulse pulse) {
    World world = Bukkit.getWorld(pulse.getWorldId());
    if (world == null) {
      pendingPulses.remove(key, pulse);
      return;
    }

    Location location = new Location(world, pulse.getX(), pulse.getY(), pulse.getZ());
    J.s(location, () -> runPulse(key, pulse), 0);
  }

  private void runPulse(FluidPulseKey key, FluidPulse pulse) {
    if (fluidTickBridge == null || !fluidTickBridge.isAvailable()) {
      return;
    }

    World world = Bukkit.getWorld(pulse.getWorldId());
    if (world == null) {
      pendingPulses.remove(key, pulse);
      return;
    }

    int chunkX = pulse.getX() >> 4;
    int chunkZ = pulse.getZ() >> 4;
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      return;
    }

    Block block = world.getBlockAt(pulse.getX(), pulse.getY(), pulse.getZ());
    TickResult result = fluidTickBridge.tickVanillaFluid(block, accelerateWater, accelerateLava);
    if (result == TickResult.FAILED) {
      int failures = bridgeFailureCount == null ? 1 : bridgeFailureCount.incrementAndGet();
      if (failures < Math.max(1, bridgeFailureThreshold)) {
        return;
      }

      String failureReason = "Disabled after " + failures + " consecutive runtime bridge failures: " + fluidTickBridge.getFailureReason();
      fluidTickBridge = fluidTickBridge.toUnavailable(failureReason);
      if (!runtimeBridgeFailureWarned) {
        runtimeBridgeFailureWarned = true;
        React.warn("Fast Fluids acceleration is passive: " + fluidTickBridge.getFailureReason()
            + " | " + fluidTickBridge.getResolutionSummary());
      }
      return;
    }

    if (bridgeFailureCount != null) {
      bridgeFailureCount.set(0);
    }
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

    private static FluidPulseKey of(Block block) {
      return new FluidPulseKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
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

    private boolean consumeOne() {
      while (true) {
        int current = remainingTicks.get();
        if (current <= 0) {
          return false;
        }
        if (remainingTicks.compareAndSet(current, current - 1)) {
          return true;
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

  private enum TickResult {
    TICKED,
    SKIPPED,
    FAILED
  }

  private enum FluidKind {
    WATER,
    LAVA,
    OTHER
  }

  private static final class VanillaFluidTickBridge {
    private static final Map<Class<?>, Method> handleMethodCache = new ConcurrentHashMap<>();
    private final Class<?> serverLevelClass;
    private final Class<?> blockPosClass;
    private final Class<?> fluidStateClass;
    private final Class<?> fluidTypeClass;
    private final Constructor<?> blockPosConstructor;
    private final Method getFluidStateMethod;
    private final Method fluidStateIsEmptyMethod;
    private final Method fluidStateGetTypeMethod;
    private final Method worldFluidTickMethod;
    private final Method fluidTypeTickMethod;
    private final Method fluidStateTickMethod;
    private final String resolutionSummary;
    private volatile boolean available;
    private volatile String failureReason;

    private VanillaFluidTickBridge(
        Class<?> serverLevelClass,
        Class<?> blockPosClass,
        Class<?> fluidStateClass,
        Class<?> fluidTypeClass,
        Constructor<?> blockPosConstructor,
        Method getFluidStateMethod,
        Method fluidStateIsEmptyMethod,
        Method fluidStateGetTypeMethod,
        Method worldFluidTickMethod,
        Method fluidTypeTickMethod,
        Method fluidStateTickMethod,
        String resolutionSummary,
        boolean available,
        String failureReason
    ) {
      this.serverLevelClass = serverLevelClass;
      this.blockPosClass = blockPosClass;
      this.fluidStateClass = fluidStateClass;
      this.fluidTypeClass = fluidTypeClass;
      this.blockPosConstructor = blockPosConstructor;
      this.getFluidStateMethod = getFluidStateMethod;
      this.fluidStateIsEmptyMethod = fluidStateIsEmptyMethod;
      this.fluidStateGetTypeMethod = fluidStateGetTypeMethod;
      this.worldFluidTickMethod = worldFluidTickMethod;
      this.fluidTypeTickMethod = fluidTypeTickMethod;
      this.fluidStateTickMethod = fluidStateTickMethod;
      this.resolutionSummary = resolutionSummary == null ? "resolution=unresolved" : resolutionSummary;
      this.available = available;
      this.failureReason = failureReason;
    }

    private static VanillaFluidTickBridge resolve() {
      try {
        Class<?> serverLevelClass = resolveClass("net.minecraft.server.level.ServerLevel", "net.minecraft.server.level.WorldServer");
        if (serverLevelClass == null) {
          return unavailable("ServerLevel class was not found.");
        }

        Class<?> blockPosClass = resolveClass("net.minecraft.core.BlockPos", "net.minecraft.core.BlockPosition");
        if (blockPosClass == null) {
          return unavailable("BlockPos class was not found.");
        }

        Constructor<?> blockPosConstructor = blockPosClass.getDeclaredConstructor(int.class, int.class, int.class);
        makeAccessible(blockPosConstructor);
        Method getFluidStateMethod = resolveFluidStateMethod(serverLevelClass, blockPosClass);
        if (getFluidStateMethod == null) {
          return unavailable("Fluid state lookup method was not found.");
        }

        Class<?> fluidStateClass = getFluidStateMethod.getReturnType();
        if (!isMaterialPackageClass(fluidStateClass)) {
          return unavailable("Fluid state lookup return type was not a material package class.");
        }
        Method fluidStateGetTypeMethod = resolveFluidStateGetTypeMethod(fluidStateClass);
        Class<?> inferredFluidTypeClass = fluidStateGetTypeMethod == null ? null : fluidStateGetTypeMethod.getReturnType();
        Method worldFluidTickMethod = resolveWorldFluidTickMethod(serverLevelClass, blockPosClass, inferredFluidTypeClass);
        Class<?> fluidTypeClass = inferredFluidTypeClass;
        if (worldFluidTickMethod != null) {
          Class<?> worldFluidTickArgumentClass = worldFluidTickMethod.getParameterTypes()[1];
          if (fluidTypeClass == null || !isMutuallyAssignable(fluidTypeClass, worldFluidTickArgumentClass)) {
            fluidTypeClass = worldFluidTickArgumentClass;
          }
        }
        Method fluidStateIsEmptyMethod = resolveFluidStateIsEmptyMethod(fluidStateClass);
        Method fluidTypeTickMethod = fluidTypeClass == null ? null : resolveFluidTypeTickMethod(fluidTypeClass, serverLevelClass, blockPosClass, fluidStateClass);
        Method fluidStateTickMethod = resolveFluidStateTickMethod(fluidStateClass, serverLevelClass, blockPosClass);
        String resolutionSummary = buildResolutionSummary(
            serverLevelClass,
            blockPosClass,
            fluidStateClass,
            fluidTypeClass,
            getFluidStateMethod,
            fluidStateGetTypeMethod,
            worldFluidTickMethod,
            fluidTypeTickMethod,
            fluidStateTickMethod
        );
        if (worldFluidTickMethod == null && fluidTypeTickMethod == null && fluidStateTickMethod == null) {
          return unavailable("No native fluid tick method was found.", resolutionSummary);
        }

        return new VanillaFluidTickBridge(
            serverLevelClass,
            blockPosClass,
            fluidStateClass,
            fluidTypeClass,
            blockPosConstructor,
            getFluidStateMethod,
            fluidStateIsEmptyMethod,
            fluidStateGetTypeMethod,
            worldFluidTickMethod,
            fluidTypeTickMethod,
            fluidStateTickMethod,
            resolutionSummary,
            true,
            ""
        );
      } catch (Throwable throwable) {
        return unavailable("Native fluid bridge resolution failed: " + briefThrowable(throwable));
      }
    }

    private static VanillaFluidTickBridge unavailable(String reason) {
      return unavailable(reason, "resolution=unresolved");
    }

    private static VanillaFluidTickBridge unavailable(String reason, String resolutionSummary) {
      return new VanillaFluidTickBridge(
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          resolutionSummary,
          false,
          reason
      );
    }

    private VanillaFluidTickBridge toUnavailable(String reason) {
      return new VanillaFluidTickBridge(
          serverLevelClass,
          blockPosClass,
          fluidStateClass,
          fluidTypeClass,
          blockPosConstructor,
          getFluidStateMethod,
          fluidStateIsEmptyMethod,
          fluidStateGetTypeMethod,
          worldFluidTickMethod,
          fluidTypeTickMethod,
          fluidStateTickMethod,
          resolutionSummary,
          false,
          reason
      );
    }

    private boolean isAvailable() {
      return available;
    }

    private String getFailureReason() {
      return failureReason == null || failureReason.isBlank() ? "No additional details." : failureReason;
    }

    private String getResolutionSummary() {
      return resolutionSummary == null || resolutionSummary.isBlank() ? "resolution=unresolved" : resolutionSummary;
    }

    private TickResult tickVanillaFluid(Block block, boolean allowWater, boolean allowLava) {
      if (!available || block == null || block.getWorld() == null) {
        return TickResult.FAILED;
      }

      if (!allowWater && !allowLava) {
        return TickResult.SKIPPED;
      }

      try {
        int chunkX = block.getX() >> 4;
        int chunkZ = block.getZ() >> 4;
        World world = block.getWorld();
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          return TickResult.SKIPPED;
        }
        if (!isChunkNeighborhoodReady(world, block.getX(), block.getZ())) {
          return TickResult.SKIPPED;
        }

        Method getHandleMethod = resolveWorldHandleMethod(world.getClass(), serverLevelClass);
        if (getHandleMethod == null) {
          failureReason = "World handle accessor method was not found.";
          return TickResult.FAILED;
        }

        Object worldHandle = getHandleMethod.invoke(world);
        if (worldHandle == null) {
          failureReason = "World handle resolution returned null.";
          return TickResult.FAILED;
        }

        if (serverLevelClass != null && !serverLevelClass.isInstance(worldHandle)) {
          failureReason = "World handle type did not match expected ServerLevel class.";
          return TickResult.FAILED;
        }

        Object blockPos = blockPosConstructor.newInstance(block.getX(), block.getY(), block.getZ());
        Object fluidState = getFluidStateMethod.invoke(worldHandle, blockPos);
        if (fluidState == null) {
          return TickResult.SKIPPED;
        }

        if (fluidStateClass != null && !fluidStateClass.isInstance(fluidState)) {
          return TickResult.SKIPPED;
        }

        if (fluidStateIsEmptyMethod != null) {
          Object emptyValue = fluidStateIsEmptyMethod.invoke(fluidState);
          if (emptyValue instanceof Boolean && (Boolean) emptyValue) {
            return TickResult.SKIPPED;
          }
        }

        Material blockMaterial = block.getType();
        if (blockMaterial == Material.WATER && !allowWater) {
          return TickResult.SKIPPED;
        }
        if (blockMaterial == Material.LAVA && !allowLava) {
          return TickResult.SKIPPED;
        }

        Object fluidType = null;
        if (fluidStateGetTypeMethod != null) {
          fluidType = fluidStateGetTypeMethod.invoke(fluidState);
        }
        if (fluidType == null && fluidTypeClass != null && fluidTypeClass.isInstance(fluidState)) {
          fluidType = fluidState;
        }

        if (blockMaterial != Material.WATER && blockMaterial != Material.LAVA) {
          FluidKind kind = resolveFluidKind(fluidType, blockMaterial);
          if (kind == FluidKind.WATER && !allowWater) {
            return TickResult.SKIPPED;
          }
          if (kind == FluidKind.LAVA && !allowLava) {
            return TickResult.SKIPPED;
          }
        }

        if (worldFluidTickMethod != null) {
          Object primaryFluidArgument = selectPrimaryFluidArgument(worldFluidTickMethod, fluidState, fluidType);
          if (primaryFluidArgument != null) {
            worldFluidTickMethod.invoke(worldHandle, blockPos, primaryFluidArgument);
            return TickResult.TICKED;
          }
        }

        if (fluidTypeTickMethod != null) {
          Object fluidTypeReceiver = selectReceiver(fluidTypeTickMethod.getDeclaringClass(), fluidType, fluidState);
          if (fluidTypeReceiver != null) {
            Object[] fluidTypeArguments = buildInvocationArguments(fluidTypeTickMethod, worldHandle, blockPos, fluidState, fluidType);
            if (fluidTypeArguments != null) {
              fluidTypeTickMethod.invoke(fluidTypeReceiver, fluidTypeArguments);
              return TickResult.TICKED;
            }
          }
        }

        if (fluidStateTickMethod != null) {
          Object fluidStateReceiver = selectReceiver(fluidStateTickMethod.getDeclaringClass(), fluidState, fluidType);
          if (fluidStateReceiver != null) {
            Object[] fluidStateArguments = buildInvocationArguments(fluidStateTickMethod, worldHandle, blockPos, fluidState, fluidType);
            if (fluidStateArguments != null) {
              fluidStateTickMethod.invoke(fluidStateReceiver, fluidStateArguments);
              return TickResult.TICKED;
            }
          }
        }

        failureReason = "Native fluid bridge could not satisfy any tick method argument shape.";
        return TickResult.FAILED;
      } catch (Throwable throwable) {
        failureReason = "Native fluid bridge runtime failure: " + briefThrowable(throwable);
        return TickResult.FAILED;
      }
    }

    private static FluidKind resolveFluidKind(Object fluidType, Material blockMaterial) {
      if (blockMaterial == Material.WATER) {
        return FluidKind.WATER;
      }
      if (blockMaterial == Material.LAVA) {
        return FluidKind.LAVA;
      }
      if (fluidType == null) {
        return FluidKind.OTHER;
      }

      String className = fluidType.getClass().getName().toLowerCase();
      if (className.contains("water")) {
        return FluidKind.WATER;
      }
      if (className.contains("lava")) {
        return FluidKind.LAVA;
      }

      String typeString = fluidType.toString().toLowerCase();
      if (typeString.contains("water")) {
        return FluidKind.WATER;
      }
      if (typeString.contains("lava")) {
        return FluidKind.LAVA;
      }

      return FluidKind.OTHER;
    }

    private static Method resolveFluidStateMethod(Class<?> worldClass, Class<?> blockPosClass) {
      Method bestMethod = null;
      int bestScore = Integer.MIN_VALUE;
      List<Method> methods = collectMethods(worldClass);
      for (Method method : methods) {
        if (method.getParameterCount() != 1) {
          continue;
        }

        Class<?> parameterType = method.getParameterTypes()[0];
        if (!isParameterCompatible(parameterType, blockPosClass)) {
          continue;
        }

        Class<?> returnType = method.getReturnType();
        if (returnType == void.class || !isMaterialPackageClass(returnType)) {
          continue;
        }

        String methodName = method.getName().toLowerCase();
        int score = 200;
        if (methodName.equals("getfluidstate")) {
          score += 200;
        }
        if (methodName.contains("fluid")) {
          score += 60;
        }
        if (methodName.contains("state")) {
          score += 20;
        }
        if (method.getDeclaringClass() == worldClass) {
          score += 15;
        }
        if (parameterType == blockPosClass) {
          score += 10;
        }
        if (returnType.getSimpleName().toLowerCase().contains("fluid")) {
          score += 10;
        }

        if (score > bestScore) {
          bestScore = score;
          bestMethod = method;
        }
      }

      return bestMethod;
    }

    private static Method resolveFluidStateGetTypeMethod(Class<?> fluidStateClass) {
      Method bestMethod = null;
      int bestScore = Integer.MIN_VALUE;
      List<Method> methods = collectMethods(fluidStateClass);
      for (Method method : methods) {
        if (method.getParameterCount() != 0) {
          continue;
        }

        Class<?> returnType = method.getReturnType();
        if (returnType == void.class || !isMaterialPackageClass(returnType)) {
          continue;
        }

        String methodName = method.getName().toLowerCase();
        int score = 120;
        if (methodName.equals("gettype")) {
          score += 180;
        }
        if (methodName.contains("type")) {
          score += 80;
        }
        if (methodName.contains("fluid")) {
          score += 25;
        }
        if (returnType != fluidStateClass) {
          score += 20;
        }
        if (returnType.getSimpleName().toLowerCase().contains("fluid")) {
          score += 15;
        }

        if (score > bestScore) {
          bestScore = score;
          bestMethod = method;
        }
      }

      return bestMethod;
    }

    private static Method resolveFluidStateIsEmptyMethod(Class<?> fluidStateClass) {
      Method bestMethod = null;
      int bestScore = Integer.MIN_VALUE;
      List<Method> methods = collectMethods(fluidStateClass);
      for (Method method : methods) {
        if (method.getParameterCount() != 0 || method.getReturnType() != boolean.class) {
          continue;
        }

        String methodName = method.getName().toLowerCase();
        int score = 100;
        if (methodName.equals("isempty")) {
          score += 240;
        }
        if (methodName.contains("empty")) {
          score += 140;
        }
        if (methodName.contains("source")) {
          score -= 60;
        }

        if (score > bestScore) {
          bestScore = score;
          bestMethod = method;
        }
      }

      if (bestScore < 130) {
        return null;
      }

      return bestMethod;
    }

    private static Method resolveWorldFluidTickMethod(Class<?> worldClass, Class<?> blockPosClass, Class<?> fluidTypeClass) {
      Method bestMethod = null;
      int bestScore = Integer.MIN_VALUE;
      List<Method> methods = collectMethods(worldClass);
      for (Method method : methods) {
        if (method.getReturnType() != void.class || method.getParameterCount() != 2) {
          continue;
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        if (!isParameterCompatible(parameterTypes[0], blockPosClass)) {
          continue;
        }
        if (!isMaterialPackageClass(parameterTypes[1])) {
          continue;
        }

        String methodName = method.getName().toLowerCase();
        int score = 240;
        if (methodName.equals("tickfluid")) {
          score += 260;
        }
        if (methodName.contains("tick")) {
          score += 120;
        }
        if (fluidTypeClass != null && isMutuallyAssignable(parameterTypes[1], fluidTypeClass)) {
          score += 100;
        }
        if (method.getDeclaringClass() == worldClass) {
          score += 20;
        }
        if (parameterTypes[1].getSimpleName().toLowerCase().contains("fluid")) {
          score += 20;
        }

        if (score > bestScore) {
          bestScore = score;
          bestMethod = method;
        }
      }

      return bestMethod;
    }

    private static Method resolveFluidTypeTickMethod(Class<?> fluidTypeClass, Class<?> worldClass, Class<?> blockPosClass, Class<?> fluidStateClass) {
      Method bestMethod = null;
      int bestScore = Integer.MIN_VALUE;
      List<Method> methods = collectMethods(fluidTypeClass);
      for (Method method : methods) {
        if (method.getReturnType() != void.class || method.getParameterCount() < 3) {
          continue;
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        if (!isParameterCompatible(parameterTypes[0], worldClass)) {
          continue;
        }
        if (!isParameterCompatible(parameterTypes[1], blockPosClass)) {
          continue;
        }
        if (!isParameterCompatible(parameterTypes[2], fluidStateClass)) {
          continue;
        }

        String methodName = method.getName().toLowerCase();
        int score = 220 - method.getParameterCount() * 8;
        if (methodName.equals("tick")) {
          score += 220;
        }
        if (methodName.contains("tick")) {
          score += 120;
        }
        if (methodName.contains("fluid")) {
          score += 20;
        }

        if (score > bestScore) {
          bestScore = score;
          bestMethod = method;
        }
      }

      return bestMethod;
    }

    private static Method resolveFluidStateTickMethod(Class<?> fluidStateClass, Class<?> worldClass, Class<?> blockPosClass) {
      Method bestMethod = null;
      int bestScore = Integer.MIN_VALUE;
      List<Method> methods = collectMethods(fluidStateClass);
      for (Method method : methods) {
        if (method.getReturnType() != void.class || method.getParameterCount() < 2) {
          continue;
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        if (!isParameterCompatible(parameterTypes[0], worldClass)) {
          continue;
        }
        if (!isParameterCompatible(parameterTypes[1], blockPosClass)) {
          continue;
        }

        String methodName = method.getName().toLowerCase();
        int score = 180 - method.getParameterCount() * 8;
        if (methodName.equals("tick")) {
          score += 220;
        }
        if (methodName.contains("tick")) {
          score += 120;
        }
        if (methodName.contains("fluid")) {
          score += 20;
        }

        if (score > bestScore) {
          bestScore = score;
          bestMethod = method;
        }
      }

      return bestMethod;
    }

    private static Method resolveWorldHandleMethod(Class<?> worldClass, Class<?> serverLevelClass) {
      Method cached = handleMethodCache.get(worldClass);
      if (cached != null) {
        return cached;
      }

      Method method = findMethodByName(worldClass, "getHandle");
      if (method == null) {
        List<Method> methods = collectMethods(worldClass);
        for (Method candidate : methods) {
          if (candidate.getParameterCount() != 0) {
            continue;
          }

          Class<?> returnType = candidate.getReturnType();
          if (returnType == void.class) {
            continue;
          }

          if (serverLevelClass != null && isMutuallyAssignable(returnType, serverLevelClass)) {
            method = candidate;
            break;
          }
        }
      }

      if (method != null) {
        makeAccessible(method);
        handleMethodCache.put(worldClass, method);
      }

      return method;
    }

    private static Object selectPrimaryFluidArgument(Method primaryMethod, Object fluidState, Object fluidType) {
      Class<?> argumentType = primaryMethod.getParameterTypes()[1];
      if (fluidType != null && argumentType.isInstance(fluidType)) {
        return fluidType;
      }

      if (fluidState != null && argumentType.isInstance(fluidState)) {
        return fluidState;
      }

      return null;
    }

    private static Object selectReceiver(Class<?> receiverType, Object primary, Object secondary) {
      if (primary != null && receiverType.isInstance(primary)) {
        return primary;
      }

      if (secondary != null && receiverType.isInstance(secondary)) {
        return secondary;
      }

      return null;
    }

    private static Object[] buildInvocationArguments(Method method, Object worldHandle, Object blockPos, Object fluidState, Object fluidType) {
      Class<?>[] parameterTypes = method.getParameterTypes();
      Object[] arguments = new Object[parameterTypes.length];
      for (int i = 0; i < parameterTypes.length; i++) {
        Class<?> parameterType = parameterTypes[i];
        if (i == 0) {
          if (!parameterType.isInstance(worldHandle)) {
            return null;
          }
          arguments[i] = worldHandle;
          continue;
        }

        if (i == 1) {
          if (!parameterType.isInstance(blockPos)) {
            return null;
          }
          arguments[i] = blockPos;
          continue;
        }

        if (i == 2 && fluidState != null && parameterType.isInstance(fluidState)) {
          arguments[i] = fluidState;
          continue;
        }

        if (fluidState != null && parameterType.isInstance(fluidState)) {
          arguments[i] = fluidState;
          continue;
        }

        if (fluidType != null && parameterType.isInstance(fluidType)) {
          arguments[i] = fluidType;
          continue;
        }

        if (parameterType.isPrimitive()) {
          arguments[i] = primitiveDefault(parameterType);
          continue;
        }

        arguments[i] = null;
      }

      return arguments;
    }

    private static Object primitiveDefault(Class<?> primitiveClass) {
      if (primitiveClass == boolean.class) {
        return false;
      }
      if (primitiveClass == byte.class) {
        return (byte) 0;
      }
      if (primitiveClass == short.class) {
        return (short) 0;
      }
      if (primitiveClass == int.class) {
        return 0;
      }
      if (primitiveClass == long.class) {
        return 0L;
      }
      if (primitiveClass == float.class) {
        return 0.0F;
      }
      if (primitiveClass == double.class) {
        return 0.0D;
      }
      if (primitiveClass == char.class) {
        return Character.valueOf('\0');
      }

      return 0;
    }

    private static boolean isParameterCompatible(Class<?> parameterType, Class<?> runtimeType) {
      return parameterType.isAssignableFrom(runtimeType);
    }

    private static boolean isMutuallyAssignable(Class<?> first, Class<?> second) {
      return first.isAssignableFrom(second) || second.isAssignableFrom(first);
    }

    private static boolean isMaterialPackageClass(Class<?> type) {
      if (type == null) {
        return false;
      }

      String name = type.getName();
      return name.startsWith("net.minecraft.world.level.material.");
    }

    private static List<Method> collectMethods(Class<?> owner) {
      List<Method> methods = new ArrayList<>();
      Method[] declared = owner.getDeclaredMethods();
      for (Method method : declared) {
        makeAccessible(method);
        methods.add(method);
      }

      Method[] inherited = owner.getMethods();
      for (Method method : inherited) {
        makeAccessible(method);
        methods.add(method);
      }

      return methods;
    }

    private static String buildResolutionSummary(
        Class<?> serverLevelClass,
        Class<?> blockPosClass,
        Class<?> fluidStateClass,
        Class<?> fluidTypeClass,
        Method getFluidStateMethod,
        Method fluidStateGetTypeMethod,
        Method worldFluidTickMethod,
        Method fluidTypeTickMethod,
        Method fluidStateTickMethod
    ) {
      List<String> parts = new ArrayList<>();
      parts.add("serverLevelClass=" + describeClass(serverLevelClass));
      parts.add("blockPosClass=" + describeClass(blockPosClass));
      parts.add("fluidStateClass=" + describeClass(fluidStateClass));
      parts.add("fluidTypeClass=" + describeClass(fluidTypeClass));
      parts.add("fluidGetter=" + describeMethod(getFluidStateMethod));
      parts.add("fluidTypeAccessor=" + describeMethod(fluidStateGetTypeMethod));
      parts.add("primaryTick=" + describeMethod(worldFluidTickMethod));
      parts.add("fallbackTypeTick=" + describeMethod(fluidTypeTickMethod));
      parts.add("fallbackStateTick=" + describeMethod(fluidStateTickMethod));
      return String.join(", ", parts);
    }

    private static String describeClass(Class<?> type) {
      return type == null ? "none" : type.getName();
    }

    private static String describeMethod(Method method) {
      if (method == null) {
        return "none";
      }

      StringBuilder builder = new StringBuilder();
      builder.append(method.getDeclaringClass().getName());
      builder.append('#');
      builder.append(method.getName());
      builder.append('(');
      Class<?>[] parameterTypes = method.getParameterTypes();
      for (int i = 0; i < parameterTypes.length; i++) {
        if (i > 0) {
          builder.append(',');
        }
        builder.append(parameterTypes[i].getName());
      }
      builder.append(')');
      builder.append(':');
      builder.append(method.getReturnType().getName());
      return builder.toString();
    }

    private static String briefThrowable(Throwable throwable) {
      Throwable root = throwable;
      while (root.getCause() != null && root.getCause() != root) {
        root = root.getCause();
      }

      String className = root.getClass().getSimpleName();
      String message = root.getMessage();
      if (message == null || message.isBlank()) {
        return className;
      }

      return className + " (" + message + ")";
    }

    private static boolean isChunkNeighborhoodReady(World world, int x, int z) {
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

    private static Method findMethodByName(Class<?> owner, String name, Class<?>... parameterTypes) {
      try {
        Method method = owner.getMethod(name, parameterTypes);
        makeAccessible(method);
        return method;
      } catch (NoSuchMethodException ignored) {
      }

      try {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        makeAccessible(method);
        return method;
      } catch (NoSuchMethodException ignored) {
        return null;
      }
    }

    private static void makeAccessible(Constructor<?> constructor) {
      try {
        constructor.setAccessible(true);
      } catch (Throwable ignored) {
      }
    }

    private static void makeAccessible(Method method) {
      try {
        method.setAccessible(true);
      } catch (Throwable ignored) {
      }
    }

    private static Class<?> resolveClass(String... names) {
      for (String name : names) {
        try {
          return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
        }
      }

      return null;
    }
  }
}
