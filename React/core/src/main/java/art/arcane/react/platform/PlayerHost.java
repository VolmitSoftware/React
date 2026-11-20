package art.arcane.react.platform;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Player activity hooks for features: movement, teleports, portals, interactions, attacks and consumption, plus the
 * per-player chunk send distance. Every listener runs on the thread that owns the player or entity.
 */
public interface PlayerHost {
  /**
   * A cancellable player action; cancelling it undoes or blocks the action the way Bukkit's cancelled event does.
   */
  interface Cancellable {
    ReactPlayer player();

    boolean isCancelled();

    void cancel();
  }

  /**
   * A player movement or turn. Cancelling it puts the player back at {@link #from()}.
   */
  interface Move extends Cancellable {
    GameLocation from();

    GameLocation to();
  }

  /**
   * A player teleport outside portals, such as a command or plugin teleport.
   */
  interface Teleport extends Cancellable {
    GameLocation to();
  }

  /**
   * An entity about to travel through a portal. Cancelling it keeps the entity where it is; {@link #travel()} later
   * sends it to the same destination.
   */
  interface Portal {
    UUID entityId();

    boolean isPlayer();

    /**
     * The platform entity, for scheduling on its owning thread.
     */
    Object handle();

    /**
     * Null when the portal leads nowhere.
     */
    GameLocation destination();

    boolean isCancelled();

    void cancel();

    /**
     * True while the traveller can still be sent: an online, living player or a valid, living entity.
     */
    boolean canTravel();

    /**
     * Sends the traveller to the portal destination; completes once the traversal settled.
     */
    CompletableFuture<Boolean> travel();
  }

  @FunctionalInterface
  interface Listener<T> {
    void on(T event);
  }

  /**
   * Online players, as a snapshot safe to read from any thread.
   */
  List<ReactPlayer> players();

  boolean isOnline(ReactPlayer player);

  /**
   * Player movements and turns that pass the platform's movement threshold.
   */
  Subscription onMove(Listener<Move> listener);

  Subscription onTeleport(Listener<Teleport> listener);

  Subscription onPortal(Listener<Portal> listener);

  Subscription onJoin(Listener<ReactPlayer> listener);

  Subscription onQuit(Listener<ReactPlayer> listener);

  /**
   * Block and air clicks and item use. Cancelling blocks the interaction.
   */
  Subscription onInteract(Listener<Cancellable> listener);

  /**
   * A player hitting an entity directly or with a projectile they shot. Cancelling blocks the damage.
   */
  Subscription onAttack(Listener<Cancellable> listener);

  /**
   * A player finishing eating or drinking an item. Cancelling keeps the item and its effects.
   */
  Subscription onConsume(Listener<Cancellable> listener);

  /**
   * False where players cannot get their own chunk send distance, such as plain Spigot.
   */
  boolean supportsSendViewDistance();

  /**
   * @throws IllegalStateException when the platform cannot read the player's send distance
   */
  int sendViewDistance(ReactPlayer player);

  /**
   * @throws IllegalStateException when the platform cannot change the player's send distance
   */
  void setSendViewDistance(ReactPlayer player, int distance);

  /**
   * True when the chunk at the location has been generated, loaded or not.
   */
  boolean isChunkGenerated(GameLocation location);
}
