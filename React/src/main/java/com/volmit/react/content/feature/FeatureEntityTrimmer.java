/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.model.ReactConfiguration;
import com.volmit.react.model.ReactEntity;
import com.volmit.react.util.math.M;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.world.CustomMobChecker;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeatureEntityTrimmer extends ReactFeature implements Listener {
    public static final String ID = "entity-trimmer";
    private transient double maxPriority = -1;
    private transient int cooldown = 0;
    private boolean skipCustomMobs = false;
    private int playerMobBlockDistance = 32;

    /**
     * List of blacklisted entities with already blacklisted examples
     */
    private List<EntityType> blacklist = List.of(
            EntityType.ITEM_DISPLAY, EntityType.PLAYER, EntityType.ARMOR_STAND, EntityType.ITEM_FRAME, EntityType.PAINTING, EntityType.LEASH_HITCH,
            EntityType.MINECART, EntityType.MINECART_CHEST, EntityType.MINECART_COMMAND, EntityType.MINECART_FURNACE,
            EntityType.MINECART_HOPPER, EntityType.MINECART_MOB_SPAWNER, EntityType.MINECART_TNT, EntityType.BOAT,
            EntityType.FALLING_BLOCK, EntityType.DROPPED_ITEM, EntityType.EXPERIENCE_ORB, EntityType.FISHING_HOOK,
            EntityType.PRIMED_TNT, EntityType.SPLASH_POTION, EntityType.THROWN_EXP_BOTTLE, EntityType.ENDER_PEARL,
            EntityType.ENDER_SIGNAL, EntityType.FIREWORK, EntityType.LIGHTNING, EntityType.SHULKER_BULLET,
            EntityType.SMALL_FIREBALL, EntityType.SNOWBALL, EntityType.SPECTRAL_ARROW, EntityType.SPLASH_POTION,
            EntityType.THROWN_EXP_BOTTLE);

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

        for (EntityType i : EntityType.values()) {
            double p = ReactConfiguration.get().getPriority().getPriority(i);
            if (p > maxPriority) {
                maxPriority = p;
            }
            if (p < minPriority) {
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
            for (World i : Bukkit.getWorlds()) {
                lastEntities.addAll(i.getEntities());
            }
        });

        if (cooldown-- > 0) {
            return;
        }

        List<Entity> entitiesToRemove = new ArrayList<>();
        Map<Chunk, List<Entity>> entitiesPerChunk = new HashMap<>();
        Map<World, List<Entity>> entitiesPerWorld = new HashMap<>();
        Map<Player, List<Entity>> entitiesPerPlayer = new HashMap<>();

        List<Entity> tempEntities = new ArrayList<>(lastEntities);
        for (Entity entity : tempEntities) {
            Chunk chunk = entity.getLocation().getChunk();
            World world = entity.getWorld();
            entitiesPerChunk.computeIfAbsent(chunk, k -> new ArrayList<>()).add(entity);
            entitiesPerWorld.computeIfAbsent(world, k -> new ArrayList<>()).add(entity);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(world) && player.getLocation().distance(entity.getLocation()) < playerMobBlockDistance) {
                    entitiesPerPlayer.computeIfAbsent(player, k -> new ArrayList<>()).add(entity);
                }
            }
        }


        // Kill excess entities per chunk, player, world
        entitiesPerChunk.forEach((chunk, entities) -> {
            if (entities.size() > softMaxEntitiesPerChunk) {
                addEntitiesToRemove(entitiesToRemove, entities, softMaxEntitiesPerChunk);
            }
        });
        entitiesPerPlayer.forEach((player, entities) -> {
            if (entities.size() > softMaxEntitiesPerPlayer) {
                addEntitiesToRemove(entitiesToRemove, entities, softMaxEntitiesPerPlayer);
            }
        });
        entitiesPerWorld.forEach((world, entities) -> {
            if (entities.size() > softMaxEntitiesPerWorld) {
                addEntitiesToRemove(entitiesToRemove, entities, softMaxEntitiesPerWorld);
            }
        });

        lastEntities.clear();
        lastEntities.addAll(entitiesToRemove);

        // Sorting, filtering, opportunity threshold, and killing.
        List<Entity> shitlist = new ArrayList<>(lastEntities);
        lastEntities.clear();

        // Remove blacklisted entities and sort the list.
        shitlist.removeIf(entity -> blacklist.contains(entity.getType()) || entity.getTicksLived() < 400);
        shitlist.sort((a, b) -> {
            double pa = ReactEntity.getPriority(a);
            double pb = ReactEntity.getPriority(b);
            return Double.compare(pa, pb);
        });

        int ec = shitlist.size();

        double pri = -1;
        Entity e;

        for (int i = shitlist.size() - 1; i >= 0; i--) {
            e = shitlist.get(i);
            pri = ReactEntity.getPriority(e);

            if (pri > maxPriority || pri < 0) {
                shitlist.remove(i);
            } else if (skipCustomMobs && CustomMobChecker.isCustom(e)) {
                shitlist.remove(i);
            }
        }

        int maxKill = (int) (ec * opporunityThreshold);

        if (maxKill < minKillBatchSize) {
            cooldown += 3;
            return;
        }

        if (maxKill > 0 && shitlist.size() >= maxKill) {
            for (int i = 0; i < maxKill; i++) {
                kill(shitlist.remove(0));
            }

            if (printEntityPurgeSuccess) // Prevent spam
                React.success("Entity Trimmer: " + maxKill + " entities removed");
        }
    }

    private void addEntitiesToRemove(List<Entity> entitiesToRemove, List<Entity> entities, int maxEntities) {
        entities.sort((a, b) -> {
            double pa = ReactEntity.getPriority(a);
            double pb = ReactEntity.getPriority(b);
            return Double.compare(pa, pb);
        });

        for (int i = maxEntities; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            if (!entitiesToRemove.contains(entity) && !blacklist.contains(entity.getType()) &&
                    !(skipCustomMobs && CustomMobChecker.isCustom(entity)) && entity.getTicksLived() >= 400) {
                entitiesToRemove.add(entity);
            }
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
