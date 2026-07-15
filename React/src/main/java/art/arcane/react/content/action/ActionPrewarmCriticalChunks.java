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
import art.arcane.volmlib.util.format.Form;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Prewarm Critical Chunks action. Preloads high-risk chunks and neighbors to reduce stutter spikes.")
public class ActionPrewarmCriticalChunks extends ReactAction<ActionPrewarmCriticalChunks.Params> {
  public static final String ID = "action-prewarm-critical-chunks";

  public ActionPrewarmCriticalChunks() {
    super(ID);
  }

  @Override
  public String getCompletedMessage(ActionTicket<Params> ticket) {
    Params p = ticket.getParams();
    return "Prewarmed " + p.getChunksWarmed() + " chunks (" + p.getChunksLoaded() + " newly loaded) in " + Form.duration(ticket.getDuration(), 1);
  }

  @Override
  public void workOn(ActionTicket<Params> ticket) {
    Params params = ticket.getParams();
    if (!params.isPrepared()) {
      List<ChunkRef> queue = buildQueueSync(params);
      params.setQueue(new ArrayDeque<>(queue == null ? List.of() : queue));
      params.setChunksWarmed(0);
      params.setChunksLoaded(0);
      params.getChunksWarmedAtomic().set(0);
      params.getChunksLoadedAtomic().set(0);
      params.getChunksProcessedAtomic().set(0);
      params.getInFlightChunks().set(0);
      params.setPrepared(true);
      ticket.setWork(0);
      ticket.setTotalWork(Math.max(1, params.getQueue().size()));
    }

    workOnAsync(ticket, params);
  }

  private void workOnAsync(ActionTicket<Params> ticket, Params params) {
    params.setChunksWarmed(params.getChunksWarmedAtomic().get());
    params.setChunksLoaded(params.getChunksLoadedAtomic().get());
    ticket.setCount(params.getChunksWarmed());
    ticket.setWork(Math.min(ticket.getTotalWork(), params.getChunksProcessedAtomic().get()));

    if (params.getQueue().isEmpty() && params.getInFlightChunks().get() <= 0) {
      ticket.complete();
      return;
    }

    int chunkBudget = Math.max(1, React.controller(ActionController.class).getActionSpeedMultiplier() / 8);
    int maxInFlight = Math.max(4, chunkBudget * 2);
    int dispatched = 0;
    while (dispatched < chunkBudget && !params.getQueue().isEmpty() && params.getInFlightChunks().get() < maxInFlight) {
      ChunkRef target = params.getQueue().pollFirst();
      if (target == null) {
        break;
      }

      dispatchPrewarm(target, params);
      dispatched++;
    }

    params.setChunksWarmed(params.getChunksWarmedAtomic().get());
    params.setChunksLoaded(params.getChunksLoadedAtomic().get());
    ticket.setCount(params.getChunksWarmed());
    ticket.setWork(Math.min(ticket.getTotalWork(), params.getChunksProcessedAtomic().get()));

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

  private List<ChunkRef> buildQueueSync(Params params) {
    Map<ChunkRef, Double> weighted = new HashMap<>();
    ObserverController observer = React.controller(ObserverController.class);
    if (observer != null && observer.getSampled() != null) {
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
          if (score <= 0D) {
            continue;
          }

          addWeighted(weighted, ChunkRef.of(chunk), score);
          for (int dx = -params.getNeighborRadius(); dx <= params.getNeighborRadius(); dx++) {
            for (int dz = -params.getNeighborRadius(); dz <= params.getNeighborRadius(); dz++) {
              if (dx == 0 && dz == 0) {
                continue;
              }

              double dist = Math.sqrt((dx * dx) + (dz * dz));
              addWeighted(weighted, new ChunkRef(WorldIdentity.serialize(world), chunk.getX() + dx, chunk.getZ() + dz), score * (0.7D / (1D + dist)));
            }
          }
        }
      }
    }

    if (params.isIncludePlayerChunks() && !J.isFoliaThreading()) {
      for (Player player : Bukkit.getOnlinePlayers()) {
        World world = player.getWorld();
        if (world == null) {
          continue;
        }

        if (params.getWorld() != null && !params.getWorld().isBlank() && !WorldIdentity.serialize(world).equals(params.getWorld())) {
          continue;
        }

        Chunk origin = player.getLocation().getChunk();
        for (int dx = -params.getPlayerChunkRadius(); dx <= params.getPlayerChunkRadius(); dx++) {
          for (int dz = -params.getPlayerChunkRadius(); dz <= params.getPlayerChunkRadius(); dz++) {
            double dist = Math.sqrt((dx * dx) + (dz * dz));
            addWeighted(weighted, new ChunkRef(WorldIdentity.serialize(world), origin.getX() + dx, origin.getZ() + dz), 180D / (1D + dist));
          }
        }
      }
    }

