package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class FeatureHopperControl extends ReactFeature implements Listener {
    public static final String ID = "feature-hopper-control";
    private transient ConcurrentHashMap<Hopper, Boolean> matterSystem;

    public FeatureHopperControl() {
        super(ID);
    }

    @Override
    public void onActivate() {
        matterSystem = new ConcurrentHashMap<>();
        React.instance.registerListener(this);
    }

    private void disableHopper(Hopper h) {
        BlockState bs = h.getBlock().getState();
        org.bukkit.block.data.type.Hopper hopper = (org.bukkit.block.data.type.Hopper) bs.getBlockData();
        hopper.setEnabled(false);
        bs.setBlockData(hopper);
        bs.update(true);
        matterSystem.put(h, false);
    }

    private void enableHopper(Hopper h) {
        BlockState bs = h.getBlock().getState();
        org.bukkit.block.data.type.Hopper hopper = (org.bukkit.block.data.type.Hopper) bs.getBlockData();
        hopper.setEnabled(true);
        bs.setBlockData(hopper);
        bs.update(true);
        matterSystem.put(h, true);
    }

    private boolean getHopperStatus(Hopper h) {
        return matterSystem.get(h);
    }

    @EventHandler
    public void on(BlockPlaceEvent e) {
        if (e.getBlock().getType() == Material.HOPPER) {
            Hopper h = (Hopper) e.getBlock().getState();
            disableHopper(h);
            React.info("Hopper Added to Matter System!");
        }
    }

    // Cancel the event so that the hopper doesn't update
    @EventHandler
    public void on(BlockPhysicsEvent e) {
        if (e.getBlock().getType() == Material.HOPPER) {
            if (matterSystem.containsKey(e.getBlock().getState())) {
                React.info("Hopper Physics Updated, and blocked");
                e.setCancelled(true);
            } else {
                React.info("Hopper Added to Matter System!");
                Hopper h = (Hopper) e.getBlock().getState();
                disableHopper(h);
            }
        }
    }

    @EventHandler
    public void on(InventoryMoveItemEvent e) {
        if (e.getDestination().getHolder() instanceof Hopper h) {
            React.info("Hopper Move Triggered");
            if (!matterSystem.contains(h)) {
                disableHopper(h);
            }
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
        React.info("Hopper Tick Triggered");
        // For every hopper in matter system
        for (Hopper h : new ArrayList<>(matterSystem.keySet())) {
            //if the hopper is enabled play the villager happy particle
            if (matterSystem.get(h)) {
                h.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, h.getBlock().getLocation().add(0.5, 2.5, 0.5), 1);
            }
            //if the hopper is disabled play the red particle
            else {
                h.getWorld().spawnParticle(Particle.REDSTONE, h.getBlock().getLocation().add(0.5, 1.5, 0.5), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.YELLOW, 1));
            }
        }
        for (Hopper h : new ArrayList<>(matterSystem.keySet())) {
            // Remove it if it's not there
            if (!h.getBlock().getType().equals(Material.HOPPER)) {
                React.info("Hopper Removed from Matter System!");
                matterSystem.remove(h);
                continue;
            }

            // Check if the hopper has items or if any of its neighbours has items
            J.s(() -> {
                if (hasItemsOrNeighbourHasItems(h)) {
                    if (!matterSystem.get(h)) {
                        enableHopper(h);
                        h.getWorld().spawnParticle(Particle.REDSTONE, h.getBlock().getLocation().add(0.5, 1.5, 0.5), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.GREEN, 1));

                    }
                } else {
                    if (matterSystem.get(h)) {
                        disableHopper(h);
                        h.getWorld().spawnParticle(Particle.REDSTONE, h.getBlock().getLocation().add(0.5, 1.5, 0.5), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.RED, 1));
                    }
                }
            });
        }
    }

    private boolean hasItemsOrNeighbourHasItems(Hopper h) {
        // Check if the hopper has items
        if (!h.getInventory().isEmpty()) {
            return true;
        }

        // Check the items of the neighbours (horizontal and above)
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
            Block relative = h.getBlock().getRelative(face);
            if (relative.getType() == Material.HOPPER) {
                Hopper neighbour = (Hopper) relative.getState();
                if (!neighbour.getInventory().isEmpty()) {
                    return true;
                }
            } else if (relative.getType().isInteractable()) { // Check for container blocks like chests
                Container container = (Container) relative.getState();
                if (!container.getInventory().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

}
