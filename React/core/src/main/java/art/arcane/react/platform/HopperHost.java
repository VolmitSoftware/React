package art.arcane.react.platform;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Hopper item transfers, hopper ticks and the hopper and dropped-item state React's hopper features index. Block
 * reads and writes run on the thread that owns the block; item handles are the platform's own entity objects.
 */
public interface HopperHost {
  interface Move {
    /**
     * The hopper block taking part in the move, the source when both sides are hoppers; null when neither side is
     * a hopper block, as with hopper minecarts.
     */
    GameLocation hopper();

    boolean sourceIsHopper();

    boolean destinationIsHopper();

    boolean isCancelled();

    void cancel();
  }

  @FunctionalInterface
  interface MoveListener {
    void onMove(Move move);
  }

  Subscription onMove(MoveListener listener);

  /**
   * A hopper block. {@code facing} is where it pushes items, or null when the platform cannot tell.
   */
  record HopperBlock(int x, int y, int z, Facing facing) {
  }

  /**
   * Decides, right before a hopper block ticks, whether that tick is skipped.
   */
  @FunctionalInterface
  interface TickFilter {
    boolean skip(UUID worldId, int x, int y, int z);
  }

  /**
   * Changes React's hopper and dropped-item indexes follow. Item handles are the platform's entity objects.
   */
  interface IndexListener {
    void itemSeen(Object item);

    void itemGone(UUID itemId);

    void hopperPlaced(UUID worldId, int x, int y, int z);

    void hopperGone(UUID worldId, int x, int y, int z);
  }

  /**
   * A hopper block's transfer state, for moving items into it without its own entity scan.
   */
  interface HopperAccess {
    /**
     * Moves as much of the item entity's stack as fits; true when anything moved.
     */
    boolean addItem(Object item);

    boolean isEmpty();

    int cooldown();

    void cooldown(int ticks);
  }

  /**
   * The platform's handle of a chunk the caller already knows is loaded; null when the world is not loaded.
   */
  Object chunk(UUID worldId, int chunkX, int chunkZ);

  /**
   * Hopper block entities of a loaded chunk, read without block snapshots.
   */
  List<HopperBlock> hoppers(Object chunk);

  /**
   * The block type at a position, in React's config naming; null when the world is not loaded.
   */
  String blockType(UUID worldId, int x, int y, int z);

  /**
   * True when the block at the position holds an inventory.
   */
  boolean isContainer(UUID worldId, int x, int y, int z);

  /**
   * Moves one item from the first filled slot of the source container into the target container. {@code permit}
   * runs once before the move and can veto it. False when nothing moved.
   */
  boolean transferOne(UUID worldId, int fromX, int fromY, int fromZ, int toX, int toY, int toZ, BooleanSupplier permit);

  boolean isOwnedByCurrentRegion(UUID worldId, double x, double y, double z);

  /**
   * Installs the hopper tick filter once the platform's hook is ready and reports whether it took. Cancelling the
   * subscription removes it. Without a tick hook the filter is never installed and nothing is reported.
   */
  Subscription installTickFilter(TickFilter filter, Consumer<Boolean> installed);

  Subscription onIndexChange(IndexListener listener);

  /**
   * True when {@link #hopperAccess} can reach hopper blocks on this platform.
   */
  boolean supportsHopperAccess();

  /**
   * Transfer state of the hopper block at the position; null when there is none.
   */
  HopperAccess hopperAccess(UUID worldId, int x, int y, int z);

  /**
   * The entity handle of a loaded dropped item; null when the id is not a loaded item.
   */
  Object item(UUID itemId);

  UUID itemId(Object item);

  /**
   * True while the item entity is valid and not dead.
   */
  boolean isLiveItem(Object item);

  /**
   * The item's position; null when it has no world.
   */
  GameLocation itemLocation(Object item);

  /**
   * Every entity in a loaded chunk, as platform handles.
   */
  Object[] chunkEntities(Object chunk);

  boolean isItem(Object entity);

  /**
   * Online players whose regions can anchor region-local work; empty off regionized servers.
   */
  Object[] regionPlayers();

  /**
   * A player's position; null when the player is offline or has no world.
   */
  GameLocation playerLocation(Object player);

  /**
   * Merges similar dropped item stacks near the center, removing emptied entities; the number removed.
   */
  int mergeNearbyItems(UUID worldId, double x, double y, double z, double radius, int maxMergedEntities);

  /**
   * A sampled chunk's current value for one sampler, keyed by the world's config name.
   */
  record SampledChunkRate(String worldKey, int chunkX, int chunkZ, double rate) {
  }

  /**
   * Every sampled chunk with its value for the sampler, 0 where the chunk has none.
   */
  List<SampledChunkRate> sampledChunks(String samplerId);

  int minBuildHeight(UUID worldId);

  /**
   * Unloads the chunk, saving it; true when it is no longer loaded afterwards.
   */
  boolean unloadChunk(UUID worldId, int chunkX, int chunkZ);
}
