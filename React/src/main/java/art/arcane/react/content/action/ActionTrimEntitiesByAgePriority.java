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
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.model.ReactEntity;
import art.arcane.volmlib.util.format.Form;
import art.arcane.react.util.scheduling.J;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.config.ConfigDescription("Configuration for Trim Entities By Age Priority action. Scores and trims old low-priority entities with safety guards.")
public class ActionTrimEntitiesByAgePriority extends ReactAction<ActionTrimEntitiesByAgePriority.Params> {
    public static final String ID = "action-trim-entities-by-age-priority";
    public static final String SHORT = "ateap";
    private static final Set<EntityType> PROTECTED_TYPES = EnumSet.of(
            EntityType.PLAYER,
            EntityType.ARMOR_STAND,
            EntityType.ITEM_FRAME,
            EntityType.PAINTING,
            EntityType.LEASH_HITCH,
            EntityType.MINECART,
            EntityType.MINECART_CHEST,
            EntityType.MINECART_COMMAND,
            EntityType.MINECART_FURNACE,
            EntityType.MINECART_HOPPER,
            EntityType.MINECART_MOB_SPAWNER,
            EntityType.MINECART_TNT,
            EntityType.BOAT,
            EntityType.CHEST_BOAT,
            EntityType.FISHING_HOOK,
            EntityType.ITEM_DISPLAY,
            EntityType.BLOCK_DISPLAY,
            EntityType.TEXT_DISPLAY,
            EntityType.INTERACTION
    );

    public ActionTrimEntitiesByAgePriority() {
        super(ID);
    }

    @Override
    public String getCompletedMessage(ActionTicket<Params> ticket) {
        Params p = ticket.getParams();
        return "Trimmed " + p.getTrimmed() + " entities across " + p.getChunksProcessed() + " chunks in " + Form.duration(ticket.getDuration(), 1);
    }

    @Override
    public void workOn(ActionTicket<Params> ticket) {
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

        if (J.isFoliaThreading()) {
            workOnFolia(ticket, params);
            return;
        }

        if (params.getQueue().isEmpty() || params.getTrimmed() >= params.getMaxTrim()) {
            ticket.setCount(params.getTrimmed());
            ticket.complete();
            return;
        }

        int chunkBudget = J.isFoliaThreading()
                ? 1
                : Math.max(1, React.controller(ActionController.class).getActionSpeedMultiplier() / 10);
        int worked = 0;
        while (worked < chunkBudget && !params.getQueue().isEmpty() && params.getTrimmed() < params.getMaxTrim()) {
            ChunkRef next = params.getQueue().pollFirst();
            if (next == null) {
                break;
            }
            int remaining = Math.max(0, params.getMaxTrim() - params.getTrimmed());
            int removed = trimChunk(next, params, remaining);
            params.setTrimmed(params.getTrimmed() + removed);
            params.setChunksProcessed(params.getChunksProcessed() + 1);
            worked++;
        }

        ticket.setWork(Math.min(ticket.getTotalWork(), ticket.getWork() + worked));
        ticket.setCount(params.getTrimmed());

        if (params.getQueue().isEmpty() || params.getTrimmed() >= params.getMaxTrim()) {
            ticket.complete();
        }
    }

    private void workOnFolia(ActionTicket<Params> ticket, Params params) {
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

        int chunkBudget = Math.max(1, React.controller(ActionController.class).getActionSpeedMultiplier() / 8);
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

            dispatchChunkTrim(next, params);
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
        if (J.isFoliaThreading()) {
            return buildQueueSync(params);
        }

        return J.sResult(() -> buildQueueSync(params));
    }

    private List<ChunkRef> buildQueueSync(Params params) {
        List<ChunkRef> refs = new ArrayList<>();
        ObserverController observer = React.controller(ObserverController.class);
        if (observer == null || observer.getSampled() == null) {
            return refs;
        }

        List<Map.Entry<ChunkRef, Double>> weighted = new ArrayList<>();
        for (var sampledWorld : observer.getSampled().getWorlds().values()) {
            World world = sampledWorld.getWorld();
            if (world == null) {
                continue;
            }

            if (params.getWorld() != null && !params.getWorld().isBlank() && !world.getName().equalsIgnoreCase(params.getWorld())) {
                continue;
            }

            for (var sampledChunk : sampledWorld.getChunks().values()) {
                Chunk chunk = sampledChunk.getChunk();
                if (chunk == null || chunk.getWorld() == null) {
                    continue;
                }

                weighted.add(Map.entry(ChunkRef.of(chunk), sampledChunk.totalScore()));
            }
        }

        weighted.sort(Map.Entry.<ChunkRef, Double>comparingByValue(Comparator.reverseOrder()));
        for (Map.Entry<ChunkRef, Double> entry : weighted) {
            refs.add(entry.getKey());
        }

        return refs;
    }

    private int trimChunk(ChunkRef ref, Params params, int remaining) {
        if (!J.isFoliaThreading()) {
            return J.sResult(() -> trimChunkSync(ref, params, remaining));
        }

        World world = Bukkit.getWorld(ref.world());
        if (world == null) {
            return 0;
        }

        return J.runChunkResult(world, ref.x(), ref.z(), () -> trimChunkSync(ref, params, remaining), 0);
    }

