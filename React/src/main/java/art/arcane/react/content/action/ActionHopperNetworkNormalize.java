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
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerHopperUpdates;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.volmlib.util.format.Form;
import art.arcane.react.util.scheduling.J;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

@art.arcane.react.util.config.ConfigDescription("Configuration for Hopper Network Normalize action. Targets hopper hotspots, merges item entities, and can unload idle hot chunks.")
public class ActionHopperNetworkNormalize extends ReactAction<ActionHopperNetworkNormalize.Params> {
    public static final String ID = "action-hopper-network-normalize";
    public static final String SHORT = "ahnn";

    public ActionHopperNetworkNormalize() {
        super(ID);
    }

    @Override
    public String getCompletedMessage(ActionTicket<Params> ticket) {
        Params p = ticket.getParams();
        return "Normalized " + p.getHoppersNormalized() + " hoppers, merged " + p.getItemsMerged() + " item entities, unloaded " + p.getChunksUnloaded() + " chunks in " + Form.duration(ticket.getDuration(), 1);
    }

    @Override
    public void workOn(ActionTicket<Params> ticket) {
        Params params = ticket.getParams();
        if (!params.isPrepared()) {
            List<ChunkTarget> queue = buildQueue(params);
            params.setQueue(new ArrayDeque<>(queue == null ? List.of() : queue));
            params.setPrepared(true);
            ticket.setWork(0);
            ticket.setTotalWork(Math.max(1, params.getQueue().size()));
        }

        if (params.getQueue().isEmpty()) {
            ticket.setCount(params.getHoppersNormalized());
            ticket.complete();
            return;
        }

        int chunkBudget = J.isFoliaThreading()
                ? 1
                : Math.max(1, React.controller(ActionController.class).getActionSpeedMultiplier() / 12);
        int worked = 0;
        while (worked < chunkBudget && !params.getQueue().isEmpty()) {
            ChunkTarget target = params.getQueue().pollFirst();
            if (target == null) {
                break;
            }
            NormalizeResult result = normalizeChunk(target, params);
            if (result != null) {
                params.setHoppersNormalized(params.getHoppersNormalized() + result.hoppersNormalized());
                params.setItemsMerged(params.getItemsMerged() + result.itemsMerged());
                if (result.unloadedChunk()) {
                    params.setChunksUnloaded(params.getChunksUnloaded() + 1);
                }
            }

            worked++;
        }

        ticket.setWork(Math.min(ticket.getTotalWork(), ticket.getWork() + worked));
        ticket.setCount(params.getHoppersNormalized());

        if (params.getQueue().isEmpty()) {
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
        if (J.isFoliaThreading()) {
            return buildQueueSync(params);
        }

        return J.sResult(() -> buildQueueSync(params));
    }

    private List<ChunkTarget> buildQueueSync(Params params) {
        List<ChunkTarget> targets = new ArrayList<>();
        Sampler hopperSampler = React.sampler(SamplerHopperUpdates.ID);
        if (hopperSampler == null) {
            return targets;
        }

        ObserverController observer = React.controller(ObserverController.class);
        if (observer == null || observer.getSampled() == null) {
            return targets;
        }

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

                double rate = hopperSampler.sample(chunk);
                if (rate < params.getMinimumHopperUpdatesPerChunk()) {
                    continue;
                }

                targets.add(new ChunkTarget(world.getName(), chunk.getX(), chunk.getZ(), rate));
            }
        }

        targets.sort(Comparator.comparingDouble(ChunkTarget::rate).reversed());
        int limit = Math.max(1, params.getMaxChunks());
        if (targets.size() > limit) {
            targets = new ArrayList<>(targets.subList(0, limit));
        }

