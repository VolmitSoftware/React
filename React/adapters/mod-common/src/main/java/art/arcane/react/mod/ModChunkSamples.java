package art.arcane.react.mod;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.model.SampledChunk;
import com.google.common.util.concurrent.AtomicDouble;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sampler values attributed to chunks, keyed by dimension id. Counters halve once a second like the Bukkit observer's,
 * and chunks with nothing left are dropped.
 */
final class ModChunkSamples {
    private static final long DECAY_INTERVAL_MS = 1_000L;
    private final Map<String, Map<Long, SampledChunk>> worlds = new ConcurrentHashMap<>();
    private final AtomicLong nextDecayMs = new AtomicLong();

    AtomicDouble counter(Object subject, Sampler sampler) {
        decayIfDue(System.currentTimeMillis());
        SampledChunk chunk = chunkOf(subject);
        if (chunk == null) {
            return new AtomicDouble();
        }
        return sampler.isChunkGauge() ? chunk.gauge(sampler.getId()) : chunk.get(sampler.getId());
    }

    Optional<Double> sample(Object subject, Sampler sampler) {
        if (!(subject instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        ChunkPos pos = chunk.getPos();
        return chunk(ModReactWorld.nameOf(level), pos.x(), pos.z())
                .flatMap((SampledChunk sampled) -> sampled.optional(sampler.getId()))
                .map(AtomicDouble::get);
    }

    Optional<SampledChunk> chunk(String worldKey, int chunkX, int chunkZ) {
        Map<Long, SampledChunk> chunks = worldKey == null ? null : worlds.get(worldKey);
        return chunks == null ? Optional.empty() : Optional.ofNullable(chunks.get(ChunkPos.pack(chunkX, chunkZ)));
    }

    double value(String worldKey, int chunkX, int chunkZ, String samplerId) {
        return chunk(worldKey, chunkX, chunkZ)
                .flatMap((SampledChunk sampled) -> sampled.optional(samplerId))
                .map(AtomicDouble::get)
                .orElse(0D);
    }

    /** Replaces a gauge for every chunk of one world; chunks missing from {@code values} lose it. */
    void replaceGauge(ServerLevel level, String samplerId, Map<Long, Integer> values) {
        String worldKey = ModReactWorld.nameOf(level);
        Map<Long, SampledChunk> chunks = worlds.computeIfAbsent(worldKey, (String ignored) -> new ConcurrentHashMap<>());
        for (Map.Entry<Long, SampledChunk> entry : chunks.entrySet()) {
            if (!values.containsKey(entry.getKey())) {
                entry.getValue().getValues().remove(samplerId);
            }
        }
        for (Map.Entry<Long, Integer> entry : values.entrySet()) {
            long key = entry.getKey();
            chunks.computeIfAbsent(key, (Long ignored) -> new SampledChunk(ModReactWorld.idOf(level), worldKey,
                    ChunkPos.getX(key), ChunkPos.getZ(key))).gauge(samplerId).set(entry.getValue());
        }
    }

    void decayIfDue(long nowMs) {
        long due = nextDecayMs.get();
        if (nowMs < due || !nextDecayMs.compareAndSet(due, nowMs + DECAY_INTERVAL_MS)) {
            return;
        }
        for (Map<Long, SampledChunk> chunks : worlds.values()) {
            Iterator<SampledChunk> iterator = chunks.values().iterator();
            while (iterator.hasNext()) {
                SampledChunk chunk = iterator.next();
                chunk.decay();
                if (chunk.isEmpty()) {
                    iterator.remove();
                }
            }
        }
    }

    private SampledChunk chunkOf(Object subject) {
        ServerLevel level;
        int chunkX;
        int chunkZ;
        if (subject instanceof ModBlockRef block) {
            level = block.level();
            chunkX = block.pos().getX() >> 4;
            chunkZ = block.pos().getZ() >> 4;
        } else if (subject instanceof LevelChunk chunk && chunk.getLevel() instanceof ServerLevel chunkLevel) {
            level = chunkLevel;
            chunkX = chunk.getPos().x();
            chunkZ = chunk.getPos().z();
        } else {
            return null;
        }
        String worldKey = ModReactWorld.nameOf(level);
        return worlds.computeIfAbsent(worldKey, (String ignored) -> new ConcurrentHashMap<>())
                .computeIfAbsent(ChunkPos.pack(chunkX, chunkZ),
                        (Long ignored) -> new SampledChunk(ModReactWorld.idOf(level), worldKey, chunkX, chunkZ));
    }
}
