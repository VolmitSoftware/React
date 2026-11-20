package art.arcane.react.platform;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Platform side of React maps. {@code I} is the platform item stack type. Surfaces are 128x128 palette
 * bytes in row-major order; a byte of -1 marks a pixel no renderer has painted yet and is written as 0.
 */
public interface MapHost<I> {
  /**
   * Mints a new map id in the world, or returns -1 when the platform cannot.
   */
  int allocateMapId(UUID worldId);

  /**
   * Recreates backing data for an id that lost it, keeping the id. Only called when {@link #mapIdsAreWorldBound()} is false.
   */
  void ensureMapData(int mapId, UUID worldId);

  /**
   * True when a map id belongs to one world and must be re-minted when its item moves to another world.
   */
  boolean mapIdsAreWorldBound();

  UUID mapWorld(int mapId);

  /**
   * React now draws this id: suppress vanilla colours and delivery for it.
   */
  void claim(int mapId);

  void release(int mapId);

  /**
   * True while the claim from {@link #claim(int)} is still in place.
   */
  boolean isClaimed(int mapId);

  /**
   * Copies the changed rect of the composed surface into the platform's map store.
   */
  void publish(int mapId, byte[] surface, DirtyRect changed);

  /**
   * Delivers the map to one viewer. {@code rect} is the area that viewer has not seen yet and may be EMPTY,
   * which patch-based hosts skip; {@code surface} is null when React has no composition for the id.
   */
  void send(UUID viewerId, int mapId, byte[] surface, DirtyRect rect);

  /**
   * True when the platform already redraws and delivers held maps every tick, so React only pushes frame maps.
   */
  boolean pullsHeldMaps();

  /**
   * Null when the stack is not a filled map. The map id is -1 when the item has none.
   */
  MapItemView readMapItem(I item);

  /**
   * Writes id, tags and lore and clears any custom name. Returns the stack to store back.
   */
  I writeMapItem(I item, int mapId, MapItemTags tags, List<String> lore);

  I newMapItem(int mapId, MapItemTags tags, List<String> lore);

  /**
   * Puts the item in the main hand; the displaced stack goes to the inventory and overflow drops at the viewer.
   */
  void giveToMainHand(UUID viewerId, I item);

  void giveToInventory(UUID viewerId, I item);

  /**
   * Visits every inventory stack. {@code rewrite} returns null to keep a stack or the stack to store in its slot.
   */
  void rewriteInventoryMaps(UUID viewerId, Function<I, I> rewrite);

  /**
   * Moves the first matching stack (main hand, off hand, then inventory order) into the main hand.
   */
  boolean moveToMainHand(UUID viewerId, Predicate<I> match);

  /**
   * Item frames, glow frames included, in a loaded chunk. Returns false when the world or chunk is not loaded.
   */
  boolean framesInChunk(UUID worldId, int chunkX, int chunkZ, Consumer<FrameSnapshot<I>> sink);

  /**
   * Null when the frame is gone or unloaded. The item is null when {@code withItem} is false.
   */
  FrameSnapshot<I> frame(UUID frameId, boolean withItem);

  boolean frameExists(UUID frameId);

  /**
   * Replaces the framed item without sound or neighbour updates. Called on the frame's owner thread.
   */
  void setFrameItemQuietly(FrameSnapshot<I> frame, I item);

  /**
   * Rotation 0..7, 0 is upright. Called on the frame's owner thread.
   */
  void setFrameRotation(UUID frameId, int rotation);

  boolean isWorldLoaded(UUID worldId);

  /**
   * The world new maps fall back to when nothing else names one, or null.
   */
  UUID defaultWorld();

  void loadedChunks(Consumer<ChunkRef> sink);

  /**
   * One snapshot per online viewer. The runtime calls this at most once per maintenance pass.
   */
  List<ViewerSnapshot> viewers();

  UUID viewerWorld(UUID viewerId);

  boolean isOnline(UUID viewerId);

  /**
   * Ray test from the viewer's eye to the frame. Called on the viewer's owner thread.
   */
  boolean hasLineOfSight(UUID viewerId, UUID frameId, UUID worldId, double x, double y, double z);

  /**
   * Runs work on the thread that owns the frame, inline when the caller already owns it and the delay is 0.
   * Returns false when the work could not be scheduled; {@code retired} runs if the frame is removed first.
   */
  boolean runOnFrameOwner(UUID frameId, long delayTicks, Runnable work, Runnable retired);

  boolean runOnViewerOwner(UUID viewerId, long delayTicks, Runnable work);

  boolean runOnChunkOwner(UUID worldId, int chunkX, int chunkZ, Runnable work);

  void bind(MapHostListener<I> listener);

  void unbind();
}
