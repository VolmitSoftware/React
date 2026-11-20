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

import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Fast Fluids tweak. Fast-forwards water and lava spread chains into bounded burst updates to reduce repeated per-step fluid churn.")
public class TweakFastFluids extends ReactTweak {
  public static final String ID = "fast-fluids";
  private static final int[][] DRAIN_NEIGHBORS = new int[][]{
      {0, -1, 0},
      {0, 1, 0},
      {0, 0, -1},
      {1, 0, 0},
      {0, 0, 1},
      {-1, 0, 0}
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
  private transient FluidPulseQueue pulses;
  private transient volatile BlockHost blocks;
  private transient volatile ChunkHost chunkHost;
  private transient volatile WorldHost worlds;
  private transient final List<Subscription> subscriptions = new ArrayList<>();
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
    pulses = new FluidPulseQueue();
    bridgeFailureGate = new BridgeFailureGate(
        clampInt(Integer.getInteger("react.fastfluids.bridgeFailureThreshold", 8), 1, 64));
    ReactPlatform platform = ReactPlatforms.get();
    blocks = platform.blockHost().orElse(null);
    chunkHost = platform.chunkHost().orElse(null);
    worlds = platform.worldHost().orElse(null);
    fluidBridgesAvailable = blocks != null && blocks.canTickFluids();
    if (!fluidBridgesAvailable) {
      platform.warn("Fast Fluids acceleration is passive: no usable fluid bridge resolved");
    }
    BlockHost host = blocks;
    if (host != null) {
      subscriptions.add(host.onFluidFlow(ListenerPriority.MONITOR, true, this::onFlow));
      subscriptions.add(host.onFluidLevelChange(ListenerPriority.MONITOR, true, this::onLevelChange));
    }
    pulseTaskId = J.sr(this::flushPulseQueue, 1);
  }

