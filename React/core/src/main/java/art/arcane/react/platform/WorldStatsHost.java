package art.arcane.react.platform;

import art.arcane.react.api.sampler.Sampler;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * World, chunk and entity statistics. Methods that walk worlds are called on the main thread
 * unless noted otherwise.
 */
public interface WorldStatsHost {
  int worldCount();

  /**
   * Loaded chunks across all worlds, saturating at {@link Integer#MAX_VALUE}.
   */
  int loadedChunkCount();

  /**
   * Entities across all worlds, saturating at {@link Integer#MAX_VALUE}.
   */
  int entityCount();

  /**
   * @throws RuntimeException when this server cannot count block entities
   */
  long blockEntityCount();

  /**
   * @throws RuntimeException when this server cannot count ticking block entities
   */
  long tickingBlockEntityCount();

  /**
   * @throws RuntimeException when this server cannot list force-loaded chunks
   */
  long forceLoadedChunkCount();

  /**
   * Chunk tickets held by plugins or mods. Empty when this server has no way to count them.
   *
   * @throws RuntimeException when counting failed
   */
  OptionalLong pluginChunkTicketCount();

  /**
   * One entry per loaded world. Counts that cannot be read are 0.
   */
  List<WorldLoad> worldLoads();

  /**
   * Starts live entity tracking for the entities sampler: keeps {@code liveCount} in step with spawns,
   * loads, unloads and deaths, and adds each entity to {@code owner}'s chunk counter for the chunk it is in.
   */
  EntityTracking trackEntities(Sampler owner, AtomicInteger liveCount);

  void acquireEntityCensus();

  void releaseEntityCensus();

  void refreshEntityCensusOnMainThread();

  /**
   * Refresh used on region-threaded servers; may be called from any thread.
   */
  void refreshEntityCensusConcurrently();

  int entityCensus(EntityCategory category);

  record WorldLoad(UUID worldId, long entityCount, long chunkCount) {
  }

  interface EntityTracking {
    void stop();
  }

  enum EntityCategory {
    GROUND_ITEMS,
    HOSTILE,
    ANIMALS,
    VILLAGERS,
    PROJECTILES,
    PHYSICS,
    ACTIVE_AI
  }
}
