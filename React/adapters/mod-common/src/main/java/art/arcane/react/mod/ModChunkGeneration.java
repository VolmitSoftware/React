package art.arcane.react.mod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Whether chunks are generated, as Bukkit's {@code isChunkGenerated} answers it: loaded chunks are, stored chunks are
 * when saved at full status. Stored chunks are read on the chunk IO worker, never on the server thread; answers are
 * cached, so a chunk is read once and later checks are exact. Until its first read finishes, a chunk counts as
 * generated.
 */
final class ModChunkGeneration {
    private static final String FULL_STATUS = "minecraft:full";
    private static final long MISSING_RETENTION_MS = 5_000L;
    private static final int MAX_ENTRIES = 65_536;
    private static final Map<Key, Entry> CACHE = new ConcurrentHashMap<>();
    private static final Map<Key, CompletableFuture<Boolean>> READS = new ConcurrentHashMap<>();
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private record Key(ServerLevel level, long chunk) {
    }

    private record Entry(boolean generated, long expiresAtMs) {
    }

    private ModChunkGeneration() {
    }

    static void clear() {
        GENERATION.incrementAndGet();
        CACHE.clear();
        READS.clear();
    }

    /** Never waits for the disk; reads the chunk in the background when its state is not known yet. */
    static boolean generated(ServerLevel level, int chunkX, int chunkZ) {
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
            return true;
        }
        Entry entry = CACHE.get(new Key(level, VanillaApi.pack(chunkX, chunkZ)));
        if (entry != null && (entry.generated() || System.currentTimeMillis() < entry.expiresAtMs())) {
            return entry.generated();
        }
        read(level, chunkX, chunkZ);
        return true;
    }

    /** Reads the chunk's stored state and its neighbours' in the background, so the next checks around it are exact. */
    static void prefetchAround(ServerLevel level, int chunkX, int chunkZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz) == null) {
                    Entry entry = CACHE.get(new Key(level, VanillaApi.pack(chunkX + dx, chunkZ + dz)));
                    if (entry == null || (!entry.generated() && System.currentTimeMillis() >= entry.expiresAtMs())) {
                        read(level, chunkX + dx, chunkZ + dz);
                    }
                }
            }
        }
    }

    /** Completes with the exact answer once the chunk's stored state was read. */
    static CompletableFuture<Boolean> read(ServerLevel level, int chunkX, int chunkZ) {
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
            return CompletableFuture.completedFuture(true);
        }
        Key key = new Key(level, VanillaApi.pack(chunkX, chunkZ));
        CompletableFuture<Boolean> pending = READS.get(key);
        if (pending != null) {
            return pending;
        }
        CompletableFuture<Boolean> created = new CompletableFuture<>();
        int generation = GENERATION.get();
        pending = READS.putIfAbsent(key, created);
        if (pending != null) {
            return pending;
        }
        try {
            level.getChunkSource().chunkMap.read(new ChunkPos(chunkX, chunkZ)).whenComplete((Optional<CompoundTag> stored, Throwable failure) -> {
                boolean generated = failure != null
                        || (stored.isPresent() && FULL_STATUS.equals(VanillaApi.stringOr(stored.get(), "Status", "")));
                if (failure == null && generation == GENERATION.get()) {
                    if (CACHE.size() >= MAX_ENTRIES) {
                        CACHE.clear();
                    }
                    CACHE.put(key, new Entry(generated, generated ? Long.MAX_VALUE : System.currentTimeMillis() + MISSING_RETENTION_MS));
                }
                READS.remove(key, created);
                created.complete(generated);
            });
        } catch (RuntimeException failure) {
            READS.remove(key, created);
            created.complete(true);
        }
        return created;
    }
}
