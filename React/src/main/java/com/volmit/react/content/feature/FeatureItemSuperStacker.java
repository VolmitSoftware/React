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

package com.volmit.react.content.feature;

import art.arcane.chrono.ChronoLatch;
import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.content.sampler.SamplerEntities;
import com.volmit.react.core.controller.EntityController;
import com.volmit.react.util.math.RNG;
import com.volmit.react.util.world.BundleUtils;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Stacks items into bundles
 */
public class FeatureItemSuperStacker extends ReactFeature implements Listener {
    public static final String ID = "item-super-stacker";
    private int maxItemsPerBundle = 64;
    private double searchRadius = 3;
    private transient ChronoLatch cl = new ChronoLatch(10);

    public FeatureItemSuperStacker() {
        super(ID);
    }

    public boolean isSuperStack(Item item) {
        return BundleUtils.isBundle(item.getItemStack()) && BundleUtils.isFlagged(item.getItemStack());
    }

    public List<ItemStack> explode(Item item) {
        ItemStack m = item.getItemStack();
        item.remove();
        ((SamplerEntities) React.sampler(SamplerEntities.ID)).getEntities().decrementAndGet();

        if (BundleUtils.isFlagged(m)) {
            return BundleUtils.explode(item.getItemStack());
        }

        return List.of(m);
    }

    public void effectMerge(Item item, Item into) {
        Location buf = item.getLocation().clone();
        item.getWorld().spawnParticle(Particle.ITEM_CRACK, item.getLocation(), 7, 0.1, 0.1, 0.1, 0.1, item.getItemStack());

        Vector j = into.getLocation().clone().subtract(item.getLocation()).toVector().normalize().multiply(into.getLocation().clone().distance(item.getLocation()) / (searchRadius * 2));
        for (int i = 0; i < searchRadius * 2; i++) {
            buf = buf.clone().add(j);
            item.getWorld().spawnParticle(Particle.ITEM_CRACK, buf, 3, 0, 0, 0, 0, item.getItemStack());
        }

        if (cl.flip()) {
            item.getWorld().playSound(item.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.5f, 1.2f + RNG.r.f(-0.1f, 0.1f));
        }
    }

    public void mergeWithNearbyItems(Item item) {
        if (item.isDead()) {
            return;
        }

        for (Entity i : item.getWorld().getNearbyEntities(item.getLocation(), searchRadius, searchRadius, searchRadius)) {
            if (i instanceof Item into) {
                if (into.isDead() || into.getUniqueId().equals(item.getUniqueId())) {
                    continue;
                }

                ItemStack is = BundleUtils.merge(item.getItemStack(), into.getItemStack(), maxItemsPerBundle);

                if (is != null) {
                    effectMerge(item, into);
                    item.remove();
                    ((SamplerEntities) React.sampler(SamplerEntities.ID)).getEntities().decrementAndGet();
                    into.setItemStack(is);
                    break;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (isSuperStack(e.getItem())) {
                e.setCancelled(true);
                for (ItemStack i : explode(e.getItem())) {
                    p.getInventory().addItem(i).values().forEach((g) -> p.getWorld().dropItem(p.getLocation(), g));
                }

                if (cl.flip()) {
                    p.getWorld().playSound(e.getItem().getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 1f, 0.85f + RNG.r.f(-0.1f, 0.1f));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ItemSpawnEvent e) {
        mergeWithNearbyItems(e.getEntity());
    }

    @Override
    public void onActivate() {
        React.controller(EntityController.class).registerEntityTickListener(EntityType.DROPPED_ITEM, (i) -> mergeWithNearbyItems((Item) i));
    }

    @Override
    public void onDeactivate() {

    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}
