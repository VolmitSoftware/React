package com.volmit.react.util;

import com.volmit.react.Surge;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import primal.lang.collection.GList;

import java.util.List;

@SuppressWarnings("deprecation")
public abstract class WorldMonitor implements Listener {
    private final GList<Chunk> pending;
    private boolean chunksChanged = true;
    private boolean dropChanged = true;
    private boolean tileChanged = true;
    private boolean livingChanged = true;
    private boolean totalChanged = true;
    private boolean updated = false;
    private int totalChunks = 0;
    private int totalDrops = 0;
    private int totalTiles = 0;
    private int totalLiving = 0;
    private int totalEntities = 0;
    private int chunksLoaded = 0;
    private int chunksUnloaded = 0;
    private int rollingTileCount = 0;
    private long ms = M.ms();

    public WorldMonitor() {
        Surge.register(this);
        pending = new GList<Chunk>();
    }

    public void run() {
        if (TICK.tick < 20) {
            return;
        }

        try {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                return;
            }
        } catch (Throwable e) {

        }

        try {
            sample();
        } catch (Throwable e) {
            Ex.t(e);
        }
    }

    public abstract void updated(int totalChunks, int totalDrops, int totalTiles, int totalLiving, int totalEntities, int chunksLoaded, int chunksUnloaded);

    @EventHandler
    public void on(ChunkLoadEvent e) {
        chunksChanged = true;
        tileChanged = true;
        livingChanged = true;
        dropChanged = true;
        chunksLoaded++;
    }

    @EventHandler
    public void on(ChunkUnloadEvent e) {
        chunksChanged = true;
        tileChanged = true;
        livingChanged = true;
        dropChanged = true;
        chunksUnloaded++;
    }

    @EventHandler
    public void on(EntitySpawnEvent e) {
        livingChanged = true;
    }

    @EventHandler
    public void on(EntityDeathEvent e) {
        livingChanged = true;
    }

    @EventHandler
    public void on(PlayerDropItemEvent e) {
        dropChanged = true;
    }

    @EventHandler
    public void on(PlayerPickupItemEvent e) {
        dropChanged = true;
    }

    @EventHandler
    public void on(BlockPlaceEvent e) {
        tileChanged = true;
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        tileChanged = true;
    }

    private void doUpdate() {
        updated = true;
    }

    private void sample() {
        if (chunksChanged || dropChanged || tileChanged || livingChanged) {
            totalChanged = true;
        }

        try {
            if (chunksChanged || tileChanged) {
                sampleTilesAndChunks();
                chunksChanged = false;
                doUpdate();
            }

        } catch (Throwable e) {
            Ex.t(e);
        }

        try {
            if (totalChanged || livingChanged || dropChanged) {
                sampleEntities();
                totalChanged = false;
                livingChanged = false;
                dropChanged = false;
                doUpdate();
            }

        } catch (Throwable e) {
            Ex.t(e);
        }

        try {
            if (updated || M.ms() - ms > 1000) {
                updated(totalChunks, totalDrops, totalTiles, totalLiving, totalEntities, chunksLoaded, chunksUnloaded);

                if (M.ms() - ms > 1000) {
                    ms = M.ms();
                    chunksLoaded = 0;
                    chunksLoaded = 0;
                }
            }
        } catch (Throwable e) {
            Ex.t(e);
        }
    }

    private void sampleEntities() {
        totalEntities = totalLiving = totalDrops = 0;

        for (World w : Bukkit.getWorlds()) {
            final List<Entity> e = w.getEntities();
            totalEntities += e.size();

            for (Entity t : e) {
                if (t instanceof LivingEntity) ++totalLiving;
                else if (t instanceof Item) ++totalDrops;
            }
        }
    }

    private void sampleTilesAndChunks() {
        totalChunks = 0;

        if (pending.isEmpty()) {
            totalTiles = rollingTileCount;
            rollingTileCount = 0;
        }

        for (World i : Bukkit.getWorlds()) {
            try {
                final Chunk[] chunks = i.getLoadedChunks();
                totalChunks += chunks.length;

                for (Chunk j : chunks) {
                    if (pending.isEmpty()) pending.add(j);
                }
            } catch (Throwable e) {
                Ex.t(e);
            }
        }

        if (!pending.isEmpty()) {
            new S("tile-count-interval") {
                @Override
                public void run() {
                    long ns = M.ns();

                    while (!pending.isEmpty() && M.ns() - ns < 250000) {
                        Chunk c = pending.pop();

                        if (!c.isLoaded()) {
                            continue;
                        }

                        rollingTileCount += c.getTileEntities().length;
                    }

                    if (pending.isEmpty()) {
                        tileChanged = false;
                    }
                }
            };

            return;
        }
    }
}
