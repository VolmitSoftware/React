package art.arcane.react.mod;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.mod.mixin.ChunkMapAccess;
import art.arcane.react.mod.mixin.LevelAccess;
import art.arcane.react.platform.WorldStatsHost;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

/** Counts read from the server's levels on the server thread. Entity totals are recounted rather than tracked live. */
final class ModWorldStatsHost implements WorldStatsHost {
    private static final EntityTracking UNTRACKED = () -> {
    };
    private final MinecraftServer server;
    private final ModOwnerGauges ownerGauges;
    private final AtomicInteger censusUsers = new AtomicInteger();
    private volatile Map<EntityCategory, Integer> census = new EnumMap<>(EntityCategory.class);

    ModWorldStatsHost(MinecraftServer server, ModOwnerGauges ownerGauges) {
        this.server = server;
        this.ownerGauges = ownerGauges;
    }

    @Override
    public int worldCount() {
        int count = 0;
        for (ServerLevel ignored : server.getAllLevels()) {
            count++;
        }
        return count;
    }

    @Override
    public int loadedChunkCount() {
        long count = 0L;
        for (ServerLevel level : server.getAllLevels()) {
            count += level.getChunkSource().getLoadedChunksCount();
        }
        return saturate(count);
    }

    @Override
    public int entityCount() {
        long count = 0L;
        for (ServerLevel level : server.getAllLevels()) {
            count += countEntities(level);
        }
        return saturate(count);
    }

    @Override
    public long blockEntityCount() {
        long total = 0L;
        for (ServerLevel level : server.getAllLevels()) {
            for (ChunkHolder holder : ((ChunkMapAccess) level.getChunkSource().chunkMap).react$visibleChunkMap().values()) {
                LevelChunk chunk = VanillaForms.fullChunk(holder);
                if (chunk != null) {
                    total += chunk.getBlockEntities().size();
                }
            }
        }
        return total;
    }

    @Override
    public long tickingBlockEntityCount() {
        long total = 0L;
        for (ServerLevel level : server.getAllLevels()) {
            total += ((LevelAccess) level).react$blockEntityTickers().size();
        }
        return total;
    }

    @Override
    public long forceLoadedChunkCount() {
        long total = 0L;
        for (ServerLevel level : server.getAllLevels()) {
            total += VanillaApi.forcedChunkCount(level);
        }
        return total;
    }

    @Override
    public OptionalLong pluginChunkTicketCount() {
        return ownerGauges.chunkTicketTotal();
    }

    @Override
    public List<WorldLoad> worldLoads() {
        List<WorldLoad> loads = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            loads.add(new WorldLoad(ModReactWorld.idOf(level), countEntities(level), level.getChunkSource().getLoadedChunksCount()));
        }
        return loads;
    }

    @Override
    public EntityTracking trackEntities(Sampler owner, AtomicInteger liveCount) {
        return UNTRACKED;
    }

    @Override
    public void acquireEntityCensus() {
        censusUsers.incrementAndGet();
    }

    @Override
    public void releaseEntityCensus() {
        censusUsers.updateAndGet(users -> Math.max(0, users - 1));
    }

    @Override
    public void refreshEntityCensusOnMainThread() {
        if (censusUsers.get() <= 0) {
            return;
        }
        Map<EntityCategory, Integer> counts = new EnumMap<>(EntityCategory.class);
        for (EntityCategory category : EntityCategory.values()) {
            counts.put(category, 0);
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                classify(entity, counts);
            }
        }
        census = counts;
    }

    @Override
    public void refreshEntityCensusConcurrently() {
        refreshEntityCensusOnMainThread();
    }

    @Override
    public int entityCensus(EntityCategory category) {
        return census.getOrDefault(category, 0);
    }

    private static void classify(Entity entity, Map<EntityCategory, Integer> counts) {
        if (entity.isRemoved()) {
            return;
        }
        if (entity instanceof ItemEntity) {
            counts.merge(EntityCategory.GROUND_ITEMS, 1, Integer::sum);
        }
        if (entity instanceof Enemy) {
            counts.merge(EntityCategory.HOSTILE, 1, Integer::sum);
        }
        if (entity instanceof Animal) {
            counts.merge(EntityCategory.ANIMALS, 1, Integer::sum);
        }
        EntityType<?> type = entity.getType();
        if (VanillaCompat.isTrader(type)) {
            counts.merge(EntityCategory.VILLAGERS, 1, Integer::sum);
        }
        if (entity instanceof Projectile) {
            counts.merge(EntityCategory.PROJECTILES, 1, Integer::sum);
        }
        if (entity instanceof PrimedTnt || entity instanceof FallingBlockEntity) {
            counts.merge(EntityCategory.PHYSICS, 1, Integer::sum);
        }
        if (entity instanceof Mob mob && !mob.isNoAi()) {
            counts.merge(EntityCategory.ACTIVE_AI, 1, Integer::sum);
        }
    }

    private static long countEntities(ServerLevel level) {
        long count = 0L;
        for (Entity ignored : level.getAllEntities()) {
            count++;
        }
        return count;
    }

    private static int saturate(long count) {
        return (int) Math.min(Integer.MAX_VALUE, count);
    }
}
