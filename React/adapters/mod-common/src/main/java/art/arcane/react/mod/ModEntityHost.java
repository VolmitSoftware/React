package art.arcane.react.mod;

import art.arcane.react.api.protect.ReactModProtection;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.ReactWorld;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entity access for features on mods. Mods carry no protection providers or custom-mob plugins, and React's
 * per-entity priority model is not tracked here yet, so every entity sits at the baseline priority.
 */
final class ModEntityHost implements EntityHost {
    private static final double BASELINE_PRIORITY = 100D;
    private final MinecraftServer server;
    private final ModEntityKiller killer;
    private final Map<UUID, long[]> worldCounts = new ConcurrentHashMap<>();
    private volatile List<String> types;

    ModEntityHost(MinecraftServer server, ModEntityKiller killer) {
        this.server = server;
        this.killer = killer;
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
        return BASELINE_PRIORITY;
    }

    @Override
    public double typePriority(String type) {
        return BASELINE_PRIORITY;
    }

    @Override
    public boolean isProtected(GameEntity entity, ReactOperation operation) {
        return ReactModProtection.isProtected(entity.handle(), operation);
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
        return false;
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
