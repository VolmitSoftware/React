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

package art.arcane.react.content.action;

import art.arcane.react.React;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.action.ReactAction;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.model.SampledWorld;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.entity.StackExclusion;
import art.arcane.volmlib.util.format.Form;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Quarantine Hot Chunks action. Isolates high-score chunks and optionally culls old entities away from players.")
public class ActionQuarantineHotChunks extends ReactAction<ActionQuarantineHotChunks.Params> {
  public static final String ID = "action-quarantine-hot-chunks";
  public static final String SHORT = "aqhc";

  public ActionQuarantineHotChunks() {
    super(ID);
  }

  @Override
  public String getCompletedMessage(ActionTicket<Params> ticket) {
    Params p = ticket.getParams();
    return "Quarantined " + p.getChunksQuarantined() + " chunks, culled " + p.getEntitiesCulled() + " entities in " + Form.duration(ticket.getDuration(), 1);
  }

  @Override
  public void workOn(ActionTicket<Params> ticket) {
    Params params = ticket.getParams();
    if (!params.isPrepared()) {
      prepare(params);
      params.getChunksQuarantinedAtomic().set(0);
      params.getEntitiesCulledAtomic().set(0);
      params.getProcessedChunks().set(0);
      params.getInFlightChunks().set(0);
      ticket.setWork(0);
      ticket.setTotalWork(Math.max(1, params.getQueue().size()));
    }

    workOnQuarantineAsync(ticket, params);
  }

  private void workOnQuarantineAsync(ActionTicket<Params> ticket, Params params) {
    params.setChunksQuarantined(params.getChunksQuarantinedAtomic().get());
    params.setEntitiesCulled(params.getEntitiesCulledAtomic().get());
    ticket.setCount(params.getChunksQuarantined());
    ticket.setWork(Math.min(ticket.getTotalWork(), params.getProcessedChunks().get()));

    if (params.getQueue().isEmpty() && params.getInFlightChunks().get() <= 0) {
      ticket.complete();
      return;
    }

    int chunkBudget = Math.max(1, React.controller(ActionController.class).getActionSpeedMultiplier() / 8);
    int maxInFlight = Math.max(4, chunkBudget * 2);
    int dispatched = 0;
    while (dispatched < chunkBudget
        && !params.getQueue().isEmpty()
        && params.getInFlightChunks().get() < maxInFlight) {
      ChunkRef next = params.getQueue().pollFirst();
      if (next == null) {
        break;
      }

      dispatchChunkQuarantine(next, params);
      dispatched++;
    }

    params.setChunksQuarantined(params.getChunksQuarantinedAtomic().get());
    params.setEntitiesCulled(params.getEntitiesCulledAtomic().get());
    ticket.setCount(params.getChunksQuarantined());
    ticket.setWork(Math.min(ticket.getTotalWork(), params.getProcessedChunks().get()));

    if (params.getQueue().isEmpty() && params.getInFlightChunks().get() <= 0) {
      ticket.complete();
    }
  }

  @Override
  public Params getDefaultParams() {
    return Params.builder().build();
  }

  @Override
  public void onInit() {

  }

