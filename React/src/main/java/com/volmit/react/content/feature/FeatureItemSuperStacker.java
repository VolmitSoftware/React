package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.core.nms.R194;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.math.RNG;
import com.volmit.react.util.world.BundleUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Stacks items into bundles
 */
public class FeatureItemSuperStacker extends ReactFeature implements Listener {
    public static final String ID = "item-super-stacker";
    private int maxItemsPerBundle = 64;
    private double searchRadius = 3;

    public FeatureItemSuperStacker() {
        super(ID);
    }

    public boolean isSuperStack(Item item) {
        return BundleUtils.isBundle(item.getItemStack()) && BundleUtils.isFlagged(item.getItemStack());
    }

    public List<ItemStack> explode(Item item) {
        ItemStack m = item.getItemStack();
        item.remove();
        if(BundleUtils.isFlagged(m)) {
            return BundleUtils.explode(item.getItemStack());
        }

        return List.of(m);
    }

    public void effectMerge(Item item, Item into) {
        Location buf = item.getLocation().clone();
        item.getWorld().spawnParticle(Particle.ITEM_CRACK, item.getLocation(), 7, 0.1, 0.1, 0.1, 0.1, item.getItemStack());

        Vector j = into.getLocation().clone().subtract(item.getLocation()).toVector().normalize().multiply(into.getLocation().clone().distance(item.getLocation()) / (searchRadius * 2));
        for(int i = 0; i < searchRadius * 2; i++) {
            buf = buf.clone().add(j);
            item.getWorld().spawnParticle(Particle.ITEM_CRACK, buf, 3, 0, 0, 0, 0, item.getItemStack());
        }

        item.getWorld().playSound(item.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.5f, 1.2f + RNG.r.f(-0.1f, 0.1f));
    }

    public void mergeify(Item item) {
        for(Entity i : item.getWorld().getNearbyEntities(item.getLocation(), searchRadius, searchRadius, searchRadius)) {
            if(i instanceof Item into) {
                if(into.getUniqueId().equals(item.getUniqueId())) {
                    continue;
                }

                ItemStack is = BundleUtils.merge(item.getItemStack(), into.getItemStack(), maxItemsPerBundle);

                if(is != null) {
                    effectMerge(item, into);
                    item.remove();
                    into.setItemStack(is);
                    break;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityPickupItemEvent e) {
        if(e.getEntity() instanceof Player p){
            if(isSuperStack(e.getItem())) {
                e.setCancelled(true);
                for(ItemStack i : explode(e.getItem())) {
                    p.getInventory().addItem(i).values().forEach((g) -> p.getWorld().dropItem(p.getLocation(), g));
                }

                p.getWorld().playSound(e.getItem().getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 1f, 0.85f + RNG.r.f(-0.1f, 0.1f));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ItemSpawnEvent e) {
       mergeify(e.getEntity());
    }

    @Override
    public void onActivate() {
        React.instance.registerListener(this);
        React.instance.getEntityController().registerEntityTickListener(EntityType.DROPPED_ITEM, (i) -> mergeify((Item) i));
    }

    @Override
    public void onDeactivate() {
        React.instance.unregisterListener(this);
    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}
