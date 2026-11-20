package art.arcane.react.platform;

import java.util.List;
import java.util.UUID;

public interface MapHostListener<I> {
  void onViewerJoin(UUID viewerId);

  void onViewerChangedWorld(UUID viewerId, UUID toWorld);

  /**
   * A chunk's entities became available. Called on the chunk's owner thread.
   */
  void onFramesLoaded(UUID worldId, int chunkX, int chunkZ, List<FrameSnapshot<I>> frames);

  void onFrameUnloaded(UUID frameId);

  /**
   * An item was placed, removed or rotated. May fire before the change lands; the runtime re-reads a tick later.
   */
  void onFrameChanged(UUID frameId);

  void onFrameBroken(UUID frameId);

  void onChunkLoaded(UUID worldId, int chunkX, int chunkZ);

  void onChunkUnloaded(UUID worldId, int chunkX, int chunkZ);

  void onWorldUnloaded(UUID worldId);

  /**
   * Maintenance tick on the main or global thread. Bukkit calls it every maintenance interval, mods every server tick.
   */
  void onTick(long nowMs);
}
