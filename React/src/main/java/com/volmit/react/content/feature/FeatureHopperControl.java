package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
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
    private transient Map<Hopper, Boolean> matterSystem;

    public FeatureHopperControl() {
        super(ID);
    }

    @Override
    public void onActivate() {
        matterSystem = new ConcurrentHashMap<>();
        React.instance.registerListener(this);
    }

    private void setHopperStatus(Hopper h, boolean status) {
        BlockState bs = h.getBlock().getState();
        org.bukkit.block.data.type.Hopper hopper = (org.bukkit.block.data.type.Hopper) bs.getBlockData();
        hopper.setEnabled(status);
        bs.setBlockData(hopper);
        bs.update(true);
        matterSystem.put(h, status);
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
            if (matterSystem.containsKey(h)) {
                e.setCancelled(true);
            } else {
                setHopperStatus(h, false);
            }
        }
    }

    @EventHandler
    public void on(InventoryMoveItemEvent e) {
        if (e.getDestination().getHolder() instanceof Hopper h && !matterSystem.containsKey(h)) {
            setHopperStatus(h, false);
        }
    }

    @Override
    public void onDeactivate() {
        React.instance.unregisterListener(this);
    }

    @Override
    public int getTickInterval() {
        return 1000;
    }

    @Override
    public void onTick() {
        var hoppers = new ArrayList<>(matterSystem.keySet());
        hoppers.forEach(this::handleHopperState);
    }

    private void handleHopperState(Hopper h) {
        if (!h.getBlock().getType().equals(Material.HOPPER)) {
            matterSystem.remove(h);
            return;
        }

        J.s(() -> {
            boolean hasItems = hasItemsOrNeighbourHasItems(h);
            if (hasItems != isHopperEnabled(h)) {
                setHopperStatus(h, hasItems);
                h.getWorld().spawnParticle(
                        Particle.REDSTONE,
                        h.getBlock().getLocation().add(0.5, 1.5, 0.5),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(hasItems ? Color.GREEN : Color.RED, 1)
                );
            }
        });
    }

    private boolean hasItemsOrNeighbourHasItems(Hopper h) {
        if (!h.getInventory().isEmpty()) {
            return true;
        }

        //check the neighbours for items too
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

        //Check the top of the hopper for any dropped item entities
        Block top = h.getBlock().getRelative(BlockFace.UP);
        for (Entity entity : top.getWorld().getNearbyEntities(top.getLocation().add(0.5, 0, 0.5), 0.5, 0.5, 0.5)) {
            if (entity instanceof Item || entity instanceof Minecart) {
                return true;
            }
        }

        return false;
    }

    private boolean isHopperEnabled(Hopper h) {
        return matterSystem.getOrDefault(h, false);
    }
}