  private void prepare(Params params) {
    params.setPrepared(true);
    params.setQueue(new ArrayDeque<>());

    ObserverController observer = React.controller(ObserverController.class);
    if (observer == null || observer.getSampled() == null) {
      return;
    }

    Map<ChunkRef, Double> weighted = new HashMap<>();
    for (SampledWorld sampledWorld : observer.getSampled().getWorlds().values()) {
      World world = sampledWorld.getWorld();
      if (world == null) {
        continue;
      }

      if (params.getWorld() != null && !params.getWorld().isBlank() && !WorldIdentity.serialize(world).equals(params.getWorld())) {
        continue;
      }

      for (SampledChunk sampledChunk : sampledWorld.getChunks().values()) {
        Chunk chunk = sampledChunk.getChunk();
        if (chunk == null || chunk.getWorld() == null) {
          continue;
        }

        double score = sampledChunk.totalScore();
        if (score < params.getMinimumChunkScore()) {
          continue;
        }

        ChunkRef ref = ChunkRef.of(chunk);
        weighted.merge(ref, score, Math::max);

        if (params.isIncludeNeighborRing()) {
          for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
              if (dx == 0 && dz == 0) {
                continue;
              }

              ChunkRef neighbor = new ChunkRef(WorldIdentity.serialize(world), chunk.getX() + dx, chunk.getZ() + dz);
              weighted.merge(neighbor, score * 0.55D, Math::max);
            }
          }
        }
      }
    }

    weighted.entrySet().stream()
        .filter(i -> i.getValue() >= params.getMinimumChunkScore() * 0.35D)
        .sorted(Map.Entry.<ChunkRef, Double>comparingByValue(Comparator.reverseOrder()))
        .limit(Math.max(1, params.getMaxChunks()))
        .map(Map.Entry::getKey)
        .forEach(params.getQueue()::add);
  }

  private void dispatchChunkQuarantine(ChunkRef ref, Params params) {
    World world = WorldIdentity.resolve(ref.world()).orElse(null);
    if (world == null) {
      params.getProcessedChunks().incrementAndGet();
      return;
    }

    params.getInFlightChunks().incrementAndGet();
    boolean scheduled = J.runChunk(world, ref.x(), ref.z(), () -> {
      try {
        QuarantineResult result = quarantineChunkSync(ref, params);
        if (result != null) {
          if (result.entitiesCulled() > 0) {
            params.getEntitiesCulledAtomic().addAndGet(result.entitiesCulled());
          }
          if (result.chunkUnloaded()) {
            params.getChunksQuarantinedAtomic().incrementAndGet();
          }
        }
      } finally {
        params.getProcessedChunks().incrementAndGet();
        params.getInFlightChunks().decrementAndGet();
      }
    });

    if (!scheduled) {
      params.getProcessedChunks().incrementAndGet();
      params.getInFlightChunks().decrementAndGet();
    }
  }

  private QuarantineResult quarantineChunkSync(ChunkRef ref, Params params) {
    World world = WorldIdentity.resolve(ref.world()).orElse(null);
    if (world == null) {
      return new QuarantineResult(false, 0);
    }

    if (hasNearbyPlayer(world, ref.x(), ref.z(), params.getUnsafePlayerRadius())) {
      return new QuarantineResult(false, 0);
    }

    if (!world.isChunkLoaded(ref.x(), ref.z())) {
      return new QuarantineResult(false, 0);
    }

    Chunk chunk = world.getChunkAt(ref.x(), ref.z());
    int culled = 0;
    if (params.isCullEntities()) {
      for (Entity entity : chunk.getEntities()) {
        if (!canCull(entity, params)) {
          continue;
        }

        React.kill(entity, 4 + ThreadLocalRandom.current().nextInt(8));
        culled++;
      }
    }

    boolean unloaded = false;
    if (params.isUnloadChunk() && world.isChunkLoaded(ref.x(), ref.z())) {
      chunk.unload(true);
      unloaded = !world.isChunkLoaded(ref.x(), ref.z());
    }

    return new QuarantineResult(unloaded, culled);
  }

  private boolean canCull(Entity entity, Params params) {
    if (entity == null || entity.isDead() || entity instanceof Player) {
      return false;
    }

    if (entity.getTicksLived() < params.getMinEntityAgeTicks()) {
      return false;
    }

    if (StackExclusion.isExcluded(entity)) {
      return false;
    }

    if (params.isProtectNamed() && entity.getCustomName() != null && !entity.getCustomName().isBlank()) {
      return false;
    }

    if (params.isProtectTamed() && entity instanceof Tameable tameable && tameable.isTamed()) {
      return false;
    }

    if (entity instanceof Villager) {
      return false;
    }

    if (params.isProtectBosses() && (entity instanceof EnderDragon || entity instanceof Wither || entity instanceof Warden)) {
      return false;
    }

    return true;
  }

  private boolean hasNearbyPlayer(World world, int chunkX, int chunkZ, double radiusBlocks) {
    double centerX = (chunkX << 4) + 8.0;
    double centerZ = (chunkZ << 4) + 8.0;
    double radiusSquared = radiusBlocks * radiusBlocks;
    for (Player player : world.getPlayers()) {
      Location location = player.getLocation();
      double dx = location.getX() - centerX;
      double dz = location.getZ() - centerZ;
      if ((dx * dx) + (dz * dz) <= radiusSquared) {
        return true;
      }
    }

    return false;
  }

  private record QuarantineResult(boolean chunkUnloaded, int entitiesCulled) {
  }

  private record ChunkRef(String world, int x, int z) {
    private static ChunkRef of(Chunk chunk) {
      Objects.requireNonNull(chunk.getWorld(), "chunk.world");
      return new ChunkRef(WorldIdentity.serialize(chunk.getWorld()), chunk.getX(), chunk.getZ());
    }
  }

  @Builder
  @Data
  @Accessors(chain = true)
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Params implements ActionParams {
    @art.arcane.react.util.project.config.ConfigDoc(value = "World name filter for quarantine hot chunks operations.", impact = "Set a world name to scope actions there, or leave blank to include all worlds.")
    private String world;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum chunks allowed by quarantine hot chunks.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
    private int maxChunks = 24;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum chunk score required by quarantine hot chunks.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
    private double minimumChunkScore = 90D;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Unsafe player radius used by quarantine hot chunks (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
    private double unsafePlayerRadius = 56D;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether quarantine hot chunks applies unload chunk.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean unloadChunk = true;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether quarantine hot chunks applies cull entities.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean cullEntities = true;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Includes neighbor ring in quarantine hot chunks processing.", impact = "Enable this to add those targets to processing; disable it to leave them out.")
    private boolean includeNeighborRing = true;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum entity age ticks required by quarantine hot chunks.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
    private int minEntityAgeTicks = 200;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Protects named from quarantine hot chunks enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
    private boolean protectNamed = true;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Protects tamed from quarantine hot chunks enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
    private boolean protectTamed = true;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Protects bosses from quarantine hot chunks enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
    private boolean protectBosses = true;
    @Builder.Default
    private transient boolean prepared = false;
    @Builder.Default
    private transient Deque<ChunkRef> queue = new ArrayDeque<>();
    @Builder.Default
    private transient int chunksQuarantined = 0;
    @Builder.Default
    private transient int entitiesCulled = 0;
    @Builder.Default
    private transient AtomicInteger inFlightChunks = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger processedChunks = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger chunksQuarantinedAtomic = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger entitiesCulledAtomic = new AtomicInteger(0);

    public Params withWorld(World world) {
      if (world != null) {
        this.world = WorldIdentity.serialize(world);
      }
      return this;
    }
  }
}
