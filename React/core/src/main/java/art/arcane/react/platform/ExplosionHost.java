package art.arcane.react.platform;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Explosions, primed TNT and explosion packets. Block types use {@link BlockHost}'s naming. Listeners run on the thread
 * that owns the exploding blocks.
 */
public interface ExplosionHost {
  record ExplodedBlock(GameLocation location, String type) {
  }

  /**
   * An explosion about to break its blocks. The block list belongs to the game: blocks removed from it stay in place.
   */
  interface Explosion extends HostEvent {
    /**
     * The block that exploded for block explosions, or the explosion center for entity explosions.
     */
    GameLocation location();

    /**
     * True when no entity caused the explosion, as with beds and respawn anchors.
     */
    boolean fromBlock();

    /**
     * True when the explosion destroys blocks rather than keeping them or only triggering them.
     */
    boolean destroysBlocks();

    /**
     * The chance that each destroyed block drops its items.
     */
    float yield();

    List<ExplodedBlock> blocks();

    void removeBlocks(Predicate<ExplodedBlock> filter);
  }

  interface PrimedTnt extends HostEvent {
    int fuseTicks();

    void setFuseTicks(int ticks);
  }

  /**
   * React's side of the explosion packet path, called on the thread that sends the packets.
   */
  interface PacketHooks {
    void observe(GameLocation center);

    /**
     * True to drop the vanilla explosion packets of one explosion; React then owes a merged broadcast for it.
     */
    boolean suppress(UUID worldId, double x, double y, double z, float radius, int packetCount);

    /**
     * Called once the hooks are in place, which can happen later than the install call.
     */
    void installed(boolean observing, boolean suppressing);
  }

  enum WorldState {
    LOADED,
    UNLOADED,
    UNKNOWN
  }

  Subscription onExplode(ListenerPriority priority, boolean ignoreCancelled, Consumer<Explosion> listener);

  Subscription onTntPrimed(ListenerPriority priority, boolean ignoreCancelled, Consumer<PrimedTnt> listener);

  /**
   * The block type at the location now; null when the world is not loaded.
   */
  String blockType(GameLocation location);

  /**
   * Drops a container's contents and empties it, or drops the block's items as if broken without a tool.
   */
  void dropContents(GameLocation location);

  /**
   * Sets the block to air, skipping neighbour updates when {@code fastBlockUpdates} is set and no neighbour needs one.
   */
  void clear(GameLocation location, boolean fastBlockUpdates);

  /**
   * Starts a block-breaking explosion without fire at the location.
   */
  void explode(GameLocation location, float power);

  void installPacketHooks(PacketHooks hooks);

  void uninstallPacketHooks();

  /**
   * True when this server can send merged explosion packets at all.
   */
  boolean canBroadcast();

  WorldState worldState(UUID worldId);

  /**
   * Sends one explosion packet to every player within {@code rangeBlocks}; false when it could not be sent.
   */
  boolean broadcastMergedExplosion(UUID worldId, double x, double y, double z, float radius, double rangeBlocks);
}
