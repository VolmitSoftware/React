package art.arcane.react.platform;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Block changes for features. Block types use React's config naming: the Bukkit
 * {@code Material} constant for vanilla blocks, such as {@code OAK_LEAVES}, and the namespaced id for modded ones.
 * Listeners run on the thread that owns the block.
 */
public interface BlockHost {
  interface BlockEvent extends HostEvent {
    GameLocation location();

    String type();

    /**
     * The player breaking or placing the block; null for other block events.
     */
    default GameEntity player() {
      return null;
    }
  }

  interface PhysicsEvent extends BlockEvent {
    /**
     * The block type whose change triggered the update.
     */
    String changedType();
  }

  interface PistonEvent extends HostEvent {
    GameLocation piston();

    boolean extending();

    /**
     * The blocks the piston moves, before they move.
     */
    List<GameLocation> blocks();
  }

  Subscription onBreak(ListenerPriority priority, boolean ignoreCancelled, Consumer<BlockEvent> listener);

  Subscription onPlace(ListenerPriority priority, boolean ignoreCancelled, Consumer<BlockEvent> listener);

  Subscription onPhysics(ListenerPriority priority, boolean ignoreCancelled, Consumer<PhysicsEvent> listener);

  Subscription onPiston(ListenerPriority priority, boolean ignoreCancelled, Consumer<PistonEvent> listener);

  interface FluidFlowEvent extends HostEvent {
    GameLocation from();

    String fromType();

    GameLocation to();

    String toType();
  }

  interface FluidLevelEvent extends HostEvent {
    GameLocation location();

    String type();

    /**
     * The fluid level of the current block, or -1 when it has none.
     */
    int level();

    String newType();

    /**
     * The fluid level of the new block, or -1 when it has none.
     */
    int newLevel();
  }

  /**
   * A falling block entity turning a block into itself as it starts to fall, or itself into a block as it lands.
   */
  interface FallingBlockEvent extends HostEvent {
    GameEntity entity();

    GameLocation block();

    /**
     * The falling block's own block state, opaque outside the platform module.
     */
    Object blockState();

    double velocityY();
  }

  @FunctionalInterface
  interface FallingTickHook {
    /**
     * True when the falling block should skip this tick.
     */
    boolean skipTick(GameEntity falling);
  }

  Subscription onFluidFlow(ListenerPriority priority, boolean ignoreCancelled, Consumer<FluidFlowEvent> listener);

  Subscription onFluidLevelChange(ListenerPriority priority, boolean ignoreCancelled, Consumer<FluidLevelEvent> listener);

  Subscription onFallingBlockChange(ListenerPriority priority, boolean ignoreCancelled, Consumer<FallingBlockEvent> listener);

  /**
   * Installs the falling block tick hook; {@code onInstalled} runs once the hook is live, which can be later or never.
   */
  void installFallingTickHook(FallingTickHook hook, Runnable onInstalled);

  void uninstallFallingTickHook();

  /**
   * True when the platform can run extra fluid ticks through {@link #tickFluid}.
   */
  boolean canTickFluids();

  /**
   * Runs one vanilla fluid tick at the block; false when no allowed fluid is there.
   */
  boolean tickFluid(UUID worldId, int x, int y, int z, boolean water, boolean lava);

  int minHeight(UUID worldId);

  int maxHeight(UUID worldId);

  /**
   * The block type at the position, loading its chunk if needed.
   */
  String blockType(UUID worldId, int x, int y, int z);

  /**
   * True for air blocks.
   */
  boolean isEmpty(UUID worldId, int x, int y, int z);

  void setBlock(UUID worldId, int x, int y, int z, String type, boolean applyPhysics);

  void dropItem(GameLocation at, String itemType, int amount);

  /**
   * Hands items to a player the way the fast drops tweak does, dropping them at {@code at} when it does not apply.
   */
  void giveItem(GameEntity player, GameLocation at, String itemType, int amount);

  /**
   * How many blocks straight above the block hold exactly the given block state.
   */
  int matchingAbove(GameLocation block, Object blockState);

  /**
   * Moves a column of {@code 1 + bonus} blocks of the given state from the block straight down to the ground, with
   * the fall and landing effects.
   */
  void dropColumn(GameLocation block, Object blockState, int bonus);
}
