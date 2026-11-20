package art.arcane.react.platform.bukkit;

import art.arcane.react.api.rendering.MapComposer;
import art.arcane.react.api.rendering.MapRuntime;
import art.arcane.react.api.rendering.ReactRenderContext;
import art.arcane.react.platform.DirtyRect;
import art.arcane.react.platform.ReactPlatforms;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * The only renderer on a React map view. Bukkit pulls it for every viewer; it composes through the core
 * runtime and copies whatever this canvas has not received yet.
 */
public final class BukkitMapPipe extends MapRenderer {
  private final MapRuntime<ItemStack> runtime;
  private final BukkitMapHost host;
  private final int mapId;
  private final UUID receiverId = UUID.randomUUID();
  private volatile boolean active = true;
  private MapCanvas boundCanvas;

  BukkitMapPipe(MapRuntime<ItemStack> runtime, BukkitMapHost host, int mapId) {
    super(false);
    this.runtime = runtime;
    this.host = host;
    this.mapId = mapId;
  }

  public int mapId() {
    return mapId;
  }

  public boolean isActive() {
    return active && runtime.isActive();
  }

  public boolean isOwnedBy(MapRuntime<ItemStack> expected) {
    return runtime == expected;
  }

  void close() {
    active = false;
  }

  @Override
  public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
    if (!isActive()) {
      return;
    }

    try {
      if (!runtime.shouldRenderForViewer(mapId, host.snapshotOf(player))) {
        return;
      }

      long now = System.currentTimeMillis();
      runtime.compose(mapId, player.getUniqueId(), now);
      MapComposer composer = runtime.composer(mapId);
      if (composer == null) {
        return;
      }

      synchronized (this) {
        if (boundCanvas != canvas) {
          boundCanvas = canvas;
          runtime.resetReceiver(mapId, receiverId);
        }

        DirtyRect pending = runtime.takePending(mapId, receiverId, now);
        if (!pending.isEmpty()) {
          copy(composer.published(), pending, canvas);
        }
      }
    } catch (Throwable e) {
      if (ReactPlatforms.isBound()) {
        ReactPlatforms.get().verbose("Map renderer for map " + mapId + " failed for player " + player.getName() + ": " + e);
      }
    }
  }

  @SuppressWarnings("removal")
  static void copy(byte[] pixels, DirtyRect rect, MapCanvas canvas) {
    int size = DirtyRect.SIZE;
    for (int row = rect.y0(); row < rect.y1(); row++) {
      int base = row * size;
      for (int column = rect.x0(); column < rect.x1(); column++) {
        byte value = pixels[base + column];
        if (value != ReactRenderContext.UNTOUCHED && canvas.getPixel(column, row) != value) {
          canvas.setPixel(column, row, value);
        }
      }
    }
  }
}
