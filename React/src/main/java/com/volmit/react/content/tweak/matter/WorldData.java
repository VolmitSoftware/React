package com.volmit.react.content.tweak.matter;


import art.arcane.spatial.mantle.Mantle;
import art.arcane.spatial.matter.SpatialMatter;

import com.volmit.react.React;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.scheduling.TickedObject;
import lombok.Getter;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.HashMap;
import java.util.Map;

public class WorldData extends TickedObject {
    private static final Map<World, WorldData> mantles = new HashMap<>();

    static {
        SpatialMatter.registerSliceType(new Earnings.EarningsMatter());
    }

    private final World world;
    @Getter
    private final Mantle mantle;

    public WorldData(World world) {
        super("world-data", world.getUID().toString(), 30_000);
        this.world = world;
        mantle = new Mantle(React.instance.getDataFolder("data", "mantle"), world.getMaxHeight());
    }

    public static void stop() {
        mantles.values().forEach(WorldData::unregister);
    }

    public static WorldData of(World world) {
        return mantles.computeIfAbsent(world, WorldData::new);
    }

    public double getEarningsMultiplier(Block block) {
        Earnings e = mantle.get(block.getX(), block.getY(), block.getZ(), Earnings.class);

        if (e == null) {
            return 1;
        }

        return 1 / (double) (e.getEarnings() == 0 ? 1 : e.getEarnings());
    }

    public double reportEarnings(Block block) {
        Earnings e = mantle.get(block.getX(), block.getY(), block.getZ(), Earnings.class);
        e = e == null ? new Earnings(0) : e;

        if (e.getEarnings() >= 127) {
            return 1 / (double) (e.getEarnings() == 0 ? 1 : e.getEarnings());
        }

        mantle.set(block.getX(), block.getY(), block.getZ(), e.increment());
        return 1 / (double) (e.getEarnings() == 0 ? 1 : e.getEarnings());
    }

    public void unregister() {
        super.unregister();
        mantle.close();
        mantles.remove(world);
    }

    @EventHandler
    public void on(WorldSaveEvent e) {
        J.a(mantle::saveAll);
    }

    @EventHandler
    public void on(WorldUnloadEvent e) {
        unregister();
    }

    @Override
    public void onTick() {
        mantle.trim(60_000);
    }
}
