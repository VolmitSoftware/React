package art.arcane.react.mod;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.sampler.SamplerHosts;
import art.arcane.react.content.feature.TickSpikeReplay;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.LoadedChunkCoordinate;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.platform.PlatformScheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Feeds the tick spike replay map on a mod server: every quarter second, a slow tick leaves heat around the costliest
 * chunk, as the Bukkit feature does.
 */
final class ModTickSpikeReplay {
    private static final int TICK_INTERVAL_TICKS = 5;
    private final MinecraftServer server;
    private final ModChunkSamples samples;
    private final TickSpikeReplay replay = new TickSpikeReplay(TickSpikeReplay.DEFAULT_SETTINGS);

    ModTickSpikeReplay(MinecraftServer server, ModChunkSamples samples) {
        this.server = server;
        this.samples = samples;
        replay.reset(System.currentTimeMillis());
    }

    TickSpikeReplay replay() {
        return replay;
    }

    void schedule(PlatformScheduler scheduler) {
        scheduler.global(() -> {
            tick();
            schedule(scheduler);
        }, TICK_INTERVAL_TICKS);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Sampler tickTime = SamplerHosts.sampler(SamplerTickTime.ID);
        double tickMs = tickTime == null ? 0D : tickTime.sample();
        if (replay.claimCapture(now, tickMs)) {
            samples.worst().ifPresent((SampledChunk worst) -> capture(worst, tickMs, now));
        }
        replay.decayIfDue(now);
    }

    private void capture(SampledChunk worst, double tickMs, long now) {
        UUID worldId = worst.getWorldId();
        String worldKey = worst.getWorldKey();
        ServerLevel level = level(worldId);
        if (level == null || worldKey == null) {
            return;
        }
        int originX = worst.getChunkX();
        int originZ = worst.getChunkZ();
        int radius = Math.max(0, replay.settings().captureRadiusChunks());
        List<LoadedChunkCoordinate> loaded = new ArrayList<>();
        for (int chunkX = originX - radius; chunkX <= originX + radius; chunkX++) {
            for (int chunkZ = originZ - radius; chunkZ <= originZ + radius; chunkZ++) {
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
                    loaded.add(new LoadedChunkCoordinate(chunkX, chunkZ));
                }
            }
        }
        replay.captureScored(worldId, originX, originZ, loaded,
                (int chunkX, int chunkZ) -> samples.chunk(worldKey, chunkX, chunkZ).map(SampledChunk::totalScore).orElse(0D),
                tickMs, now);
    }

    private ServerLevel level(UUID worldId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (ModReactWorld.idOf(level).equals(worldId)) {
                return level;
            }
        }
        return null;
    }
}
