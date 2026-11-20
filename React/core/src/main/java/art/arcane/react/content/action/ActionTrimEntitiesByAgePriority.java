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
import art.arcane.react.api.entity.EntityPriority;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.ActionMessages;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
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
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Trim Entities By Age Priority action. Scores and trims old low-priority entities with safety guards.")
public class ActionTrimEntitiesByAgePriority extends ReactAction<ActionTrimEntitiesByAgePriority.Params> {
  public static final String ID = "action-trim-entities-by-age-priority";
  private static final Set<String> PROTECTED_TYPES = Set.of(
      "PLAYER",
      "ARMOR_STAND",
      "ITEM_FRAME",
      "PAINTING",
      "LEASH_KNOT",
      "MINECART",
      "CHEST_MINECART",
      "COMMAND_BLOCK_MINECART",
      "FURNACE_MINECART",
      "HOPPER_MINECART",
      "SPAWNER_MINECART",
      "TNT_MINECART",
      "ACACIA_BOAT",
      "BAMBOO_RAFT",
      "BIRCH_BOAT",
      "CHERRY_BOAT",
      "DARK_OAK_BOAT",
      "JUNGLE_BOAT",
      "MANGROVE_BOAT",
      "OAK_BOAT",
      "PALE_OAK_BOAT",
      "SPRUCE_BOAT",
      "ACACIA_CHEST_BOAT",
      "BAMBOO_CHEST_RAFT",
      "BIRCH_CHEST_BOAT",
      "CHERRY_CHEST_BOAT",
      "DARK_OAK_CHEST_BOAT",
      "JUNGLE_CHEST_BOAT",
      "MANGROVE_CHEST_BOAT",
      "OAK_CHEST_BOAT",
      "PALE_OAK_CHEST_BOAT",
      "SPRUCE_CHEST_BOAT",
      "FISHING_BOBBER",
      "ITEM_DISPLAY",
      "BLOCK_DISPLAY",
      "TEXT_DISPLAY",
      "INTERACTION"
  );

  public ActionTrimEntitiesByAgePriority() {
    super(ID);
  }

  @Override
  public String getCompletedMessage(ActionTicket<Params> ticket) {
    Params p = ticket.getParams();
    return ReactLanguage.plain(
        ActionMessages.TRIMMED_ENTITIES,
        MessageArgument.untrusted("entities", p.getTrimmed()),
        MessageArgument.untrusted("chunks", p.getChunksProcessed()),
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
      List<ChunkRef> queue = buildQueue(params);
      params.setQueue(new ArrayDeque<>(queue == null ? List.of() : queue));
      params.setTrimmed(0);
      params.setChunksProcessed(0);
      params.getTrimmedAtomic().set(0);
      params.getChunksProcessedAtomic().set(0);
      params.getInFlightChunks().set(0);
      params.getRemainingTrimBudget().set(Math.max(0, params.getMaxTrim()));
      params.setPrepared(true);
      ticket.setWork(0);
      ticket.setTotalWork(Math.max(1, params.getQueue().size()));
    }

    workOnAsync(ticket, params);
  }

