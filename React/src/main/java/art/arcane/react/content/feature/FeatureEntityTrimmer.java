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

package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.util.scheduling.J;
import art.arcane.react.util.world.CustomMobChecker;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class FeatureEntityTrimmer extends ReactFeature implements Listener {
    public static final String ID = "entity-trimmer";
    private transient double maxPriority = -1;
    private transient int cooldown = 0;
    private transient boolean trimQueued = false;
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

    public FeatureEntityTrimmer() {
        super(ID);
    }

    @Override
    public void onActivate() {
        trimQueued = false;
        double highestPriority = -1;
        double lowestPriority = Double.MAX_VALUE;

        for (EntityType entityType : EntityType.values()) {
            double priority = ReactConfiguration.get().getPriority().getPriority(entityType);
            if (priority > highestPriority) {
                highestPriority = priority;
            }

            if (priority < lowestPriority) {
                lowestPriority = priority;
            }
        }

        maxPriority = M.lerp(Math.max(lowestPriority, 0), highestPriority, priorityPercentCutoff);
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
        if (cooldown-- > 0 || trimQueued) {
            return;
        }

        trimQueued = true;
        J.s(() -> {
            try {
                trimEntities();
            } finally {
                trimQueued = false;
            }
        });
    }

    private void trimEntities() {
        List<Entity> entities = collectEntities();
        if (entities.isEmpty()) {
            return;
        }

        int chunkRadius = Math.max(0, (playerMobBlockDistance + 15) >> 4);
        long maxPlayerDistanceSquared = (long) playerMobBlockDistance * playerMobBlockDistance;
        Map<ChunkKey, List<PlayerSnapshot>> playersByChunk = indexPlayersByChunk(chunkRadius);

        Map<ChunkKey, List<Entity>> entitiesPerChunk = new HashMap<>();
        Map<World, List<Entity>> entitiesPerWorld = new HashMap<>();
        Map<Player, List<Entity>> entitiesPerPlayer = new HashMap<>();
        Map<Entity, Double> priorityCache = new IdentityHashMap<>(entities.size());

        for (Entity entity : entities) {
            if (entity == null || entity.isDead()) {
                continue;
            }

            Location location = entity.getLocation();
            World world = location.getWorld();
            if (world == null) {
                continue;
            }

            ChunkKey key = ChunkKey.of(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
            entitiesPerChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(entity);
            entitiesPerWorld.computeIfAbsent(world, k -> new ArrayList<>()).add(entity);

            List<PlayerSnapshot> nearbyPlayers = playersByChunk.get(key);
            if (nearbyPlayers == null || nearbyPlayers.isEmpty()) {
                continue;
            }

            for (PlayerSnapshot playerSnapshot : nearbyPlayers) {
                if (playerSnapshot.location.distanceSquared(location) <= maxPlayerDistanceSquared) {
                    entitiesPerPlayer.computeIfAbsent(playerSnapshot.player, k -> new ArrayList<>()).add(entity);
                }
            }
        }

        Comparator<Entity> byPriority = Comparator.comparingDouble(entity -> priorityOf(entity, priorityCache));
        Set<Entity> candidates = new LinkedHashSet<>();
        addEntitiesToRemove(candidates, entitiesPerChunk.values(), softMaxEntitiesPerChunk, byPriority);
        addEntitiesToRemove(candidates, entitiesPerPlayer.values(), softMaxEntitiesPerPlayer, byPriority);
        addEntitiesToRemove(candidates, entitiesPerWorld.values(), softMaxEntitiesPerWorld, byPriority);

        if (candidates.isEmpty()) {
            return;
        }

        List<EntityCandidate> killList = new ArrayList<>(candidates.size());
        for (Entity entity : candidates) {
            if (!isValidTarget(entity)) {
                continue;
            }

            double priority = priorityOf(entity, priorityCache);
            if (priority < 0 || priority > maxPriority) {
                continue;
            }

            killList.add(new EntityCandidate(entity, priority));
        }

        if (killList.isEmpty()) {
            return;
        }

        killList.sort(Comparator.comparingDouble(EntityCandidate::priority));
        int maxKill = Math.min(killList.size(), (int) (killList.size() * opporunityThreshold));

        if (maxKill < minKillBatchSize) {
            cooldown += 3;
            return;
        }

        for (int i = 0; i < maxKill; i++) {
            kill(killList.get(i).entity);
        }

        if (printEntityPurgeSuccess) {
            React.success("Entity Trimmer: " + maxKill + " entities removed");
        }
    }

    private List<Entity> collectEntities() {
        List<Entity> entities = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            entities.addAll(world.getEntities());
        }

        return entities;
    }

    private Map<ChunkKey, List<PlayerSnapshot>> indexPlayersByChunk(int chunkRadius) {
        Map<ChunkKey, List<PlayerSnapshot>> chunks = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location playerLocation = player.getLocation();
            World world = playerLocation.getWorld();
            if (world == null) {
                continue;
            }

            PlayerSnapshot snapshot = new PlayerSnapshot(player, playerLocation.clone());
            int playerChunkX = playerLocation.getBlockX() >> 4;
            int playerChunkZ = playerLocation.getBlockZ() >> 4;

            for (int x = playerChunkX - chunkRadius; x <= playerChunkX + chunkRadius; x++) {
                for (int z = playerChunkZ - chunkRadius; z <= playerChunkZ + chunkRadius; z++) {
                    chunks.computeIfAbsent(ChunkKey.of(world, x, z), k -> new ArrayList<>()).add(snapshot);
                }
            }
        }

        return chunks;
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity.isDead()) {
            return false;
        }

        if (entity.getTicksLived() < 400) {
            return false;
        }

        if (blacklist.contains(entity.getType())) {
            return false;
        }

        return !skipCustomMobs || !CustomMobChecker.isCustom(entity);
    }

    private double priorityOf(Entity entity, Map<Entity, Double> cache) {
        return cache.computeIfAbsent(entity, ReactEntity::getPriority);
    }

    private void addEntitiesToRemove(Set<Entity> entitiesToRemove, Collection<List<Entity>> groups, int maxEntities, Comparator<Entity> byPriority) {
        if (maxEntities < 0) {
            return;
        }

        for (List<Entity> entities : groups) {
            if (entities.size() <= maxEntities) {
                continue;
            }

            entities.sort(byPriority);
            for (int i = maxEntities; i < entities.size(); i++) {
                entitiesToRemove.add(entities.get(i));
            }
        }
    }

    private void kill(Entity entity) {
        J.s(() -> React.kill(entity), ThreadLocalRandom.current().nextInt(20));
    }

    private static final class PlayerSnapshot {
        private final Player player;
        private final Location location;

        private PlayerSnapshot(Player player, Location location) {
            this.player = player;
            this.location = location;
        }
    }

    private static final class EntityCandidate {
        private final Entity entity;
        private final double priority;

        private EntityCandidate(Entity entity, double priority) {
            this.entity = entity;
            this.priority = priority;
        }

        private double priority() {
            return priority;
        }
    }

    private static final class ChunkKey {
        private final UUID world;
        private final int x;
        private final int z;

        private ChunkKey(UUID world, int x, int z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }

        private static ChunkKey of(World world, int x, int z) {
            return new ChunkKey(world.getUID(), x, z);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof ChunkKey key)) {
                return false;
            }

            return x == key.x && z == key.z && world.equals(key.world);
        }

        @Override
        public int hashCode() {
            int result = world.hashCode();
            result = 31 * result + x;
            result = 31 * result + z;
            return result;
        }
    }
}