    List<Map.Entry<ChunkRef, Double>> sorted = new ArrayList<>(weighted.entrySet());
    sorted.sort(Map.Entry.<ChunkRef, Double>comparingByValue(Comparator.reverseOrder()));

    int limit = Math.max(1, params.getMaxChunks());
    List<ChunkRef> refs = new ArrayList<>();
    for (Map.Entry<ChunkRef, Double> entry : sorted) {
      refs.add(entry.getKey());
      if (refs.size() >= limit) {
        break;
      }
    }

    return refs;
  }

  private void addWeighted(Map<ChunkRef, Double> weighted, ChunkRef ref, double value) {
    if (value <= 0D) {
      return;
    }

    weighted.merge(ref, value, Math::max);
  }

  private PrewarmResult prewarmSync(ChunkRef ref, Params params) {
    World world = WorldIdentity.resolve(ref.world()).orElse(null);
    if (world == null) {
      return new PrewarmResult(false, false);
    }

    boolean wasLoaded = world.isChunkLoaded(ref.x(), ref.z());
    if (!wasLoaded) {
      world.loadChunk(ref.x(), ref.z(), params.isGenerateMissingChunks());
      if (!world.isChunkLoaded(ref.x(), ref.z())) {
        return new PrewarmResult(false, false);
      }
    }

    Chunk chunk = world.getChunkAt(ref.x(), ref.z());
    if (params.isTouchChunkSnapshot()) {
      chunk.getChunkSnapshot(false, false, false);
    }

    // Touching entities helps prime internal chunk/entity structures.
    chunk.getEntities();

    return new PrewarmResult(true, !wasLoaded);
  }

  private void dispatchPrewarm(ChunkRef ref, Params params) {
    World world = WorldIdentity.resolve(ref.world()).orElse(null);
    if (world == null) {
      params.getChunksProcessedAtomic().incrementAndGet();
      return;
    }

    params.getInFlightChunks().incrementAndGet();
    boolean scheduled = J.runChunk(world, ref.x(), ref.z(), () -> {
      try {
        PrewarmResult result = prewarmSync(ref, params);
        if (result != null && result.warmed()) {
          params.getChunksWarmedAtomic().incrementAndGet();
          if (result.newlyLoaded()) {
            params.getChunksLoadedAtomic().incrementAndGet();
          }
        }
      } finally {
        params.getChunksProcessedAtomic().incrementAndGet();
        params.getInFlightChunks().decrementAndGet();
      }
    });

    if (!scheduled) {
      params.getChunksProcessedAtomic().incrementAndGet();
      params.getInFlightChunks().decrementAndGet();
    }
  }

  private record ChunkRef(String world, int x, int z) {
    private static ChunkRef of(Chunk chunk) {
      return new ChunkRef(WorldIdentity.serialize(chunk.getWorld()), chunk.getX(), chunk.getZ());
    }
  }

  private record PrewarmResult(boolean warmed, boolean newlyLoaded) {
  }

  @Builder
  @Data
  @Accessors(chain = true)
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Params implements ActionParams {
    @art.arcane.react.util.project.config.ConfigDoc(value = "World name filter for prewarm critical chunks operations.", impact = "Set a world name to scope actions there, or leave blank to include all worlds.")
    private String world;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum chunks allowed by prewarm critical chunks.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
    private int maxChunks = 40;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Neighbor radius used by prewarm critical chunks (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
    private int neighborRadius = 1;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Includes player chunks in prewarm critical chunks processing.", impact = "Enable this to add those targets to processing; disable it to leave them out.")
    private boolean includePlayerChunks = true;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Player chunk radius used by prewarm critical chunks (chunks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
    private int playerChunkRadius = 1;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether prewarm critical chunks applies generate missing chunks.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean generateMissingChunks = true;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether prewarm critical chunks applies touch chunk snapshot.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean touchChunkSnapshot = true;
    @Builder.Default
    private transient boolean prepared = false;
    @Builder.Default
    private transient Deque<ChunkRef> queue = new ArrayDeque<>();
    @Builder.Default
    private transient int chunksWarmed = 0;
    @Builder.Default
    private transient int chunksLoaded = 0;
    @Builder.Default
    private transient AtomicInteger inFlightChunks = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger chunksWarmedAtomic = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger chunksLoadedAtomic = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger chunksProcessedAtomic = new AtomicInteger(0);

    public Params withWorld(World world) {
      if (world != null) {
        this.world = WorldIdentity.serialize(world);
      }
      return this;
    }
  }
}
