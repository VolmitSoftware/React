package com.volmit.react.content.feature;

import art.arcane.chrono.ChronoLatch;
import art.arcane.chrono.PrecisionStopwatch;
import art.arcane.chrono.Profiler;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.volmit.react.React;
import com.volmit.react.api.entity.EntityPriority;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.content.sampler.SamplerChunksLoaded;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.math.M;
import com.volmit.react.util.scheduling.J;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPoseChangeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class FeatureEntityTrimmer extends ReactFeature implements Listener {
    public static final String ID = "entity-trimmer";
    private transient double maxPriority = -1;
    private transient int cooldown = 0;
    private EntityPriority priority = new EntityPriority();
    private int softMaxEntitiesPerChunk = 10;
    private int softMaxEntitiesPerPlayer = 60;
    private int softMaxEntitiesPerWorld = 1000;
    private double priorityPercentCutoff = 0.1;
    private int tickIntervalMS = 1000;
    private double opporunityThreshold = 0.1;
    private double maxCrowdCalculationMS = 1;
    private int minKillBatchSize = 25;

    public FeatureEntityTrimmer() {
        super(ID);
    }

    @Override
    public void onActivate() {
        React.instance.registerListener(this);
        priority.rebuildPriority();
        double maxPriority = -1;
        double minPriority = Double.MAX_VALUE;

        for(EntityType i : EntityType.values()) {
            double p = priority.getPriority(i);
            if(p > maxPriority) {
                maxPriority = p;
            }
            if(p < minPriority) {
                minPriority = p;
            }
        }

        this.maxPriority = M.lerp(Math.max(minPriority, 0), maxPriority, priorityPercentCutoff);
        React.verbose("Entity Trimmer Priority Cutoff: " + maxPriority + " or lower");
    }

    @Override
    public void onDeactivate() {
        React.instance.unregisterListener(this);
    }

    @Override
    public int getTickInterval() {
        return tickIntervalMS;
    }

    @EventHandler
    public void on(EntitySpawnEvent e) {
        priority.setCrowd(e.getEntity());
    }


    @EventHandler
    public void on(EntityDamageEvent e) {
        priority.setCrowd(e.getEntity());
    }

    @EventHandler
    public void on(EntityTargetEvent e) {
        priority.setCrowd(e.getEntity());

        if(e.getTarget() != null) {
            priority.setCrowd(e.getTarget());
        }
    }

    @EventHandler
    public void on(EntityInteractEvent e) {
        priority.setCrowd(e.getEntity());
    }

    @EventHandler
    public void on(EntityPoseChangeEvent e) {
        priority.setCrowd(e.getEntity());
    }

    @EventHandler
    public void on(EntityRegainHealthEvent e) {
        priority.setCrowd(e.getEntity());
    }

    @EventHandler
    public void on(EntityBreedEvent e) {
        priority.setCrowd(e.getMother());
        priority.setCrowd(e.getFather());
        priority.setCrowd(e.getEntity());
    }

    @Override
    public void onTick() {
        if(cooldown-- > 0) {
            return;
        }

        List<Entity> shitlist = new ArrayList<>();
        int tc = (int) Math.round(Math.ceil(React.instance.getSampleController().getSampler(SamplerChunksLoaded.ID).sample()));
        int wc = 0;

        for(World i : Bukkit.getWorlds()) {
            shitlist.addAll(J.sResult(i::getEntities));
            wc++;
        }

        shitlist.sort((a, b) -> {
            double pa = priority.getPriorityWithCrowd(a);
            double pb = priority.getPriorityWithCrowd(b);
            return Double.compare(pa, pb);
        });

        int ec = shitlist.size();
        int theoreticalMax = Math.min(Bukkit.getOnlinePlayers().size() * softMaxEntitiesPerPlayer, Math.max(softMaxEntitiesPerWorld * wc, softMaxEntitiesPerChunk * tc));

        double pri = -1;
        Entity e;

        for(int i = shitlist.size() - 1; i >= 0; i--) {
            e = shitlist.get(i);
            pri = priority.getPriorityWithCrowd(e);

            if(pri > maxPriority || pri < 0) {
                shitlist.remove(i);
            }
        }

        J.s(() -> {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            int m = 0;
            for(Entity i : shitlist) {
                if(M.r(0.1)) {
                    priority.setCrowd(i);
                    m++;
                }

                if(p.getMilliseconds() > maxCrowdCalculationMS) {
                    break;
                }
            }
        });

        int maxKill = (int) (ec * opporunityThreshold);

        if(maxKill < minKillBatchSize) {
            cooldown+= 3;
            return;
        }

        if(maxKill > 0 && shitlist.size() >= maxKill) {
            for(int i = 0; i < maxKill; i++) {
                kill(shitlist.remove(0));
            }

            React.success("Entity Trimmer: " + maxKill + " entities removed");
        }
    }

    private void kill(Entity entity) {
        J.s(() -> React.kill(entity), (int) (20 * Math.random()));
    }

    @EqualsAndHashCode
    @AllArgsConstructor
    @Data
    public static class IBlock {
        private final World world;
        private final int x;
        private final int y;
        private final int z;

        public Block block() {
            return world.getBlockAt(x, y, z);
        }

        public IChunk chunk() {
            return new IChunk(world, x >> 4, z >> 4);
        }
    }

    @EqualsAndHashCode
    @AllArgsConstructor
    @Data
    public static class IChunk {
        private final World world;
        private final int x;
        private final int z;

        public Chunk chunk() {
            return world.getChunkAt(x, z);
        }
    }
}
