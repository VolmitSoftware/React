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
import org.bukkit.entity.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class FeatureItemBackpressure extends ReactFeature {
    public static final String ID = "item-backpressure";
    private int tickIntervalMS = 1000;
    private double triggerTickTimeMS = 60;
    private int triggerEntityCount = 5000;
    private int maxItemsScannedPerWorld = 220;
    private int maxItemsRemovedPerCycle = 90;
    private int minimumItemAgeTicks = 200;
    private double noPlayerRadius = 40;
    private boolean protectNamedItems = true;
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

    private boolean canRemove(Item item) {
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
