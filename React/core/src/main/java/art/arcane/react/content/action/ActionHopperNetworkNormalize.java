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
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.content.sampler.SamplerHopperUpdates;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.ActionMessages;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.HopperHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Hopper Network Normalize action. Targets hopper hotspots, merges item entities, and can unload idle hot chunks.")
public class ActionHopperNetworkNormalize extends ReactAction<ActionHopperNetworkNormalize.Params> {
  public static final String ID = "action-hopper-network-normalize";

  public ActionHopperNetworkNormalize() {
    super(ID);
  }

  @Override
  public String getCompletedMessage(ActionTicket<Params> ticket) {
    Params p = ticket.getParams();
    return ReactLanguage.plain(
        ActionMessages.NORMALIZED_HOPPERS,
        MessageArgument.untrusted("hoppers", p.getHoppersNormalized()),
        MessageArgument.untrusted("items", p.getItemsMerged()),
        MessageArgument.untrusted("chunks", p.getChunksUnloaded()),
        MessageArgument.untrusted("duration", Form.duration(ticket.getDuration(), 1))
    );
  }

  @Override
  public void workOn(ActionTicket<Params> ticket) {
    if (ticket.isDone()) {
      return;
    }
    Params params = ticket.getParams();
    if (!params.isPrepared()) {
      List<ChunkTarget> queue = buildQueue(params);
      params.setQueue(new ArrayDeque<>(queue == null ? List.of() : queue));
      params.setHoppersNormalized(0);
      params.setItemsMerged(0);
      params.setChunksUnloaded(0);
      params.getHoppersNormalizedAtomic().set(0);
      params.getItemsMergedAtomic().set(0);
      params.getChunksUnloadedAtomic().set(0);
      params.getProcessedChunks().set(0);
      params.getInFlightChunks().set(0);
      params.setPrepared(true);
      ticket.setWork(0);
      ticket.setTotalWork(Math.max(1, params.getQueue().size()));
    }

    workOnNormalizeAsync(ticket, params);
  }