    private void dispatchChunkTrim(ChunkRef ref, Params params) {
        World world = Bukkit.getWorld(ref.world());
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
        boolean scheduled = J.runChunk(world, ref.x(), ref.z(), () -> {
            try {
                int removed = trimChunkSync(ref, params, reserved);
                if (removed > 0) {
                    params.getTrimmedAtomic().addAndGet(removed);
                }

                int unused = reserved - removed;
                if (unused > 0) {
                    params.getRemainingTrimBudget().addAndGet(unused);
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

    private int trimChunkSync(ChunkRef ref, Params params, int remaining) {
        World world = Bukkit.getWorld(ref.world());
        if (world == null || !world.isChunkLoaded(ref.x(), ref.z()) || remaining <= 0) {
            return 0;
        }

        Chunk chunk = world.getChunkAt(ref.x(), ref.z());
        int maxByChunk = Math.max(1, params.getMaxTrimPerChunk());
        int budget = Math.min(maxByChunk, remaining);
        if (budget <= 0) {
            return 0;
        }

        PriorityQueue<EntityCandidate> candidates = new PriorityQueue<>(budget, Comparator.comparingDouble(EntityCandidate::score));
        for (Entity entity : chunk.getEntities()) {
            if (!canTrim(entity, params)) {
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
            Entity target = ordered.get(i).entity();
            if (target == null || target.isDead()) {
                continue;
            }

            React.kill(target, 2 + ThreadLocalRandom.current().nextInt(6));
            removed++;
        }

        return removed;
    }

    private boolean canTrim(Entity entity, Params params) {
        if (entity == null || entity.isDead() || entity instanceof Player) {
            return false;
        }

        if (entity.getTicksLived() < params.getMinEntityAgeTicks()) {
            return false;
        }

        if (PROTECTED_TYPES.contains(entity.getType())) {
            return false;
        }

        if (params.isProtectNamed() && entity.getCustomName() != null && !entity.getCustomName().isBlank()) {
            return false;
        }

        if (params.isProtectTamed() && entity instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }

        if (params.isProtectVillagers() && entity instanceof Villager) {
            return false;
        }

        if (params.isProtectBosses() && (entity instanceof EnderDragon || entity instanceof Wither || entity instanceof Warden)) {
            return false;
        }

        if (!params.isAllowItems() && entity instanceof Item) {
            return false;
        }

        if (!params.isAllowMonsters() && entity instanceof Monster) {
            return false;
        }

        if (!params.isAllowNonLiving() && !(entity instanceof LivingEntity || entity instanceof Item)) {
            return false;
        }

        return !params.isProtectNearPlayers() || !React.hasNearbyPlayer(entity.getLocation(), params.getPlayerProtectRadius());
    }

    private double removalScore(Entity entity, Params params) {
        double priority = ReactEntity.getPriority(entity);
        if (priority <= 0D) {
            priority = ReactConfiguration.get().getPriority().getPriority(entity);
        }

        if (priority <= 0D) {
            priority = 1_000D;
        }

        double ageWeight = Math.min(5D, entity.getTicksLived() / (double) Math.max(1, params.getMinEntityAgeTicks()));
        double score = ageWeight / Math.max(1D, priority);
        if (entity instanceof Monster) {
            score *= 1.15D;
        }

        if (entity instanceof Item) {
            score *= 1.35D;
        }

        return score;
    }

    private record EntityCandidate(Entity entity, double score) {
    }

    private record ChunkRef(String world, int x, int z) {
        private static ChunkRef of(Chunk chunk) {
            return new ChunkRef(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        }
    }

    @Builder
    @Data
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Params implements ActionParams {
        @art.arcane.react.util.config.ConfigDoc(value = "World name filter for trim entities by age priority operations.", impact = "Set a world name to scope actions there, or leave blank to include all worlds.")
        private String world;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Maximum trim allowed by trim entities by age priority.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
        private int maxTrim = 600;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Maximum trim allowed per chunk in trim entities by age priority.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
        private int maxTrimPerChunk = 12;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Minimum entity age ticks required by trim entities by age priority.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
        private int minEntityAgeTicks = 20 * 60 * 5;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Protects named from trim entities by age priority enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
        private boolean protectNamed = true;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Protects tamed from trim entities by age priority enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
        private boolean protectTamed = true;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Protects villagers from trim entities by age priority enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
        private boolean protectVillagers = true;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Protects bosses from trim entities by age priority enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
        private boolean protectBosses = true;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Protects near players from trim entities by age priority enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
        private boolean protectNearPlayers = true;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Player radius used by trim entities by age priority (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
        private double playerProtectRadius = 24D;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Allows items in trim entities by age priority.", impact = "Enable this to permit the behavior; disable it to block or throttle that path.")
        private boolean allowItems = true;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Allows monsters in trim entities by age priority.", impact = "Enable this to permit the behavior; disable it to block or throttle that path.")
        private boolean allowMonsters = true;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Allows non living in trim entities by age priority.", impact = "Enable this to permit the behavior; disable it to block or throttle that path.")
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

        public Params withWorld(World world) {
            if (world != null) {
                this.world = world.getName();
            }
            return this;
        }
    }
}
