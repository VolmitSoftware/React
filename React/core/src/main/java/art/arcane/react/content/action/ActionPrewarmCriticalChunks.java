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

import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.action.ReactAction;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.ActionMessages;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.PlayerPosition;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Prewarm Critical Chunks action. Preloads high-risk chunks and neighbors to reduce stutter spikes.")
public class ActionPrewarmCriticalChunks extends ReactAction<ActionPrewarmCriticalChunks.Params> {
  public static final String ID = "action-prewarm-critical-chunks";
  private static final int MAX_IN_FLIGHT = 32;

  public ActionPrewarmCriticalChunks() {
    super(ID);
  }

  @Override
  public String getCompletedMessage(ActionTicket<Params> ticket) {
    Params p = ticket.getParams();
    return ReactLanguage.plain(
        ActionMessages.PREWARMED_CHUNKS,
        MessageArgument.untrusted("warmed", p.getChunksWarmed()),
        MessageArgument.untrusted("loaded", p.getChunksLoaded()),
        MessageArgument.untrusted("duration", Form.duration(ticket.getDuration(), 1))
    );
  }

  @Override
  public void workOn(ActionTicket<Params> ticket) {
    if (ticket.isDone()) {
      return;
    }
    if (!isEnabled()) {
      ticket.fail(new IllegalStateException("Prewarm action was disabled"));
      return;
    }
    Params params = ticket.getParams();
    if (!params.isPrepared()) {
      WorldHost players = ChunkActionHosts.worlds();
      if (params.isIncludePlayerChunks() && players != null && !players.isPlayerIndexReady()) {
        return;
      }
      List<ChunkRef> queue = buildQueue(params);
      synchronized (ticket) {
        if (ticket.isDone()) {
          return;
        }
        params.setQueue(new ArrayDeque<>(queue == null ? List.of() : queue));
        params.setChunksWarmed(0);
        params.setChunksLoaded(0);
        params.getChunksWarmedAtomic().set(0);
        params.getChunksLoadedAtomic().set(0);
        params.getChunksProcessedAtomic().set(0);
        params.getInFlightChunks().set(0);
        params.setPrepared(true);
        ticket.onTerminal(ignored -> cancelPending(params));
        ticket.setWork(0);
        ticket.setTotalWork(Math.max(1, params.getQueue().size()));
      }
    }

    workOnAsync(ticket, params);
  }

  private void workOnAsync(ActionTicket<Params> ticket, Params params) {
    if (publishProgress(ticket, params)) {
      return;
    }

    int chunkBudget = Math.max(1, Math.min(MAX_IN_FLIGHT / 2, ChunkActionHosts.actionSpeed() / 8));
    int maxInFlight = Math.max(4, chunkBudget * 2);
    int dispatched = 0;
    while (!ticket.isDone() && dispatched < chunkBudget && !params.getQueue().isEmpty() && params.getInFlightChunks().get() < maxInFlight) {
      ChunkRef target = params.getQueue().pollFirst();
      if (target == null) {
        break;
      }

      dispatchPrewarm(target, ticket);
      dispatched++;
    }

    publishProgress(ticket, params);
  }

  private boolean publishProgress(ActionTicket<Params> ticket, Params params) {
    boolean complete;
    synchronized (ticket) {
      if (ticket.isDone()) {
        return true;
      }
      params.setChunksWarmed(params.getChunksWarmedAtomic().get());
      params.setChunksLoaded(params.getChunksLoadedAtomic().get());
      ticket.setCount(params.getChunksWarmed());
      ticket.setWork(Math.min(ticket.getTotalWork(), params.getChunksProcessedAtomic().get()));
      complete = params.getQueue().isEmpty() && params.getInFlightChunks().get() <= 0;
    }
    if (complete) {
      ticket.complete();
    }
    return complete;
  }

  @Override
  public Params getDefaultParams() {
    return Params.builder().build();
  }

  @Override
  public void onInit() {

  }

