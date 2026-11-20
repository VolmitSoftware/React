package art.arcane.react.platform;

import art.arcane.react.core.controller.LoadedChunkCoordinate;
import art.arcane.react.model.SampledChunk;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface ChunkHost {
  /**
   * A world about to unload. Cancelling keeps it loaded where the platform allows it; mod servers close their worlds
   * regardless.
   */
  interface WorldUnload extends HostEvent {
    UUID worldId();
  }

  /**
   * Streams the chunks that were loaded when the cursor opened, in batches of at most 256.
   */
  interface LoadedChunkCursor {
    LoadedChunkCursor EMPTY = new LoadedChunkCursor() {
      @Override
      public List<ChunkRef> next(int maximum) {
        return List.of();
      }

      @Override
      public int estimatedTotal() {
        return 0;
      }
    };

    List<ChunkRef> next(int maximum);

    int estimatedTotal();
  }

  /**
   * The loaded world with this namespaced key, such as {@code minecraft:overworld}.
   */
  Optional<ReactWorld> world(String worldKey);

  Optional<ReactWorld> world(UUID worldId);

  String worldKey(ReactWorld world);

  boolean isLoaded(ReactWorld world, int chunkX, int chunkZ);

  /**
   * True when the chunk was fully generated before, loaded or not.
   */
  boolean isGenerated(ReactWorld world, int chunkX, int chunkZ);

  /**
   * Asks the server to unload a loaded chunk, saving it. False when something still keeps it loaded.
   */
  boolean unload(ReactWorld world, int chunkX, int chunkZ);

  /**
   * The platform chunk of a loaded chunk, or null when it is not loaded. Call from the chunk's owning thread.
   */
  Object loadedChunk(ReactWorld world, int chunkX, int chunkZ);

  /**
   * Loads the chunk without blocking, completing with its platform chunk, or null when it does not exist and
   * {@code generate} is false.
   */
  CompletableFuture<Object> load(ReactWorld world, int chunkX, int chunkZ, boolean generate);

  /**
   * Touches a loaded chunk's entities, and its block snapshot when asked, so later access is warm.
   */
  void warm(Object chunk, boolean touchSnapshot);

  /**
   * True when the current thread owns the chunk's region.
   */
  boolean ownsChunk(ReactWorld world, int chunkX, int chunkZ);

  /**
   * True once React's loaded-chunk index has seen every chunk that was loaded at startup, or when there is no
   * index to wait for.
   */
  boolean isLoadedIndexReady();

  LoadedChunkCursor openLoadedCursor();

  LoadedChunkCursor openLoadedCursor(UUID worldId);

  List<LoadedChunkCoordinate> loadedInRadius(UUID worldId, int centerX, int centerZ, int radius);

  /**
   * Every chunk the samplers attributed values to, with their world key and per-sampler values.
   */
  List<SampledChunk> sampledChunks();

  /**
   * True when a player may stand within {@code radius} blocks of the column, including when React's player index
   * cannot tell yet.
   */
  boolean mayHavePlayerNear(ReactWorld world, double x, double z, double radius);

  boolean isLoaded(UUID worldId, int chunkX, int chunkZ);

  /**
   * The next loaded chunks from a cursor that walks every loaded chunk in turn, at most {@code maximum} of them.
   */
  List<ChunkRef> nextLoaded(int maximum);

  Subscription onChunkLoad(ListenerPriority priority, Consumer<ChunkRef> listener);

  Subscription onChunkUnload(ListenerPriority priority, Consumer<ChunkRef> listener);

  Subscription onWorldUnload(ListenerPriority priority, boolean ignoreCancelled, Consumer<WorldUnload> listener);
}