  private void workOnAsync(ActionTicket<Params> ticket, Params params) {
    int trimmed = params.getTrimmedAtomic().get();
    params.setTrimmed(trimmed);
    params.setChunksProcessed(params.getChunksProcessedAtomic().get());
    ticket.setCount(trimmed);
    ticket.setWork(Math.min(ticket.getTotalWork(), params.getChunksProcessed()));

    if ((params.getQueue().isEmpty() && params.getInFlightChunks().get() <= 0)
        || (params.getRemainingTrimBudget().get() <= 0 && params.getInFlightChunks().get() <= 0)) {
      ticket.complete();
      return;
    }

    ActionController actions = ReactComponents.actions();
    int chunkBudget = Math.max(1, (actions == null ? 128 : actions.getActionSpeedMultiplier()) / 8);
    int maxInFlight = Math.max(4, chunkBudget * 2);
    int dispatched = 0;
    while (dispatched < chunkBudget
        && !params.getQueue().isEmpty()
        && params.getRemainingTrimBudget().get() > 0
        && params.getInFlightChunks().get() < maxInFlight) {
      ChunkRef next = params.getQueue().pollFirst();
      if (next == null) {
        break;
      }

      dispatchChunkTrim(next, params, ticket);
      dispatched++;
    }

    params.setTrimmed(params.getTrimmedAtomic().get());
    params.setChunksProcessed(params.getChunksProcessedAtomic().get());
    ticket.setCount(params.getTrimmed());
    ticket.setWork(Math.min(ticket.getTotalWork(), params.getChunksProcessed()));

    if ((params.getQueue().isEmpty() && params.getInFlightChunks().get() <= 0)
        || (params.getRemainingTrimBudget().get() <= 0 && params.getInFlightChunks().get() <= 0)) {
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

  private List<ChunkRef> buildQueue(Params params) {
    return buildQueueSync(params);
  }

  private List<ChunkRef> buildQueueSync(Params params) {
    List<ChunkRef> refs = new ArrayList<>();
    ReactPlatform platform = ReactPlatforms.getOrNull();
    ChunkHost chunks = platform == null ? null : platform.chunkHost().orElse(null);
    if (chunks == null) {
      return refs;
    }

    List<Map.Entry<ChunkRef, Double>> weighted = new ArrayList<>();
    for (SampledChunk sampledChunk : chunks.sampledChunks()) {
      String worldKey = sampledChunk.getWorldKey();
      if (worldKey == null) {
        continue;
      }

      if (params.getWorld() != null && !params.getWorld().isBlank() && !worldKey.equals(params.getWorld())) {
        continue;
      }

      if (params.getMinimumEntitiesPerChunk() > 0
          && sampledChunk.optional(SamplerEntities.ID).map(value -> value.get()).orElse(0D)
              < params.getMinimumEntitiesPerChunk()) {
        continue;
      }
      weighted.add(Map.entry(
          new ChunkRef(sampledChunk.getWorldId(), sampledChunk.getChunkX(), sampledChunk.getChunkZ()),
          sampledChunk.totalScore()
      ));
    }

    weighted.sort(Map.Entry.<ChunkRef, Double>comparingByValue(Comparator.reverseOrder()));
    for (Map.Entry<ChunkRef, Double> entry : weighted) {
      refs.add(entry.getKey());
    }

    return refs;
  }

  private void dispatchChunkTrim(ChunkRef ref, Params params, ActionTicket<Params> ticket) {
    ReactWorld world = world(ref.worldId());
    if (world == null) {
      params.getChunksProcessedAtomic().incrementAndGet();
      return;
    }

    int reserved = reserveTrimBudget(params);
    if (reserved <= 0) {
      params.getChunksProcessedAtomic().incrementAndGet();
      return;
    }

    params.getInFlightChunks().incrementAndGet();
    boolean scheduled = J.runChunk(world, ref.chunkX(), ref.chunkZ(), () -> {
      try {
        if (ticket.isDone()) {
          return;
        }
        int removed = trimChunkSync(world, ref, params, reserved);
        if (removed > 0) {
          params.getTrimmedAtomic().addAndGet(removed);
        }

        int unused = reserved - removed;
        if (unused > 0) {
          params.getRemainingTrimBudget().addAndGet(unused);
        }
      } catch (Throwable failure) {
        ticket.fail(failure);
        ReactPlatform platform = ReactPlatforms.getOrNull();
        if (platform != null) {
          platform.reportError("Incident mitigation chunk operation failed", failure);
        }
      } finally {
        params.getChunksProcessedAtomic().incrementAndGet();
        params.getInFlightChunks().decrementAndGet();
      }
    });

    if (!scheduled) {
      params.getRemainingTrimBudget().addAndGet(reserved);
      params.getChunksProcessedAtomic().incrementAndGet();
      params.getInFlightChunks().decrementAndGet();
    }
  }

  private int reserveTrimBudget(Params params) {
    int perChunkCap = Math.max(1, params.getMaxTrimPerChunk());
    AtomicInteger remainingBudget = params.getRemainingTrimBudget();
    while (true) {
      int current = remainingBudget.get();
      if (current <= 0) {
        return 0;
      }

      int reserved = Math.min(perChunkCap, current);
      if (remainingBudget.compareAndSet(current, current - reserved)) {
        return reserved;
      }
    }
  }

  private int trimChunkSync(ReactWorld world, ChunkRef ref, Params params, int remaining) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    EntityHost host = platform == null ? null : platform.entityHost().orElse(null);
    WorldHost worlds = platform == null ? null : platform.worldHost().orElse(null);
    if (host == null || worlds == null || remaining <= 0) {
      return 0;
    }

    int maxByChunk = Math.max(1, params.getMaxTrimPerChunk());
    int budget = Math.min(maxByChunk, remaining);
    if (budget <= 0) {
      return 0;
    }

    PriorityQueue<EntityCandidate> candidates = new PriorityQueue<>(budget, Comparator.comparingDouble(EntityCandidate::score));
    for (GameEntity entity : host.chunkEntities(world, ref.chunkX(), ref.chunkZ())) {
      if (!canTrim(host, worlds, entity, params)) {
        continue;
      }

      double score = removalScore(entity, params);
      if (score <= 0D) {
        continue;
      }

      EntityCandidate candidate = new EntityCandidate(entity, score);
      if (candidates.size() < budget) {
        candidates.offer(candidate);
        continue;
      }

      EntityCandidate weakest = candidates.peek();
      if (weakest != null && candidate.score() > weakest.score()) {
        candidates.poll();
        candidates.offer(candidate);
      }
    }

    if (candidates.isEmpty()) {
      return 0;
    }

    List<EntityCandidate> ordered = new ArrayList<>(candidates);
    ordered.sort(Comparator.comparingDouble(EntityCandidate::score).reversed());
    budget = Math.min(ordered.size(), budget);
    int removed = 0;

    for (int i = 0; i < budget; i++) {
      GameEntity target = ordered.get(i).entity();
      if (target == null || !target.isValid()) {
        continue;
      }

      if (!host.allows(target, ReactOperation.TRIM)) {
        continue;
      }

      host.kill(target, 2 + ThreadLocalRandom.current().nextInt(6));
      removed++;
    }

    return removed;
  }

  private boolean canTrim(EntityHost host, WorldHost worlds, GameEntity entity, Params params) {
    if (entity == null || !entity.isValid() || entity.isPlayer()) {
      return false;
    }

    if (entity.ticksLived() < params.getMinEntityAgeTicks()) {
      return false;
    }

    String type = entity.type();
    if (PROTECTED_TYPES.contains(type)) {
      return false;
    }

    if (host.isProtected(entity, ReactOperation.TRIM)) {
      return false;
    }

    if (host.protectsNamed(entity, params.isProtectNamed())) {
      return false;
    }

    if (params.isProtectTamed() && entity.isTamed()) {
      return false;
    }

    if (params.isProtectVillagers() && "VILLAGER".equals(type)) {
      return false;
    }

    if (params.isProtectBosses() && ("ENDER_DRAGON".equals(type) || "WITHER".equals(type) || "WARDEN".equals(type))) {
      return false;
    }

    boolean item = "ITEM".equals(type);
    if (!params.isAllowItems() && item) {
      return false;
    }

    if (!params.isAllowMonsters() && entity.isMonster()) {
      return false;
    }

    if (!params.isAllowNonLiving() && !(entity.isLiving() || item)) {
      return false;
    }

    if (!params.isProtectNearPlayers()) {
      return true;
    }
    GameLocation location = entity.location();
    return location == null || !worlds.anyPlayerNear(location, params.getPlayerProtectRadius());
  }

  private double removalScore(GameEntity entity, Params params) {
    double priority = ReactEntity.getPriority(entity);
    if (priority <= 0D) {
      priority = EntityPriority.active().getPriority(entity);
    }

    if (priority <= 0D) {
      priority = 1_000D;
    }

    double ageWeight = Math.min(5D, entity.ticksLived() / (double) Math.max(1, params.getMinEntityAgeTicks()));
    double score = ageWeight / Math.max(1D, priority);
    if (entity.isMonster()) {
      score *= 1.15D;
    }

    if ("ITEM".equals(entity.type())) {
      score *= 1.35D;
    }

    return score;
  }

  private static ReactWorld world(UUID worldId) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    WorldHost worlds = platform == null ? null : platform.worldHost().orElse(null);
    return worlds == null || worldId == null ? null : worlds.world(worldId).orElse(null);
  }

