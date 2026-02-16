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

package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.config.ConfigDescription("Configuration for Item Backpressure feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureItemBackpressure extends ReactFeature {
    public static final String ID = "item-backpressure";
    @art.arcane.react.util.config.ConfigDoc(value = "Main evaluation interval for item backpressure in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
    private int tickIntervalMS = 1000;
    @art.arcane.react.util.config.ConfigDoc(value = "Tick-time threshold for trigger time in item backpressure (milliseconds).", impact = "Higher values delay activation or exit; lower values make this threshold easier to cross.")
    private double triggerTickTimeMS = 60;
    @art.arcane.react.util.config.ConfigDoc(value = "Trigger threshold for trigger entity count in item backpressure.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
    private int triggerEntityCount = 5000;
    @art.arcane.react.util.config.ConfigDoc(value = "Maximum items scanned allowed per world in item backpressure.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxItemsScannedPerWorld = 220;
    @art.arcane.react.util.config.ConfigDoc(value = "Maximum items removed allowed per cycle in item backpressure.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
    private int maxItemsRemovedPerCycle = 90;
    @art.arcane.react.util.config.ConfigDoc(value = "Minimum item age ticks required by item backpressure.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
    private int minimumItemAgeTicks = 200;
    @art.arcane.react.util.config.ConfigDoc(value = "No player radius used by item backpressure (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
    private double noPlayerRadius = 40;
    @art.arcane.react.util.config.ConfigDoc(value = "Protects named items from item backpressure enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
    private boolean protectNamedItems = true;
    @art.arcane.react.util.config.ConfigDoc(value = "Protects valuables from item backpressure enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
    private boolean protectValuables = true;
    private Set<Material> valuables = Set.of(
            Material.NETHERITE_INGOT,
            Material.NETHERITE_SCRAP,
            Material.NETHER_STAR,
            Material.DIAMOND,
            Material.ELYTRA,
            Material.TOTEM_OF_UNDYING
    );

    public FeatureItemBackpressure() {
        super(ID);
    }

    @Override
    public void onActivate() {

    }

    @Override
    public void onDeactivate() {

    }

    @Override
    public int getTickInterval() {
        return tickIntervalMS;
    }

    @Override
    public void onTick() {
        if (!shouldApplyBackpressure()) {
            return;
        }

        if (J.isFoliaThreading()) {
            removeRemoteItemsFolia();
            return;
        }

        J.s(this::removeRemoteItems);
    }

    private boolean shouldApplyBackpressure() {
        double tickTime = React.sampler(SamplerTickTime.ID).sample();
        if (tickTime >= triggerTickTimeMS) {
            return true;
        }

        return React.sampler(SamplerEntities.ID).sample() >= triggerEntityCount;
    }

    private void removeRemoteItems() {
        int budget = Math.max(1, maxItemsRemovedPerCycle);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (World world : Bukkit.getWorlds()) {
            if (budget <= 0) {
                return;
            }

            List<Item> items = new ArrayList<>(world.getEntitiesByClass(Item.class));
            if (items.isEmpty()) {
                continue;
            }

            int scan = Math.min(items.size(), maxItemsScannedPerWorld);
            for (int i = 0; i < scan; i++) {
                if (budget <= 0) {
                    return;
                }

                Item item = items.get(random.nextInt(items.size()));
                if (!canRemove(item)) {
                    continue;
                }

                item.remove();
                budget--;
            }
        }
    }

    private void removeRemoteItemsFolia() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            return;
        }

        AtomicInteger budget = new AtomicInteger(Math.max(1, maxItemsRemovedPerCycle));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int playerSamples = Math.min(players.size(), Math.max(1, maxItemsScannedPerWorld / 16));

        for (int i = 0; i < playerSamples && budget.get() > 0; i++) {
            Player player = players.get(random.nextInt(players.size()));
            J.runEntity(player, () -> removeAroundPlayer(player, budget));
        }
    }

    private void removeAroundPlayer(Player player, AtomicInteger budget) {
        if (player == null || !player.isOnline() || !J.isOwnedByCurrentRegion(player)) {
            return;
        }

        List<Entity> nearby = player.getNearbyEntities(noPlayerRadius + 24, Math.max(32, noPlayerRadius), noPlayerRadius + 24);
        if (nearby.isEmpty()) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int scan = Math.min(maxItemsScannedPerWorld, nearby.size());
        for (int i = 0; i < scan; i++) {
            if (budget.get() <= 0) {
                return;
            }

            Entity sampled = nearby.get(random.nextInt(nearby.size()));
            if (!(sampled instanceof Item item)) {
                continue;
            }

            if (!canRemove(item)) {
                continue;
            }

            item.remove();
            budget.decrementAndGet();
        }
    }

    private boolean canRemove(Item item) {
        if (item == null) {
            return false;
        }

        if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(item)) {
            return false;
        }

        if (item.isDead() || item.getTicksLived() < minimumItemAgeTicks) {
            return false;
        }

        if (protectNamedItems && item.getCustomName() != null && !item.getCustomName().isBlank()) {
            return false;
        }

        if (protectValuables && valuables.contains(item.getItemStack().getType())) {
            return false;
        }

        return !React.hasNearbyPlayer(item.getLocation(), noPlayerRadius);
    }
}