  void onFlow(BlockHost.FluidFlowEvent event) {
    if (extraVanillaTicksPerEvent <= 0) {
      return;
    }

    if (!fluidBridgesAvailable) {
      return;
    }

    String sourceMaterial = event.fromType();
    String targetMaterial = event.toType();
    if (!isSupportedFluid(sourceMaterial) && !isSupportedFluid(targetMaterial)) {
      return;
    }

    GameLocation sourceBlock = event.from();
    GameLocation targetBlock = event.to();
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

  void onLevelChange(BlockHost.FluidLevelEvent event) {
    if (extraVanillaTicksPerEvent <= 0) {
      return;
    }

    if (!fluidBridgesAvailable) {
      return;
    }

    String currentMaterial = event.type();
    String newMaterial = event.newType() == null ? currentMaterial : event.newType();
    if (!isSupportedFluid(currentMaterial) && !isSupportedFluid(newMaterial)) {
      return;
    }

    GameLocation block = event.location();
    int extraTicks = clampInt(extraVanillaTicksPerEvent, 0, 4);
    enqueueForTicks(block, extraTicks);
    if (accelerateDrain && isLikelyDrainTransition(currentMaterial, event.level(), newMaterial, event.newLevel())) {
      enqueueNeighbors(block, extraTicks);
    }
  }

  @Override
  public void onDeactivate() {
    for (Subscription subscription : subscriptions) {
      subscription.cancel();
    }
    subscriptions.clear();
    if (pulseTaskId != 0) {
      J.csr(pulseTaskId);
      pulseTaskId = 0;
    }

    if (pulses != null) {
      pulses.clear();
    }
    fluidBridgesAvailable = false;
    bridgeFailureGate = null;
  }

  private void flushPulseQueue() {
    FluidPulseQueue queue = pulses;
    if (!fluidBridgesAvailable || queue == null || queue.isEmpty()) {
      return;
    }

    int budget = clampInt(maxExtraVanillaTicksPerServerTick, 16, 4096);
    int maxBurst = clampInt(maxBurstTicksPerLocationPerServerTick, 1, 16);
    Map<FluidPulseQueue.FluidChunk, List<FluidPulseQueue.FluidBurst>> buckets = queue.drain(budget, maxBurst);
    boolean folia = J.isFoliaThreading();
    for (Map.Entry<FluidPulseQueue.FluidChunk, List<FluidPulseQueue.FluidBurst>> entry : buckets.entrySet()) {
      FluidPulseQueue.FluidChunk chunk = entry.getKey();
      WorldHost presence = worlds;
      ReactWorld world = presence == null ? null : presence.world(chunk.worldId()).orElse(null);
      if (world == null) {
        queue.discardWorld(chunk.worldId());
        continue;
      }

      List<FluidPulseQueue.FluidBurst> bursts = entry.getValue();
      if (folia) {
        J.runChunk(world, chunk.x(), chunk.z(), () -> runBursts(world, bursts));
      } else {
        runBursts(world, bursts);
      }
    }
  }

  private void runBursts(ReactWorld world, List<FluidPulseQueue.FluidBurst> bursts) {
    for (FluidPulseQueue.FluidBurst burst : bursts) {
      if (!fluidBridgesAvailable) {
        return;
      }
      runPulse(world, burst);
    }
  }

  private void runPulse(ReactWorld world, FluidPulseQueue.FluidBurst burst) {
    if (!accelerateWater && !accelerateLava) {
      resetBridgeFailures();
      return;
    }

    int x = burst.x();
    int y = burst.y();
    int z = burst.z();
    if (!isChunkNeighborhoodReady(world.id(), x, z)) {
      resetBridgeFailures();
      return;
    }

    try {
      for (int i = 0; i < Math.max(1, burst.ticks()); i++) {
        if (!blocks.tickFluid(world.id(), x, y, z, accelerateWater, accelerateLava)) {
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
    ReactPlatforms.get().warn("Fast Fluids acceleration is passive: consecutive runtime bridge failures exceeded threshold");
  }

  private void reportBridgeThrowable(Throwable throwable) {
    BridgeFailureGate gate = bridgeFailureGate;
    if (gate == null || !gate.shouldReport(throwable)) {
      return;
    }

    ReactPlatforms.get().reportError("Fast Fluids runtime bridge failure on the fluid tick path: "
        + throwable.getClass().getName()
        + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage()), throwable);
  }

  private void enqueueForTicks(GameLocation block, int extraTicks) {
    if (block == null || block.worldId() == null || extraTicks <= 0) {
      return;
    }

    enqueueForTicks(block.worldId(), block.blockX(), block.blockY(), block.blockZ(), extraTicks);
  }

  private void enqueueForTicks(UUID worldId, int x, int y, int z, int extraTicks) {
    BlockHost host = blocks;
    if (host == null || worldId == null || extraTicks <= 0) {
      return;
    }

    if (y < host.minHeight(worldId) || y >= host.maxHeight(worldId)) {
      return;
    }

    int chunkX = x >> 4;
    int chunkZ = z >> 4;
    if (!isChunkLoaded(worldId, chunkX, chunkZ)) {
      return;
    }

    FluidPulseQueue queue = pulses;
    if (queue != null) {
      queue.enqueue(worldId, x, y, z, extraTicks);
    }
  }

  private void enqueueNeighbors(GameLocation block, int extraTicks) {
    BlockHost host = blocks;
    if (host == null || block == null || block.worldId() == null || extraTicks <= 0) {
      return;
    }

    UUID worldId = block.worldId();
    int minHeight = host.minHeight(worldId);
    int maxHeight = host.maxHeight(worldId);
    int originX = block.blockX();
    int originY = block.blockY();
    int originZ = block.blockZ();
    for (int[] face : DRAIN_NEIGHBORS) {
      int x = originX + face[0];
      int y = originY + face[1];
      int z = originZ + face[2];
      if (y < minHeight || y >= maxHeight) {
        continue;
      }
      if (!isChunkNeighborhoodReady(worldId, x, z)) {
        continue;
      }
      enqueueForTicks(worldId, x, y, z, extraTicks);
    }
  }

  private boolean isSupportedFluid(String material) {
    if ("WATER".equals(material)) {
      return accelerateWater;
    }

    if ("LAVA".equals(material)) {
      return accelerateLava;
    }

    return false;
  }

  private boolean isLikelyDrainTransition(String currentMaterial, int currentLevel, String newMaterial, int newLevel) {
    if (!isSupportedFluid(currentMaterial)) {
      return false;
    }

    if (!isSupportedFluid(newMaterial)) {
      return true;
    }

    if (currentLevel < 0 || newLevel < 0) {
      return false;
    }

    return newLevel > currentLevel;
  }

  private boolean isChunkNeighborhoodReady(UUID worldId, int x, int z) {
    BlockHost host = blocks;
    if (worldId == null || host == null) {
      return false;
    }

    int chunkX = x >> 4;
    int chunkZ = z >> 4;
    if (!isChunkLoaded(worldId, chunkX, chunkZ)) {
      return false;
    }

    int localX = x & 15;
    int localZ = z & 15;
    if (localX == 0 && !isChunkLoaded(worldId, chunkX - 1, chunkZ)) {
      return false;
    }
    if (localX == 15 && !isChunkLoaded(worldId, chunkX + 1, chunkZ)) {
      return false;
    }
    if (localZ == 0 && !isChunkLoaded(worldId, chunkX, chunkZ - 1)) {
      return false;
    }
    if (localZ == 15 && !isChunkLoaded(worldId, chunkX, chunkZ + 1)) {
      return false;
    }
    if (localX == 0 && localZ == 0 && !isChunkLoaded(worldId, chunkX - 1, chunkZ - 1)) {
      return false;
    }
    if (localX == 0 && localZ == 15 && !isChunkLoaded(worldId, chunkX - 1, chunkZ + 1)) {
      return false;
    }
    if (localX == 15 && localZ == 0 && !isChunkLoaded(worldId, chunkX + 1, chunkZ - 1)) {
      return false;
    }
    if (localX == 15 && localZ == 15 && !isChunkLoaded(worldId, chunkX + 1, chunkZ + 1)) {
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

  private boolean isChunkLoaded(UUID worldId, int chunkX, int chunkZ) {
    ChunkHost host = chunkHost;
    return host != null && host.isLoaded(worldId, chunkX, chunkZ);
  }
}
