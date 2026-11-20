package art.arcane.react.platform;

import java.util.List;

/**
 * Death drops, block drops and break experience, plus handing items and experience straight to a player. Item stacks
 * and drop locations are the platform's own objects, passed through untouched.
 */
public interface DropHost {
  interface EntityDeath {
    /**
     * The player credited with the kill; null when no player killed the entity.
     */
    ReactPlayer killer();

    double killerDistanceSquared();

    int droppedExperience();

    void setDroppedExperience(int experience);

    /**
     * Removes and returns every drop of the death.
     */
    List<Object> takeDrops();
  }

  interface DroppedItem {
    Object copyStack();

    /**
     * Claims the item from the drop; false when it was no longer part of the drop.
     */
    boolean remove();

    Object dropLocation();
  }

  interface BlockDrops {
    ReactPlayer player();

    /**
     * True when the broken block held an inventory.
     */
    boolean fromContainer();

    List<DroppedItem> items();
  }

  interface BlockBreak {
    ReactPlayer player();

    int experience();

    void setExperience(int experience);
  }

  @FunctionalInterface
  interface Listener<T> {
    void on(T event);
  }

  /**
   * Fires for deaths that are not cancelled, after other listeners had their say.
   */
  Subscription onEntityDeath(Listener<EntityDeath> listener);

  Subscription onBlockDrops(Listener<BlockDrops> listener);

  Subscription onBlockBreak(Listener<BlockBreak> listener);

  Object copyStack(Object stack);

  boolean isCreative(ReactPlayer player);

  boolean isSurvival(ReactPlayer player);

  void awardExperience(ReactPlayer player, int experience);

  /**
   * Adds the stack to the player's inventory and drops what does not fit naturally at {@code dropAt}, or at the player
   * when it is null. True when anything had to be dropped.
   */
  boolean give(ReactPlayer player, Object stack, Object dropAt);

  void dropNaturally(ReactPlayer player, Object dropAt, Object stack);

  /**
   * Plays a sound to this player only, from the player's own position.
   */
  void playSound(ReactPlayer player, String soundKey, float volume, float pitch);
}
