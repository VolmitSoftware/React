package art.arcane.react.mod;

import art.arcane.react.api.entity.EntityPriority;
import art.arcane.react.api.protect.ReactModProtection;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactOperations;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Entity access for features on mods. Priority, crowding and nearest-player factors come from React's own model, read
 * from vanilla state. Protection combines mods' claims and rules from {@link ReactModProtection} with React's built-in
 * rule, the {@code volmit:no-stack} marker, which on mods is a scoreboard tag; guard events are Bukkit-only.
 * A custom mob is a mob whose type comes from another mod, the closest match to Bukkit's MythicMobs-driven mobs: its
 * own code owns its behaviour. React's persistent markers are scoreboard tags in React's namespace, so they are saved
 * with the entity and removed exactly when Bukkit removes its data keys.
 */
final class ModEntityHost implements EntityHost {
    static final String NO_STACK_MARKER = "volmit:no-stack";
    private static final String TAG_PREFIX = "react:";
    private static final int MARKER_OPERATIONS = ReactOperations.of(ReactOperation.STACK, ReactOperation.TRIM,
            ReactOperation.PURGE, ReactOperation.SLEEP, ReactOperation.DESPAWN);
    private final MinecraftServer server;
    private final ModEntityKiller killer;
    private final ModEntityTicker ticker;
    private final Map<UUID, long[]> worldCounts = new ConcurrentHashMap<>();
    private volatile List<String> types;

    ModEntityHost(MinecraftServer server, ModEntityKiller killer, ModEntityTicker ticker) {
        this.server = server;
        this.killer = killer;
        this.ticker = ticker;
    }

    @Override
    public List<GameEntity> nearby(ReactPlayer player, double radius) {
        if (player == null || !(player.handle() instanceof ServerPlayer handle) || !(handle.level() instanceof ServerLevel level)) {
            return List.of();
        }
        return wrap(level.getEntities(handle, handle.getBoundingBox().inflate(radius), (Entity entity) -> true));
    }

    @Override
    public List<GameEntity> entities(ReactWorld world) {
        List<GameEntity> result = new ArrayList<>();
        for (Entity entity : ((ServerLevel) world.handle()).getAllEntities()) {
            if (entity != null) {
                result.add(new ModGameEntity(entity));
            }
        }
        return result;
    }

    @Override
    public List<GameEntity> chunkEntities(ReactWorld world, int chunkX, int chunkZ) {
        ServerLevel level = (ServerLevel) world.handle();
        return isLoaded(level, chunkX, chunkZ) ? wrap(chunk(level, chunkX, chunkZ)) : List.of();
    }