  private void workOnNormalizeAsync(ActionTicket<Params> ticket, Params params) {
    params.setHoppersNormalized(params.getHoppersNormalizedAtomic().get());
    params.setItemsMerged(params.getItemsMergedAtomic().get());
    params.setChunksUnloaded(params.getChunksUnloadedAtomic().get());
    ticket.setCount(params.getHoppersNormalized());
    ticket.setWork(Math.min(ticket.getTotalWork(), params.getProcessedChunks().get()));

    if (params.getQueue().isEmpty() && params.getInFlightChunks().get() <= 0) {
      ticket.complete();
      return;
    }

    ActionController actions = ReactComponents.actions();
    int chunkBudget = Math.max(1, (actions == null ? 0 : actions.getActionSpeedMultiplier()) / 12);
    int maxInFlight = Math.max(4, chunkBudget * 2);
    int dispatched = 0;
    while (dispatched < chunkBudget
        && !params.getQueue().isEmpty()
        && params.getInFlightChunks().get() < maxInFlight) {
      ChunkTarget next = params.getQueue().pollFirst();
      if (next == null) {
        break;
      }

      dispatchChunkNormalize(next, params, ticket);
      dispatched++;
    }

    params.setHoppersNormalized(params.getHoppersNormalizedAtomic().get());
    params.setItemsMerged(params.getItemsMergedAtomic().get());
    params.setChunksUnloaded(params.getChunksUnloadedAtomic().get());
    ticket.setCount(params.getHoppersNormalized());
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

  private List<ChunkTarget> buildQueue(Params params) {
    return buildQueueSync(params);
  }

  private List<ChunkTarget> buildQueueSync(Params params) {
    List<ChunkTarget> targets = new ArrayList<>();
    if (SamplerHosts.sampler(SamplerHopperUpdates.ID) == null) {
      return targets;
    }

    HopperHost hoppers = ReactPlatforms.get().hopperHost().orElse(null);
    if (hoppers == null) {
      return targets;
    }

    for (HopperHost.SampledChunkRate sampledChunk : hoppers.sampledChunks(SamplerHopperUpdates.ID)) {
      String worldKey = sampledChunk.worldKey();
      if (worldKey == null) {
        continue;
      }

      if (params.getWorld() != null && !params.getWorld().isBlank() && !worldKey.equals(params.getWorld())) {
        continue;
      }

      double rate = sampledChunk.rate();
      if (rate < params.getMinimumHopperUpdatesPerChunk()) {
        continue;
      }

      targets.add(new ChunkTarget(
          worldKey,
          sampledChunk.chunkX(),
          sampledChunk.chunkZ(),
          rate
      ));
    }

    targets.sort(Comparator.comparingDouble(ChunkTarget::rate).reversed());
    int limit = Math.max(1, params.getMaxChunks());
    if (targets.size() > limit) {
      targets = new ArrayList<>(targets.subList(0, limit));
    }

    return targets;
  }

  private static ReactWorld resolveWorld(String worldKey) {
    WorldHost worlds = ReactPlatforms.get().worldHost().orElse(null);
    if (worlds == null || worldKey == null) {
      return null;
    }
    for (ReactWorld world : worlds.worlds()) {
      if (worldKey.equals(worlds.worldKey(world))) {
        return world;
      }
    }
    return null;
  }

  private void dispatchChunkNormalize(ChunkTarget target, Params params, ActionTicket<Params> ticket) {
    ReactWorld world = resolveWorld(target.world());
    if (world == null) {
      params.getProcessedChunks().incrementAndGet();
      return;
    }

    params.getInFlightChunks().incrementAndGet();
    boolean scheduled = J.runChunk(world.handle(), target.x(), target.z(), () -> {
      try {
        if (ticket.isDone()) {
          return;
        }
        NormalizeResult result = normalizeChunkSync(target, params);
        if (result != null) {
          if (result.hoppersNormalized() > 0) {
            params.getHoppersNormalizedAtomic().addAndGet(result.hoppersNormalized());
          }
          if (result.itemsMerged() > 0) {
            params.getItemsMergedAtomic().addAndGet(result.itemsMerged());
          }
          if (result.unloadedChunk()) {
            params.getChunksUnloadedAtomic().incrementAndGet();
          }
        }
      } catch (Throwable failure) {
        ticket.fail(failure);
        ReactPlatforms.get().reportError("Incident mitigation chunk operation failed", failure);
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

  private NormalizeResult normalizeChunkSync(ChunkTarget target, Params params) {
    ReactPlatform platform = ReactPlatforms.get();
    ReactWorld world = resolveWorld(target.world());
    ChunkHost chunks = platform.chunkHost().orElse(null);
    HopperHost hoppers = platform.hopperHost().orElse(null);
    WorldHost worlds = platform.worldHost().orElse(null);
    if (world == null || chunks == null || hoppers == null || worlds == null
        || !chunks.isLoaded(world.id(), target.x(), target.z())) {
      return new NormalizeResult(0, 0, false);
    }

    GameLocation center = new GameLocation(world.id(), (target.x() << 4) + 8.0, hoppers.minBuildHeight(world.id()), (target.z() << 4) + 8.0);
    if (nearPlayer(worlds, center, params.getUnsafePlayerRadius())) {
      return new NormalizeResult(0, 0, false);
    }

    int hoppersNormalized = 0;
    int mergedEntities = 0;

    Object chunk = hoppers.chunk(world.id(), target.x(), target.z());
    List<HopperHost.HopperBlock> chunkHoppers = chunk == null ? List.of() : hoppers.hoppers(chunk);
    for (HopperHost.HopperBlock hopper : chunkHoppers) {
      hoppersNormalized++;
      int remainingMergeBudget = Math.max(0, params.getMaxMergedItemEntitiesPerChunk() - mergedEntities);
      if (remainingMergeBudget <= 0) {
        break;
      }

      mergedEntities += hoppers.mergeNearbyItems(world.id(), hopper.x() + 0.5, hopper.y() + 0.5, hopper.z() + 0.5,
          params.getItemMergeRadius(), remainingMergeBudget);
    }

    boolean unloaded = false;
    if (params.isUnloadIdleHotChunks()
        && target.rate() >= params.getMinimumHopperUpdatesPerChunk() * 1.5D
        && chunks.isLoaded(world.id(), target.x(), target.z())
        && !nearPlayer(worlds, center, params.getUnsafePlayerRadius())) {
      unloaded = hoppers.unloadChunk(world.id(), target.x(), target.z());
    }

    return new NormalizeResult(hoppersNormalized, mergedEntities, unloaded);
  }

  private static boolean nearPlayer(WorldHost worlds, GameLocation center, double radius) {
    return radius > 0 && worlds.anyPlayerNear(center, radius);
  }

  private record ChunkTarget(String world, int x, int z, double rate) {
  }

  private record NormalizeResult(int hoppersNormalized, int itemsMerged,
                                 boolean unloadedChunk) {
  }

  @Builder
  @Data
  @Accessors(chain = true)
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Params implements ActionParams {
    @art.arcane.react.util.project.config.ConfigDoc(value = "World name filter for hopper network normalize operations.", impact = "Set a world name to scope actions there, or leave blank to include all worlds.")
    private String world;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum chunks allowed by hopper network normalize.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
    private int maxChunks = 20;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum hopper updates per chunk required by hopper network normalize.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
    private double minimumHopperUpdatesPerChunk = 25D;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Unsafe player radius used by hopper network normalize (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
    private double unsafePlayerRadius = 24D;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Item merge radius used by hopper network normalize (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
    private double itemMergeRadius = 2D;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum merged item entities allowed per chunk in hopper network normalize.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxMergedItemEntitiesPerChunk = 48;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether hopper network normalize applies unload idle hot chunks.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
    private boolean unloadIdleHotChunks = true;
    @Builder.Default
    private transient boolean prepared = false;
    @Builder.Default
    private transient Deque<ChunkTarget> queue = new ArrayDeque<>();
    @Builder.Default
    private transient int hoppersNormalized = 0;
    @Builder.Default
    private transient int itemsMerged = 0;
    @Builder.Default
    private transient int chunksUnloaded = 0;
    @Builder.Default
    private transient AtomicInteger inFlightChunks = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger processedChunks = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger hoppersNormalizedAtomic = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger itemsMergedAtomic = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger chunksUnloadedAtomic = new AtomicInteger(0);

    public Params withWorld(String worldKey) {
      if (worldKey != null) {
        this.world = worldKey;
      }
      return this;
    }
  }
}
