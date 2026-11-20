package art.arcane.react.platform;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Natural block changes: growth, spread, forming, fading, burning and leaf decay, plus the block reads and writes
 * the growth features make. Block states are the platform's own objects and only mean something to the host that
 * produced them.
 */
public interface GrowthHost {
  /**
   * {@link #leaf} result for a block that is not leaves.
   */
  int NOT_LEAVES = -1;

  /**
   * Bit set in a {@link #leaf} result when the leaves are persistent; the low bits hold the leaf distance.
   */
  int PERSISTENT_LEAVES = 1 << 8;

  interface ChangeEvent extends BlockHost.BlockEvent {
    /**
     * The block's state before the change.
     */
    Object state();
  }

  interface SpreadEvent extends ChangeEvent {
    /**
     * The block the spread came from.
     */
    GameLocation source();
  }

  interface GrowEvent extends ChangeEvent {
    /**
     * A private copy of the state the block grows into.
     */
    Object newState();
  }

  /**
   * A read-only view of one chunk's blocks, by in-chunk x and z (0-15) and world y.
   */
  interface ChunkBlocks {
    int minHeight();

    int maxHeight();

    int highestY(int x, int z);

    String type(int x, int y, int z);

    int leaf(int x, int y, int z);
  }

  /**
   * Picks an ageable block's new age from its current and maximum age.
   */
  @FunctionalInterface
  interface AgeStep {
    int targetAge(int age, int maximumAge);
  }

  Subscription onSpread(ListenerPriority priority, boolean ignoreCancelled, Consumer<SpreadEvent> listener);

  Subscription onGrow(ListenerPriority priority, boolean ignoreCancelled, Consumer<GrowEvent> listener);

  Subscription onForm(ListenerPriority priority, boolean ignoreCancelled, Consumer<ChangeEvent> listener);

  Subscription onFade(ListenerPriority priority, boolean ignoreCancelled, Consumer<ChangeEvent> listener);

  Subscription onBurn(ListenerPriority priority, boolean ignoreCancelled, Consumer<BlockHost.BlockEvent> listener);

  Subscription onLeavesDecay(ListenerPriority priority, boolean ignoreCancelled, Consumer<BlockHost.BlockEvent> listener);

  /**
   * The block type at a location, in React's config naming; null when its world is not loaded.
   */
  String type(GameLocation location);

  /**
   * The leaf state at a location: {@link #NOT_LEAVES}, or the distance with {@link #PERSISTENT_LEAVES} for
   * persistent leaves.
   */
  int leaf(GameLocation location);

  /**
   * A snapshot of a loaded chunk; null when the chunk or its world is not loaded.
   */
  ChunkBlocks chunk(UUID worldId, int chunkX, int chunkZ);

  /**
   * Moves an ageable block to the age {@code step} picks, without block updates; false when the block is not
   * ageable, the age stays the same or the write failed.
   */
  boolean advanceAge(GameLocation location, AgeStep step);

  /**
   * Applies bone meal once; true only when the block changed type, such as a sapling becoming a tree.
   */
  boolean boneMeal(GameLocation location);

  /**
   * Sets a block state taken from one of this host's events.
   */
  void setState(GameLocation location, Object state, boolean applyPhysics);

  /**
   * Sets a block state taken from one of this host's events, skipping block updates unless a neighbour depends on
   * the block.
   */
  void fastSet(GameLocation location, Object state);

  /**
   * Breaks a block with its drops; {@code fast} skips block updates unless a neighbour depends on the block.
   */
  void breakNaturally(GameLocation location, boolean fast);

  void playSound(GameLocation location, String sound, float volume, float pitch);

  int randomTickSpeed(UUID worldId);

  int simulationDistance(UUID worldId);

  /**
   * Player positions from React's player index; empty when no index runs.
   */
  Optional<List<PlayerPosition>> simulatingPlayers();

  /**
   * True when a player stands within {@code blocks} of the column, measured horizontally.
   */
  boolean anyPlayerInColumn(UUID worldId, double x, double z, double blocks);
}