    @Override
    public int chunkEntityCount(UUID worldId, int chunkX, int chunkZ) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        return level == null || !isLoaded(level, chunkX, chunkZ) ? -1 : chunk(level, chunkX, chunkZ).size();
    }

    @Override
    public int worldEntityCount(UUID worldId) {
        ServerLevel level = ModWorldHost.level(server, worldId);
        if (level == null) {
            return 0;
        }
        long tick = server.getTickCount();
        long[] cached = worldCounts.get(worldId);
        if (cached != null && cached[0] == tick) {
            return (int) cached[1];
        }
        int count = 0;
        for (Entity ignored : level.getAllEntities()) {
            count++;
        }
        worldCounts.put(worldId, new long[]{tick, count});
        return count;
    }

    @Override
    public Collection<String> entityTypes() {
        List<String> known = types;
        if (known == null) {
            List<String> names = new ArrayList<>();
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                names.add(ModGameEntity.typeName(type));
            }
            known = List.copyOf(names);
            types = known;
        }
        return known;
    }

    @Override
    public double priority(GameEntity entity) {
        return ReactEntity.getPriority(entity);
    }

    @Override
    public double typePriority(String type) {
        return EntityPriority.active().getPriority(type);
    }

    @Override
    public boolean isProtected(GameEntity entity, ReactOperation operation) {
        if (ReactModProtection.isProtected(entity.handle(), operation)) {
            return true;
        }
        return entity.handle() instanceof Entity handle && ReactOperations.covers(MARKER_OPERATIONS, operation)
                && VanillaApi.tags(handle).contains(NO_STACK_MARKER);
    }

    @Override
    public boolean allows(GameEntity entity, ReactOperation operation) {
        return !ReactModProtection.isProtected(entity.handle(), operation);
    }

    @Override
    public boolean protectsNamed(GameEntity entity, boolean protectNamedEntities) {
        if (!protectNamedEntities || !(entity.handle() instanceof Entity handle) || killer.isCountingDown(handle)) {
            return false;
        }
        return handle.hasCustomName() && !handle.getCustomName().getString().isBlank();
    }

    @Override
    public boolean isCustomMob(GameEntity entity) {
        if (!(entity.handle() instanceof Mob mob)) {
            return false;
        }
        String namespace = VanillaApi.namespace(mob.getType());
        return namespace != null && !"minecraft".equals(namespace);
    }

    @Override
    public void remove(GameEntity entity) {
        if (entity.handle() instanceof Entity handle) {
            killer.kill(handle);
        }
    }

    // Chunk quarantine culling

    @Override
    public boolean isDead(GameEntity entity) {
        return !(entity.handle() instanceof Entity handle) || handle.isRemoved() || !handle.isAlive();
    }

    @Override
    public boolean isTamed(GameEntity entity) {
        return entity.handle() instanceof TamableAnimal tamable && tamable.isTame();
    }

    @Override
    public boolean isVillager(GameEntity entity) {
        return entity.handle() instanceof Entity handle && isVanilla(handle.getType(), "villager");
    }

    @Override
    public boolean isBoss(GameEntity entity) {
        if (!(entity.handle() instanceof Entity handle)) {
            return false;
        }
        EntityType<?> type = handle.getType();
        return isVanilla(type, "ender_dragon") || isVanilla(type, "wither") || isVanilla(type, "warden");
    }

    private static boolean isVanilla(EntityType<?> type, String path) {
        return "minecraft".equals(VanillaApi.namespace(type)) && path.equals(VanillaApi.path(type));
    }

    @Override
    public void removeNow(GameEntity entity) {
        if (entity.handle() instanceof Entity handle) {
            handle.discard();
        }
    }

    @Override
    public Subscription onTick(Collection<String> types, Consumer<GameEntity> listener) {
        return ticker.subscribe(types, listener);
    }

    @Override
    public GameEntity wrap(Object handle) {
        return new ModGameEntity((Entity) handle);
    }

    @Override
    public List<GameEntity> nearbyEntities(GameEntity entity, double radius) {
        Entity handle = (Entity) entity.handle();
        if (!(handle.level() instanceof ServerLevel level)) {
            return List.of();
        }
        return wrap(level.getEntities(handle, handle.getBoundingBox().inflate(radius), (Entity other) -> true));
    }

    @Override
    public double nearestPlayerDistanceSquared(GameEntity entity, double maxDistance) {
        Entity handle = (Entity) entity.handle();
        if (!(handle.level() instanceof ServerLevel level)) {
            return Double.POSITIVE_INFINITY;
        }
        double limit = maxDistance * maxDistance;
        double nearest = Double.POSITIVE_INFINITY;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            double distance = player.distanceToSqr(handle);
            if (distance <= limit && distance < nearest) {
                nearest = distance;
            }
        }
        return nearest;
    }

    @Override
    public double itemValue(GameEntity entity) {
        if (!(entity.handle() instanceof ItemEntity item)) {
            return 0D;
        }
        ItemStack stack = item.getItem();
        return ModItemValue.value(server, stack.getItem()) * stack.getCount();
    }

    @Override
    public int persistentInt(GameEntity entity, String key, int fallback) {
        String prefix = TAG_PREFIX + key + "=";
        for (String tag : VanillaApi.tags((Entity) entity.handle())) {
            if (tag.startsWith(prefix)) {
                try {
                    return Integer.parseInt(tag.substring(prefix.length()));
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    @Override
    public void setPersistentInt(GameEntity entity, String key, int value) {
        Entity handle = (Entity) entity.handle();
        String prefix = TAG_PREFIX + key + "=";
        List<String> stale = new ArrayList<>();
        for (String tag : VanillaApi.tags(handle)) {
            if (tag.startsWith(prefix)) {
                stale.add(tag);
            }
        }
        for (String tag : stale) {
            handle.removeTag(tag);
        }
        handle.addTag(prefix + value);
    }

    @Override
    public boolean persistentFlag(GameEntity entity, String key) {
        return VanillaApi.tags((Entity) entity.handle()).contains(TAG_PREFIX + key);
    }

    @Override
    public void setPersistentFlag(GameEntity entity, String key, boolean value) {
        Entity handle = (Entity) entity.handle();
        if (value) {
            handle.addTag(TAG_PREFIX + key);
        } else {
            handle.removeTag(TAG_PREFIX + key);
        }
    }

    @Override
    public boolean hasAi(GameEntity entity) {
        return entity.handle() instanceof Mob mob && !mob.isNoAi();
    }

    @Override
    public void setAi(GameEntity entity, boolean ai) {
        if (entity.handle() instanceof Mob mob) {
            mob.setNoAi(!ai);
        }
    }

    @Override
    public boolean isAware(GameEntity entity) {
        return entity.handle() instanceof ModAwareness mob && mob.react$isAware();
    }

    @Override
    public void setAware(GameEntity entity, boolean aware) {
        if (entity.handle() instanceof ModAwareness mob) {
            mob.react$setAware(aware);
        }
    }

    public void kill(GameEntity entity, int seconds) {
        if (entity.handle() instanceof Entity handle) {
            killer.kill(handle, seconds);
        }
    }

    private static boolean isLoaded(ServerLevel level, int chunkX, int chunkZ) {
        return level.getChunkSource().hasChunk(chunkX, chunkZ) && level.areEntitiesLoaded(VanillaApi.pack(chunkX, chunkZ));
    }

    private static List<Entity> chunk(ServerLevel level, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        AABB box = new AABB(minX, VanillaApi.minY(level), minZ, minX + 16, VanillaApi.maxY(level) + 1, minZ + 16);
        return level.getEntities((Entity) null, box, (Entity entity) -> entity.getBlockX() >> 4 == chunkX
                && entity.getBlockZ() >> 4 == chunkZ);
    }

    private static List<GameEntity> wrap(List<Entity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        List<GameEntity> result = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            result.add(new ModGameEntity(entity));
        }
        return result;
    }
}