        return targets;
    }

    private NormalizeResult normalizeChunk(ChunkTarget target, Params params) {
        if (!J.isFoliaThreading()) {
            return J.sResult(() -> normalizeChunkSync(target, params));
        }

        World world = Bukkit.getWorld(target.world());
        if (world == null) {
            return new NormalizeResult(0, 0, false);
        }

        return J.runChunkResult(world, target.x(), target.z(), () -> normalizeChunkSync(target, params), new NormalizeResult(0, 0, false));
    }

    private NormalizeResult normalizeChunkSync(ChunkTarget target, Params params) {
        World world = Bukkit.getWorld(target.world());
        if (world == null || !world.isChunkLoaded(target.x(), target.z())) {
            return new NormalizeResult(0, 0, false);
        }

        Location center = new Location(world, (target.x() << 4) + 8.0, world.getMinHeight(), (target.z() << 4) + 8.0);
        if (React.hasNearbyPlayer(center, params.getUnsafePlayerRadius())) {
            return new NormalizeResult(0, 0, false);
        }

        Chunk chunk = world.getChunkAt(target.x(), target.z());
        int hoppersNormalized = 0;
        int mergedEntities = 0;

        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof Hopper hopper)) {
                continue;
            }

            hoppersNormalized++;
            int remainingMergeBudget = Math.max(0, params.getMaxMergedItemEntitiesPerChunk() - mergedEntities);
            if (remainingMergeBudget <= 0) {
                break;
            }

            Location location = hopper.getLocation().add(0.5, 0.5, 0.5);
            mergedEntities += mergeNearbyItems(world, location, params.getItemMergeRadius(), remainingMergeBudget);
        }

        boolean unloaded = false;
        if (params.isUnloadIdleHotChunks()
                && target.rate() >= params.getMinimumHopperUpdatesPerChunk() * 1.5D
                && world.isChunkLoaded(target.x(), target.z())
                && !React.hasNearbyPlayer(center, params.getUnsafePlayerRadius())) {
            chunk.unload(true);
            unloaded = !world.isChunkLoaded(target.x(), target.z());
        }

        return new NormalizeResult(hoppersNormalized, mergedEntities, unloaded);
    }

    private int mergeNearbyItems(World world, Location center, double radius, int maxMergedEntities) {
        if (maxMergedEntities <= 0) {
            return 0;
        }

        List<Item> items = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius, e -> e instanceof Item)) {
            Item item = (Item) entity;
            if (!item.isDead() && item.isValid() && item.getItemStack().getAmount() > 0) {
                items.add(item);
            }
        }

        int merged = 0;
        for (int i = 0; i < items.size(); i++) {
            Item base = items.get(i);
            if (!base.isValid() || base.isDead()) {
                continue;
            }

            ItemStack baseStack = base.getItemStack();
            for (int j = i + 1; j < items.size(); j++) {
                if (merged >= maxMergedEntities) {
                    return merged;
                }

                Item other = items.get(j);
                if (!other.isValid() || other.isDead()) {
                    continue;
                }

                ItemStack otherStack = other.getItemStack();
                if (!baseStack.isSimilar(otherStack)) {
                    continue;
                }

                int room = baseStack.getMaxStackSize() - baseStack.getAmount();
                if (room <= 0) {
                    break;
                }

                int transfer = Math.min(room, otherStack.getAmount());
                if (transfer <= 0) {
                    continue;
                }

                baseStack.setAmount(baseStack.getAmount() + transfer);
                base.setItemStack(baseStack);
                otherStack.setAmount(otherStack.getAmount() - transfer);
                if (otherStack.getAmount() <= 0) {
                    other.remove();
                    merged++;
                } else {
                    other.setItemStack(otherStack);
                }
            }
        }

        return merged;
    }

    private record ChunkTarget(String world, int x, int z, double rate) {
    }

    private record NormalizeResult(int hoppersNormalized, int itemsMerged, boolean unloadedChunk) {
    }

    @Builder
    @Data
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Params implements ActionParams {
        @art.arcane.react.util.config.ConfigDoc(value = "World name filter for hopper network normalize operations.", impact = "Set a world name to scope actions there, or leave blank to include all worlds.")
        private String world;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Maximum chunks allowed by hopper network normalize.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
        private int maxChunks = 20;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Minimum hopper updates per chunk required by hopper network normalize.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
        private double minimumHopperUpdatesPerChunk = 25D;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Unsafe player radius used by hopper network normalize (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
        private double unsafePlayerRadius = 24D;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Item merge radius used by hopper network normalize (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
        private double itemMergeRadius = 2D;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Maximum merged item entities allowed per chunk in hopper network normalize.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
        private int maxMergedItemEntitiesPerChunk = 48;
        @Builder.Default
        @art.arcane.react.util.config.ConfigDoc(value = "Controls whether hopper network normalize applies unload idle hot chunks.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
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

        public Params withWorld(World world) {
            if (world != null) {
                this.world = world.getName();
            }
            return this;
        }
    }
}
