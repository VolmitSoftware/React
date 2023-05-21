package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FeatureHopperControl extends ReactFeature implements Listener {
    public static final String ID = "feature-hopper-control";
    private transient Map<Hopper, Boolean> watchedHoppers;
    private int hopperTickTime = 1000;
    private double hopperEntityYOffset = 0.5;
    private double hopperEntityXZOffset = 0.5;

    public FeatureHopperControl() {
        super(ID);
    }

    @Override
    public void onActivate() {
        watchedHoppers = new ConcurrentHashMap<>();
        React.instance.registerListener(this);
    }

    private void setHopperStatus(Hopper h, boolean status) {
        BlockState bs = h.getBlock().getState();
        org.bukkit.block.data.type.Hopper hopper = (org.bukkit.block.data.type.Hopper) bs.getBlockData();
        hopper.setEnabled(status);
        bs.setBlockData(hopper);
        bs.update(true);
        watchedHoppers.put(h, status);
    }

    @EventHandler
    public void on(BlockPlaceEvent e) {
        if (e.getBlock().getType() == Material.HOPPER) {
            Hopper h = (Hopper) e.getBlock().getState();
            setHopperStatus(h, false);
        }
    }

    @EventHandler
    public void on(BlockPhysicsEvent e) {
        if (e.getBlock().getType() == Material.HOPPER) {
            Hopper h = (Hopper) e.getBlock().getState();
            if (watchedHoppers.containsKey(h)) {
                e.setCancelled(true);
            } else {
                setHopperStatus(h, false);
            }
        }
    }

    @EventHandler
    public void on(InventoryMoveItemEvent e) {
        if (e.getDestination().getHolder() instanceof Hopper h && !watchedHoppers.containsKey(h)) {
            setHopperStatus(h, false);
        }
    }

    @Override
    public void onDeactivate() {
        React.instance.unregisterListener(this);
    }

    @Override
    public int getTickInterval() {
        return hopperTickTime;
    }

    @Override
    public void onTick() {
        var hoppers = new ArrayList<>(watchedHoppers.keySet());
        hoppers.forEach(this::handleHopperState);
    }

    private void handleHopperState(Hopper h) {
        if (!h.getBlock().getType().equals(Material.HOPPER)) {
            watchedHoppers.remove(h);
            return;
        }

        J.ss(() -> {
            boolean hasItems = hasItemsOrNeighbourHasItems(h);
            if (hasItems != isHopperEnabled(h)) {
                setHopperStatus(h, hasItems);
            }
        });
    }

    private boolean hasItemsOrNeighbourHasItems(Hopper h) {
        if (!h.getInventory().isEmpty()) {
            return true;
        }

        for (BlockFace face : BlockFace.values()) {
            Block relative = h.getBlock().getRelative(face);
            if (relative.getType() == Material.HOPPER) {
                Hopper neighbour = (Hopper) relative.getState();
                if (!neighbour.getInventory().isEmpty()) {
                    return true;
                }
            } else if (relative.getState() instanceof Container container && !container.getInventory().isEmpty()) {
                return true;
            }
        }

        Block top = h.getBlock().getRelative(BlockFace.UP);
        for (Entity entity : top.getWorld().getNearbyEntities(top.getLocation().add(0.5, 0, 0.5), hopperEntityXZOffset, hopperEntityYOffset , hopperEntityXZOffset)) {
            if (entity instanceof Item || entity instanceof Minecart) {
                return true;
            }
        }

        return false;
    }

    private boolean isHopperEnabled(Hopper h) {
        return watchedHoppers.getOrDefault(h, false);
    }
}
