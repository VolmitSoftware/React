package com.volmit.react.content.feature;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.util.io.IO;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FeatureHopperControl extends ReactFeature implements Listener {
    public static final String ID = "feature-hopper-control";
    private transient Set<Hopper> watchedHoppers;
    private int hopperTickTime = 1000;
    private double hopperEntityYOffset = 0.5;
    private double hopperEntityXZOffset = 0.5;

    public FeatureHopperControl() {
        super(ID);
    }


    private void updateHopperStatus(Hopper h) {
        J.s(() -> toggleHopper(h, hasItemsOrNeighbourHasItems(h)));
        watchedHoppers.add(h);
    }

    @EventHandler
    public void on(BlockPlaceEvent e) {
        if (e.getBlock().getType() == Material.HOPPER) {
            Hopper h = (Hopper) e.getBlock().getState();
            updateHopperStatus(h);
        }
    }

    @EventHandler
    public void on(BlockPhysicsEvent e) {
        if (e.getBlock().getType() == Material.HOPPER) {
            Hopper h = (Hopper) e.getBlock().getState();
            if (watchedHoppers.contains(h)) {
                e.setCancelled(true);
            } else {
                updateHopperStatus(h);
            }
        }
    }

    @EventHandler
    public void on(InventoryMoveItemEvent e) {
        if (e.getDestination().getHolder() instanceof Hopper h) {
            updateHopperStatus(h);
        }
    }

    @Override
    public void onActivate() {
        watchedHoppers = new HashSet<>();
        React.instance.registerListener(this);
        loadHopperPersistence();
    }

    @Override
    public void onDeactivate() {
        saveHoppperPersistence();
        React.instance.unregisterListener(this);
    }

    @Override
    public int getTickInterval() {
        return hopperTickTime;
    }

    @Override
    public void onTick() {
        watchedHoppers.forEach(this::handleHopperState);
    }

    private void handleHopperState(Hopper h) {
        if (!h.getBlock().getType().equals(Material.HOPPER)) {
            watchedHoppers.remove(h);
            return;
        }

        J.s(() -> toggleHopper(h, hasItemsOrNeighbourHasItems(h)));
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
        for (Entity entity : top.getWorld().getNearbyEntities(top.getLocation().add(0.5, 0, 0.5), hopperEntityXZOffset, hopperEntityYOffset, hopperEntityXZOffset)) {
            if (entity instanceof Item || entity instanceof Minecart) {
                return true;
            }
        }

        return false;
    }

    private void toggleHopper(Hopper h, Boolean enabled) {
        //Check if its already in the state it should be
        BlockState bs = h.getBlock().getState();
        org.bukkit.block.data.type.Hopper hopper = (org.bukkit.block.data.type.Hopper) bs.getBlockData();
        if (enabled == hopper.isEnabled()) {
            J.s(() -> h.getWorld().spawnParticle(Particle.REDSTONE, h.getLocation().add(0.5, 2.5, 0.5), 1, new Particle.DustOptions(enabled ? Color.GREEN : Color.RED, 1)));

            return;
        }
        hopper.setEnabled(enabled);
        bs.setBlockData(hopper);
        bs.update(true);

    }

    private void saveHoppperPersistence() {
        J.ss(() -> {
            React.info("Saving hopper cache");
            File l = React.instance.getDataFile("data", "hopper-cache.json");
            Set<Map<String, Object>> hopperInfos = watchedHoppers.stream()
                    .map(hopper -> {
                        Map<String, Object> info = new HashMap<>();
                        info.put("world", hopper.getWorld().getName());
                        info.put("x", hopper.getLocation().getBlockX());
                        info.put("y", hopper.getLocation().getBlockY());
                        info.put("z", hopper.getLocation().getBlockZ());
                        return info;
                    })
                    .collect(Collectors.toSet());
            try {
                IO.writeAll(l, new Gson().toJson(hopperInfos));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void loadHopperPersistence() {
        J.ss(() -> {
            File l = React.instance.getDataFile("data", "hopper-cache.json");
            if (l.exists()) {
                try {
                    watchedHoppers = new HashSet<>();
                    String json = IO.readAll(l);
                    Type setType = new TypeToken<HashSet<Map<String, Object>>>() {
                    }.getType();
                    Set<Map<String, Object>> hopperInfos = new Gson().fromJson(json, setType);
                    for (Map<String, Object> info : hopperInfos) {
                        World world = React.instance.getServer().getWorld((String) info.get("world"));
                        int x = ((Number) info.get("x")).intValue();
                        int y = ((Number) info.get("y")).intValue();
                        int z = ((Number) info.get("z")).intValue();
                        Block b = world.getBlockAt(x, y, z);
                        if (b.getType() == Material.HOPPER) {
                            watchedHoppers.add((Hopper) b.getState());
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

}