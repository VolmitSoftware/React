package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.content.sampler.SamplerChunks;
import com.volmit.react.model.ReactConfiguration;
import com.volmit.react.model.ReactEntity;
import com.volmit.react.util.math.M;
import com.volmit.react.util.scheduling.J;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

public class FeatureEntityTrimmer extends ReactFeature implements Listener {
    public static final String ID = "entity-trimmer";
    private transient double maxPriority = -1;
    private transient int cooldown = 0;


    /**
     * List of blacklisted entities with already blacklisted examples
     */
    private List<EntityType> blacklist = List.of();

    /**
     * Calculates total chunks * softMax to see if we are exceeding
     */
    private boolean printEntityPurgeSuccess = true;

    /**
     * Calculates total chunks * softMax to see if we are exceeding
     */
    private int softMaxEntitiesPerChunk = 11;

    /**
     * Calculates players * softMax to see if we are exceeding
     */
    private int softMaxEntitiesPerPlayer = 100;

    /**
     * Calculates worlds * softMax to see if we are exceeding
     */
    private int softMaxEntitiesPerWorld = 1000;

    /**
     * Use the lowest X percent of entities by priority. Anything higher than the cutoff wont be touched
     */
    private double priorityPercentCutoff = 0.1;

    /**
     * How often to tick in ms
     */
    private int tickIntervalMS = 1000;

    /**
     * Will only run if it can take away X percent of entities. Wont take more per tick either
     */
    private double opporunityThreshold = 0.25;

    /**
     * The minimum amount of entities to kill per cycle. Lower than this it wont run
     */
    private int minKillBatchSize = 100;
    private transient List<Entity> lastEntities = new ArrayList<>();

    public FeatureEntityTrimmer() {
        super(ID);
    }

    @Override
    public void onActivate() {
        double maxPriority = -1;
        double minPriority = Double.MAX_VALUE;

        for(EntityType i : EntityType.values()) {
            double p = ReactConfiguration.get().getPriority().getPriority(i);
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

    }

    @Override
    public int getTickInterval() {
        return tickIntervalMS;
    }

    @Override
    public void onTick() {
        J.s(() -> {
            for(World i : Bukkit.getWorlds()) {
                lastEntities.addAll(i.getEntities());
            }
        });

        if(cooldown-- > 0) {
            return;
        }

        List<Entity> shitlist = new ArrayList<>(lastEntities);
        lastEntities.clear();
        int tc = (int) Math.round(Math.ceil(React.sampler(SamplerChunks.ID).sample()));
        int wc = 0;

        // Remove blacklisted entities
        shitlist.removeIf(entity -> blacklist.contains(entity.getType()) || entity.getTicksLived() < 400);

        shitlist.sort((a, b) -> {
            double pa = ReactEntity.getPriority(a);
            double pb = ReactEntity.getPriority(b);
            return Double.compare(pa, pb);
        });

        int ec = shitlist.size();
        int theoreticalMax = Math.min(Bukkit.getOnlinePlayers().size() * softMaxEntitiesPerPlayer, Math.max(softMaxEntitiesPerWorld * wc, softMaxEntitiesPerChunk * tc));

        double pri = -1;
        Entity e;

        for(int i = shitlist.size() - 1; i >= 0; i--) {
            e = shitlist.get(i);
            pri = ReactEntity.getPriority(e);

            if(pri > maxPriority || pri < 0) {
                shitlist.remove(i);
            }
        }

        int maxKill = (int) (ec * opporunityThreshold);

        if(maxKill < minKillBatchSize) {
            cooldown+= 3;
            return;
        }

        if(maxKill > 0 && shitlist.size() >= maxKill) {
            for(int i = 0; i < maxKill; i++) {
                kill(shitlist.remove(0));
            }

            if (printEntityPurgeSuccess) // Prevent spam
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
