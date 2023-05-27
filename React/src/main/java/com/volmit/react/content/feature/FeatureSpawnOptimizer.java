package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeatureSpawnOptimizer extends ReactFeature implements Listener {
    public static final String ID = "spawn-optimizer";
    private transient Map<Chunk, List<EntityType>> spawns;
    private transient Map<Chunk, Location> averageLocation;
    private transient FeatureMobStacking stacker;

    public FeatureSpawnOptimizer() {
        super(ID);
    }

    @Override
    public void onActivate() {
        spawns = new HashMap<>();
        averageLocation = new HashMap<>();
        stacker = React.feature(FeatureMobStacking.class);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void on(CreatureSpawnEvent e) {
        if(e.getEntityType().equals(EntityType.GLOW_SQUID)) {
            return;
        }

        if(e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.SPAWNER) || e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.NATURAL)
            || e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.BEEHIVE)
            || e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.DISPENSE_EGG)
            || e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.NETHER_PORTAL)
            || e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.PATROL)
            || e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.SILVERFISH_BLOCK)
            || e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.VILLAGE_DEFENSE)
            || e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION)
            || e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.RAID)
            || e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.REINFORCEMENTS)

        ) {
            e.setCancelled(true);
            Chunk c = e.getLocation().getChunk();
            spawns.computeIfAbsent(c, k -> new ArrayList<>()).add(e.getEntityType());
            averageLocation.put(c, e.getLocation());
        }
    }

    @Override
    public void onDeactivate() {

    }

    @Override
    public int getTickInterval() {
        return 1000;
    }

    public void push(Chunk c) {
        List<EntityType> entities = spawns.get(c);

        if(entities != null && !entities.isEmpty()) {
            Location at = averageLocation.get(c);
            Map<EntityType, Integer> oc = new HashMap<>();

            for(EntityType i : entities) {
                oc.compute(i, (k,v) -> v == null ? 1 : v + 1);
            }

            for(EntityType i : oc.keySet()) {
                if(stacker.isEnabled()) {
                    int r = oc.get(i);
                    while(r > 0) {
                        Entity e = c.getWorld().spawnEntity(at, i);
                        int max = Math.min(r, stacker.getTheoreticalMaxStackCount(e));
                        stacker.setStackCount(e, max);
                        r -= max;
                    }
                }

                else {
                    for(int j = 0; j < oc.get(i); j++) {
                        c.getWorld().spawnEntity(at, i);
                    }
                }
            }
        }
    }

    @Override
    public void onTick() {
        J.s(() -> {
            for(Chunk i : spawns.keySet()) {
                push(i);
            }

            spawns.clear();
            averageLocation.clear();
        });
    }
}