  private record EntityCandidate(GameEntity entity, double score) {
  }

  private record ChunkRef(UUID worldId, int chunkX, int chunkZ) {
  }

  @Builder
  @Data
  @Accessors(chain = true)
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Params implements ActionParams {
    @art.arcane.react.util.project.config.ConfigDoc(value = "World name filter for trim entities by age priority operations.", impact = "Set a world name to scope actions there, or leave blank to include all worlds.")
    private String world;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum trim allowed by trim entities by age priority.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
    private int maxTrim = 600;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum trim allowed per chunk in trim entities by age priority.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxTrimPerChunk = 12;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum observed entities in a chunk before trimming; zero includes every observed chunk.", impact = "Set a positive value to restrict trimming to dense chunks.")
    private int minimumEntitiesPerChunk = 0;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum entity age ticks required by trim entities by age priority.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
    private int minEntityAgeTicks = 20 * 60 * 5;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Protects named from trim entities by age priority enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
    private boolean protectNamed = true;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Protects tamed from trim entities by age priority enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
    private boolean protectTamed = true;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Protects villagers from trim entities by age priority enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
    private boolean protectVillagers = true;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Protects bosses from trim entities by age priority enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
    private boolean protectBosses = true;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Protects near players from trim entities by age priority enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
    private boolean protectNearPlayers = true;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Player radius used by trim entities by age priority (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
    private double playerProtectRadius = 24D;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Allows items in trim entities by age priority.", impact = "Enable this to permit the behavior; disable it to block or throttle that path.")
    private boolean allowItems = true;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Allows monsters in trim entities by age priority.", impact = "Enable this to permit the behavior; disable it to block or throttle that path.")
    private boolean allowMonsters = true;
    @Builder.Default
    @art.arcane.react.util.project.config.ConfigDoc(value = "Allows non living in trim entities by age priority.", impact = "Enable this to permit the behavior; disable it to block or throttle that path.")
    private boolean allowNonLiving = false;
    @Builder.Default
    private transient boolean prepared = false;
    @Builder.Default
    private transient Deque<ChunkRef> queue = new ArrayDeque<>();
    @Builder.Default
    private transient int trimmed = 0;
    @Builder.Default
    private transient int chunksProcessed = 0;
    @Builder.Default
    private transient AtomicInteger inFlightChunks = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger trimmedAtomic = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger chunksProcessedAtomic = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger remainingTrimBudget = new AtomicInteger(0);

    public Params withWorld(String worldKey) {
      if (worldKey != null) {
        this.world = worldKey;
      }
      return this;
    }
  }
}
