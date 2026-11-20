package art.arcane.react.platform;

import java.util.List;
import java.util.function.Consumer;

/**
 * Dropped item entities, React super stacks and experience orbs. Listeners run on the thread that owns the item, in
 * the order they subscribed. A super stack is a bundle React filled with merged drops; it holds more items than one
 * vanilla stack and unpacks when a player or container picks it up.
 */
public interface ItemHost {
  interface ItemEvent {
    GameEntity item();

    boolean isCancelled();

    void cancel();
  }

  /**
   * A player about to pick up an item entity.
   */
  interface PlayerPickup extends ItemEvent {
    /**
     * Moves the super stack's contents into the player's inventory and drops what does not fit at the player.
     */
    void giveContents();

    void playUnpackSound();
  }

  /**
   * Where a super stack's contents went during a container pickup.
   */
  enum Insertion {
    /**
     * Everything fit into the container.
     */
    INSERTED,
    /**
     * What did not fit stays on the item entity as a smaller super stack.
     */
    KEPT,
    /**
     * What did not fit was dropped at the item entity.
     */
    DROPPED
  }

  /**
   * A hopper or other container about to pull in an item entity.
   */
  interface ContainerPickup extends ItemEvent {
    Insertion insertContents();

    /**
     * Items left in the residual super stack after {@link #insertContents()} returned {@link Insertion#KEPT}.
     */
    int keptCount();
  }

  /**
   * A player clicking an inventory slot.
   */
  interface SlotClick {
    boolean isSuperStack();

    /**
     * Cancels the click, clears the slot and gives the super stack's contents to the player.
     */
    void unpack();
  }

  interface Damage extends ItemEvent {
    boolean isVoid();
  }

  /**
   * Dropped items allowed into a world, from the platform's spawn host at MONITOR.
   */
  Subscription onSpawn(Consumer<GameEntity> listener);

  /**
   * Item entities that arrive with a loaded chunk, not freshly spawned ones.
   */
  Subscription onLoad(Consumer<GameEntity> listener);

  Subscription onRemove(Consumer<GameEntity> listener);

  Subscription onPlayerPickup(Consumer<PlayerPickup> listener);

  Subscription onContainerPickup(Consumer<ContainerPickup> listener);

  Subscription onSlotClick(Consumer<SlotClick> listener);

  Subscription onDamage(Consumer<Damage> listener);

  /**
   * Experience orbs allowed into a world, from the platform's spawn host at MONITOR.
   */
  Subscription onOrbSpawn(Consumer<GameEntity> listener);

  static Subscription spawnsOf(SpawnHost spawns, boolean items, Consumer<GameEntity> listener) {
    if (spawns == null || listener == null) {
      return Subscription.NONE;
    }
    return spawns.onEntitySpawn(ListenerPriority.MONITOR, true, (SpawnHost.EntitySpawn spawn) -> {
      if (items ? spawn.isItem() : "EXPERIENCE_ORB".equals(spawn.type())) {
        listener.accept(spawn.entity());
      }
    });
  }

  /**
   * Wraps a platform entity handle taken from {@link GameEntity#handle()}.
   */
  GameEntity entity(Object handle);

  /**
   * True for dropped item entities.
   */
  boolean isItem(GameEntity entity);

  /**
   * True while the entity is neither dead nor removed from its world.
   */
  boolean isLive(GameEntity entity);

  /**
   * The next entities of a world from a cursor that rotates across calls, at most {@code budget} of them.
   */
  List<GameEntity> scan(ReactWorld world, int budget);

  /**
   * Removes the entity at once, without React's removal countdown.
   */
  void discard(GameEntity entity);

  /**
   * The custom name of an entity, or null when it has none.
   */
  String customName(GameEntity entity);

  /**
   * The item type in the naming of Bukkit's {@code Material} constants for vanilla items, such as {@code DIAMOND}, and
   * namespaced ids for modded ones.
   */
  String material(GameEntity item);

  int amount(GameEntity item);

  int maxStackSize(GameEntity item);

  boolean isSimilar(GameEntity item, GameEntity other);

  /**
   * Replaces the item's stack with a copy holding {@code amount} items.
   */
  void setAmount(GameEntity item, int amount);

  void setTicksLived(GameEntity item, int ticks);

  boolean isSuperStack(GameEntity item);

  /**
   * Items held by a super stack, or -1 when the item is not one.
   */
  int superStackCount(GameEntity item);

  /**
   * Packs both stacks into one super stack on {@code target}. False, with nothing changed, when they hold more than
   * {@code maxBundle} items or are a single type that fits in one vanilla stack.
   */
  boolean bundle(GameEntity source, GameEntity target, int maxBundle);

  /**
   * Removes an item that was merged away or unpacked, together with React's per-item indexes.
   */
  void discardMerged(GameEntity item);

  /**
   * Particles from {@code item} toward {@code into}, plus the merge sound to nearby players when {@code sound} is set.
   */
  void mergeEffect(GameEntity item, GameEntity into, double radius, boolean sound);

  /**
   * Publishes the floating label of a super stack; false when no label provider is installed.
   */
  boolean refreshLabel(GameEntity item, String headerFormat, String entryFormat, String moreFormat, int entryLimit);

  void clearLabel(GameEntity item);

  int experience(GameEntity orb);

  void setExperience(GameEntity orb, int experience);

  /**
   * Other experience orbs within a cube of half-size {@code radius} around the orb.
   */
  List<GameEntity> nearbyOrbs(GameEntity orb, double radius);
}
