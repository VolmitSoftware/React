package art.arcane.react.mod;

import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.core.controller.LoadedChunkCoordinate;
import art.arcane.react.mod.mixin.ChunkMapAccess;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.platform.ChunkHeatmapHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.WorldBorder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Heatmap data on a mod server. Loaded chunks, per-chunk entity counts and player positions are captured on the
 * server thread once a second while a heatmap was drawn in the last {@value #DEMAND_WINDOW_MS} ms; render threads
 * read the latest capture. Event counts come from {@link ModChunkSamples}.
 */
final class ModChunkHeatmapHost implements ChunkHeatmapHost {
    static final int REFRESH_TICKS = 20;
    private static final long DEMAND_WINDOW_MS = 10_000L;
    private final MinecraftServer server;
    private final ModChunkSamples samples;
    private volatile Map<UUID, WorldCapture> worlds = Map.of();
    private volatile long lastReadMs;

    private record WorldCapture(HeatmapWorldRef ref, Set<Long> loaded, List<PlayerPosition> players) {
    }

    ModChunkHeatmapHost(MinecraftServer server, ModChunkSamples samples) {
        this.server = server;
        this.samples = samples;
    }

    boolean demanded() {
        return System.currentTimeMillis() - lastReadMs <= DEMAND_WINDOW_MS;
    }

    /** Server thread. */
    void refresh() {
        Map<UUID, WorldCapture> captured = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            Set<Long> loaded = new HashSet<>();
            for (ChunkHolder holder : ((ChunkMapAccess) level.getChunkSource().chunkMap).react$visibleChunkMap().values()) {
                if (holder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).orElse(null) != null) {
                    loaded.add(holder.getPos().pack());
                }
            }
            Map<Long, Integer> entities = new HashMap<>();
            for (Entity entity : level.getAllEntities()) {
                entities.merge(entity.chunkPosition().pack(), 1, Integer::sum);
            }
            samples.replaceGauge(level, SamplerEntities.ID, entities);
            List<PlayerPosition> players = new ArrayList<>();
            for (ServerPlayer player : level.players()) {
                players.add(new PlayerPosition(player.getUUID(), player.getScoreboardName(), player.getX(), player.getZ(),
                        player.getDeltaMovement().length(), player.isFallFlying(), player.isPassenger()));
            }
            UUID worldId = ModReactWorld.idOf(level);
            captured.put(worldId, new WorldCapture(worldRef(worldId, level), Set.copyOf(loaded), List.copyOf(players)));
        }
        worlds = Map.copyOf(captured);
        samples.decayIfDue(System.currentTimeMillis());
    }

    /** Server thread. Drops the entity counts once no heatmap reads them. */
    void release() {
        for (ServerLevel level : server.getAllLevels()) {
            samples.replaceGauge(level, SamplerEntities.ID, Map.of());
        }
    }

    @Override
    public Optional<HeatmapWorldRef> heatmapWorld(UUID worldId) {
        lastReadMs = System.currentTimeMillis();
        WorldCapture world = worldId == null ? null : worlds.get(worldId);
        return world == null ? Optional.empty() : Optional.of(world.ref());
    }

    @Override
    public List<LoadedChunkCoordinate> loadedChunks(UUID worldId, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        WorldCapture world = worldId == null ? null : worlds.get(worldId);
        if (world == null || minChunkX > maxChunkX || minChunkZ > maxChunkZ) {
            return List.of();
        }
        long cells = ((long) maxChunkX - minChunkX + 1L) * ((long) maxChunkZ - minChunkZ + 1L);
        List<LoadedChunkCoordinate> result = new ArrayList<>();
        if (world.loaded().size() <= cells) {
            for (long key : world.loaded()) {
                int chunkX = ChunkPos.getX(key);
                int chunkZ = ChunkPos.getZ(key);
                if (chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ) {
                    result.add(new LoadedChunkCoordinate(chunkX, chunkZ));
                }
            }
            return result;
        }
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (world.loaded().contains(ChunkPos.pack(chunkX, chunkZ))) {
                    result.add(new LoadedChunkCoordinate(chunkX, chunkZ));
                }
            }
        }
        return result;
    }

    @Override
    public double chunkSample(String worldKey, int chunkX, int chunkZ, String samplerId) {
        return samples.value(worldKey, chunkX, chunkZ, samplerId);
    }

    @Override
    public double chunkTotalScore(String worldKey, int chunkX, int chunkZ) {
        return samples.chunk(worldKey, chunkX, chunkZ).map(SampledChunk::totalScore).orElse(0D);
    }

    @Override
    public List<PlayerPosition> playersInColumn(UUID worldId, double x, double z, double radiusBlocks) {
        WorldCapture world = worldId == null ? null : worlds.get(worldId);
        if (world == null || radiusBlocks <= 0D) {
            return List.of();
        }
        double maximum = radiusBlocks * radiusBlocks;
        List<PlayerPosition> result = new ArrayList<>();
        for (PlayerPosition player : world.players()) {
            double dx = player.x() - x;
            double dz = player.z() - z;
            if ((dx * dx) + (dz * dz) <= maximum) {
                result.add(player);
            }
        }
        return result;
    }

    private static HeatmapWorldRef worldRef(UUID worldId, ServerLevel level) {
        String name = ModReactWorld.nameOf(level);
        BlockPos spawn = level.getRespawnData().pos();
        WorldBorder border = level.getWorldBorder();
        double size = border.getSize();
        return new HeatmapWorldRef(worldId, name, name, spawn.getX() >> 4, spawn.getZ() >> 4, border.getCenterX(),
                border.getCenterZ(), Double.isFinite(size) && size >= 0D ? size : 0D);
    }
}