  private List<ChunkRef> buildQueue(Params params) {
    Map<ChunkRef, Double> weighted = new HashMap<>();
    ChunkHost chunks = ChunkActionHosts.chunks();
    if (chunks != null) {
      for (SampledChunk sampledChunk : chunks.sampledChunks()) {
        String worldKey = sampledChunk.getWorldKey();
        if (worldKey == null) {
          continue;
        }

        if (params.getWorld() != null && !params.getWorld().isBlank() && !worldKey.equals(params.getWorld())) {
          continue;
        }

        double score = sampledChunk.totalScore();
        if (score <= 0D) {
          continue;
        }

        int chunkX = sampledChunk.getChunkX();
        int chunkZ = sampledChunk.getChunkZ();
        addWeighted(weighted, new ChunkRef(worldKey, chunkX, chunkZ), score);
        for (int dx = -params.getNeighborRadius(); dx <= params.getNeighborRadius(); dx++) {
          for (int dz = -params.getNeighborRadius(); dz <= params.getNeighborRadius(); dz++) {
            if (dx == 0 && dz == 0) {
              continue;
            }

            double dist = Math.sqrt((dx * dx) + (dz * dz));
            addWeighted(weighted, new ChunkRef(worldKey, chunkX + dx, chunkZ + dz), score * (0.7D / (1D + dist)));
          }
        }
      }
    }

    if (params.isIncludePlayerChunks()) {
      WorldHost players = ChunkActionHosts.worlds();
      List<PlayerPosition> positions = players == null ? null : players.indexedPlayers().orElse(null);
      if (positions == null || chunks == null) {
        throw new IllegalStateException("Player index unavailable for chunk prewarming");
      }
      for (PlayerPosition player : positions) {
        ReactWorld world = chunks.world(player.worldId()).orElse(null);
        if (world == null) {
          continue;
        }
        String worldKey = chunks.worldKey(world);
        if (params.getWorld() != null && !params.getWorld().isBlank() && !worldKey.equals(params.getWorld())) {
          continue;
        }
        int chunkX = ((int) Math.floor(player.x())) >> 4;
        int chunkZ = ((int) Math.floor(player.z())) >> 4;
        for (int dx = -params.getPlayerChunkRadius(); dx <= params.getPlayerChunkRadius(); dx++) {
          for (int dz = -params.getPlayerChunkRadius(); dz <= params.getPlayerChunkRadius(); dz++) {
            double dist = Math.sqrt((dx * dx) + (dz * dz));
            addWeighted(weighted, new ChunkRef(worldKey, chunkX + dx, chunkZ + dz), 180D / (1D + dist));
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

  private void dispatchPrewarm(ChunkRef ref, ActionTicket<Params> ticket) {
    Params params = ticket.getParams();
    ChunkHost chunks = ChunkActionHosts.chunks();
    ReactWorld world = chunks == null ? null : chunks.world(ref.world()).orElse(null);
    if (world == null) {
      params.getChunksProcessedAtomic().incrementAndGet();
      return;
    }
    PrewarmFlight flight = new PrewarmFlight(ticket, chunks, new ChunkTarget(world, ref));
    params.getInFlightChunks().incrementAndGet();
    params.getFlights().add(flight);
    if (ticket.isDone()) {
      flight.cancel();
      return;
    }
    try {
      if (!J.runChunk(world.handle(), ref.x(), ref.z(), flight::start)) {
        flight.fail(new RejectedExecutionException("Prewarm owner scheduling rejected"));
      }
    } catch (Throwable failure) {
      flight.fail(failure);
    }
  }

  private void cancelPending(Params params) {
    for (PrewarmFlight flight : params.getFlights()) {
      flight.cancel();
    }
  }

  private record ChunkRef(String world, int x, int z) {
  }

  private record ChunkTarget(ReactWorld world, ChunkRef ref) {
  }

  private static final class PrewarmFlight {
    private final ActionTicket<Params> ticket;
    private final ChunkHost chunks;
    private final ChunkTarget target;
    private final AtomicBoolean finished = new AtomicBoolean();
    private volatile CompletableFuture<Object> loading;
    private boolean wasLoaded;

    private PrewarmFlight(ActionTicket<Params> ticket, ChunkHost chunks, ChunkTarget target) {
      this.ticket = ticket;
      this.chunks = chunks;
      this.target = target;
    }

    private boolean sameWorld() {
      ReactWorld current = chunks.world(target.ref().world()).orElse(null);
      return current != null && current.handle() == target.world().handle();
    }

    private boolean active() {
      if (finished.get() || ticket.isDone()) {
        retire();
        return false;
      }
      if (!ticket.getAction().isEnabled()) {
        fail(new IllegalStateException("Prewarm action was disabled"));
        return false;
      }
      return true;
    }

    private void start() {
      if (!active()) {
        return;
      }
      try {
        if (!sameWorld()) {
          retire();
          return;
        }
        wasLoaded = chunks.isLoaded(target.world(), target.ref().x(), target.ref().z());
        if (wasLoaded) {
          warm(chunks.loadedChunk(target.world(), target.ref().x(), target.ref().z()));
          return;
        }
        loading = chunks.load(target.world(), target.ref().x(), target.ref().z(),
            ticket.getParams().isGenerateMissingChunks());
        if (!active()) {
          loading.cancel(false);
          return;
        }
        loading.whenComplete(this::loaded);
      } catch (Throwable failure) {
        fail(failure);
      }
    }

    private void loaded(Object chunk, Throwable failure) {
      if (!active()) {
        return;
      }
      if (failure != null) {
        fail(failure);
        return;
      }
      if (chunk == null) {
        retire();
        return;
      }
      try {
        if (chunks.ownsChunk(target.world(), target.ref().x(), target.ref().z())) {
          warm(chunk);
        } else if (!J.runChunk(target.world().handle(), target.ref().x(), target.ref().z(), () -> warm(chunk))) {
          fail(new RejectedExecutionException("Prewarm completion scheduling rejected"));
        }
      } catch (Throwable schedulingFailure) {
        fail(schedulingFailure);
      }
    }

    private void warm(Object chunk) {
      if (!active()) {
        return;
      }
      try {
        if (chunk == null || !sameWorld() || !chunks.isLoaded(target.world(), target.ref().x(), target.ref().z())) {
          return;
        }
        Params params = ticket.getParams();
        chunks.warm(chunk, params.isTouchChunkSnapshot());
        synchronized (ticket) {
          if (!ticket.isDone()) {
            params.getChunksWarmedAtomic().incrementAndGet();
            if (!wasLoaded) {
              params.getChunksLoadedAtomic().incrementAndGet();
            }
          }
        }
      } catch (Throwable failure) {
        fail(failure);
      } finally {
        retire();
      }
    }

    private void fail(Throwable failure) {
      if (!ticket.isDone()) {
        ticket.fail(failure);
        ReactComponents.reportError("Chunk prewarm failed for " + target.ref().world() + " "
            + target.ref().x() + "," + target.ref().z(), failure);
      }
      retire();
    }

    private void cancel() {
      retire();
      CompletableFuture<Object> future = loading;
      if (future != null) {
        future.cancel(false);
      }
    }

    private void retire() {
      if (finished.compareAndSet(false, true)) {
        Params params = ticket.getParams();
        params.getFlights().remove(this);
        params.getChunksProcessedAtomic().incrementAndGet();
        params.getInFlightChunks().decrementAndGet();
      }
    }
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
    private transient Set<PrewarmFlight> flights = ConcurrentHashMap.newKeySet();
    @Builder.Default
    private transient AtomicInteger chunksWarmedAtomic = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger chunksLoadedAtomic = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger chunksProcessedAtomic = new AtomicInteger(0);

    public Params withWorld(String worldKey) {
      if (worldKey != null) {
        this.world = worldKey;
      }
      return this;
    }
  }
}
