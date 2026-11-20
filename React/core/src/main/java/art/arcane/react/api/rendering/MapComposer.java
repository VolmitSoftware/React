package art.arcane.react.api.rendering;

import art.arcane.react.localization.ReactLanguage;

import art.arcane.react.core.telemetry.ObservationMetrics;
import art.arcane.react.core.telemetry.ObservationMetrics.Counter;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.platform.DirtyRect;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Composition state for one map id: the renderer bound to it, its surface, the shadow of what was last
 * published, and the redraw gate.
 */
public final class MapComposer {
  private final int mapId;
  private final UUID worldId;
  private final String rendererId;
  private final String normalizedRendererId;
  private final AtomicLong nextDrawAtMs = new AtomicLong(0L);
  private final ReentrantLock composeLock = new ReentrantLock();
  private volatile ReactRenderer renderer;
  private volatile boolean active = true;
  private volatile Assignment appliedAssignment = Assignment.NONE;
  private byte[] surface;
  private byte[] shadow;

  public MapComposer(int mapId, UUID worldId, ReactRenderer renderer) {
    this.mapId = mapId;
    this.worldId = worldId;
    this.renderer = renderer;
    this.rendererId = renderer == null ? RendererUnknown.ID : renderer.getId();
    this.normalizedRendererId = Objects.toString(this.rendererId, "").toLowerCase(Locale.ROOT).trim();
  }

  public int mapId() {
    return mapId;
  }

  public UUID worldId() {
    return worldId;
  }

  public String rendererId() {
    return rendererId;
  }

  public String normalizedRendererId() {
    return normalizedRendererId;
  }

  public boolean isActive() {
    return active;
  }

  public void close() {
    active = false;
    composeLock.lock();
    try {
      renderer = null;
      appliedAssignment = Assignment.NONE;
      surface = null;
      shadow = null;
      nextDrawAtMs.set(0L);
    } finally {
      composeLock.unlock();
    }
  }

  /**
   * The published pixels. Only stable while no compose runs; hosts copy what they need right away.
   */
  public byte[] published() {
    return shadow();
  }

  /**
   * Redraws when the interval elapsed or the megamap assignment changed. Returns the changed area of the
   * published surface, or EMPTY when nothing was drawn or nothing changed.
   */
  public DirtyRect compose(
      MapRendererRegistry registry,
      MapRenderTarget target,
      MegamapGrid.MegamapTile tile,
      MegamapGrid.MegamapDefect defect,
      long intervalMs,
      int tileBudget,
      long now
  ) {
    if (!active) {
      return DirtyRect.EMPTY;
    }

    MegamapGrid.DefectReason defectReason = defect == null ? null : defect.reason();
    boolean reassigned = !appliedAssignment.matches(tile, defectReason);
    long nextDrawAt = nextDrawAtMs.get();
    if (!reassigned && now < nextDrawAt) {
      ObservationMetrics.increment(Counter.MAP_THROTTLE_SKIPS);
      return DirtyRect.EMPTY;
    }

    if (!nextDrawAtMs.compareAndSet(nextDrawAt, now + intervalMs) && !reassigned) {
      ObservationMetrics.increment(Counter.MAP_BUSY_SKIPS);
      return DirtyRect.EMPTY;
    }

    if (!composeLock.tryLock()) {
      ObservationMetrics.increment(Counter.MAP_BUSY_SKIPS);
      return DirtyRect.EMPTY;
    }

    try {
      if (!active) {
        return DirtyRect.EMPTY;
      }

      ReactRenderer resolved = resolveRenderer(registry);
      ReactRenderContext.Builder context = ReactRenderContext.builder().target(target);
      MegamapGrid.MegamapViewport letterbox = null;
      boolean covered = true;
      String notice;
      if (tile == null) {
        context.width(ReactRenderer.CANVAS_SIZE).height(ReactRenderer.CANVAS_SIZE);
        notice = defectReason == null ? null : ReactLanguage.raw(MegamapGrid.defectLabel(defectReason));
      } else {
        MegamapGrid.MegamapViewport viewport = MegamapGrid.viewportFor(
            tile,
            resolved.megamapCapability(),
            Math.max(1, tileBudget),
            ReactRenderer.CANVAS_SIZE
        );
        applyViewport(context, tile, viewport);
        if (viewport.adaptive()) {
          notice = null;
        } else {
          letterbox = viewport;
          covered = viewport.coversAnything(ReactRenderer.CANVAS_SIZE);
          notice = covered
              ? ReactLanguage.raw(
                  viewport.capped() ? MapMessages.MEGAMAP_CAPPED : MapMessages.MEGAMAP_ZOOMED,
                  MessageArgument.untrusted("grid", tile.gridWidth() + "x" + tile.gridHeight())
              )
              : null;
        }
      }

      boolean fullFlush = surface == null;
      if (!appliedAssignment.matches(tile, defectReason)) {
        appliedAssignment = new Assignment(tile, defectReason);
        fullFlush = true;
      }
      if (fullFlush) {
        Arrays.fill(shadow(), ReactRenderContext.UNTOUCHED);
      }

      ReactRenderContext active = context
          .surface(surface())
          .shadow(shadow())
          .fullFlush(fullFlush)
          .build();
      ReactRenderContext.push(active);
      long renderStarted = System.nanoTime();
      try {
        if (letterbox != null) {
          fillLetterbox(active, letterbox);
        }

        if (covered) {
          resolved.render();
          if (notice != null) {
            active.resetClip();
            resolved.drawMegamapNotice(notice);
          }
        }
      } finally {
        ObservationMetrics.increment(Counter.MAP_RENDERS);
        ObservationMetrics.add(Counter.MAP_RENDER_NANOS, System.nanoTime() - renderStarted);
      }

      long flushStarted = System.nanoTime();
      try {
        return active.flush();
      } finally {
        ObservationMetrics.add(Counter.MAP_FLUSH_NANOS, System.nanoTime() - flushStarted);
        ReactRenderContext.pop();
      }
    } catch (Throwable e) {
      ReactRenderContext.pop();
      ObservationMetrics.increment(Counter.MAP_FAILURES);
      if (ReactPlatforms.isBound()) {
        ReactPlatforms.get().verbose("Map renderer " + rendererId + " failed for map " + mapId + ": " + e);
      }
      return DirtyRect.EMPTY;
    } finally {
      composeLock.unlock();
    }
  }

  private byte[] surface() {
    if (surface == null) {
      surface = ReactRenderContext.newSurface();
    }
    return surface;
  }

  private byte[] shadow() {
    if (shadow == null) {
      shadow = ReactRenderContext.newSurface();
    }
    return shadow;
  }

  private ReactRenderer resolveRenderer(MapRendererRegistry registry) {
    if (registry != null) {
      ReactRenderer resolved = registry.resolve(rendererId);
      if (resolved != null) {
        renderer = resolved;
        return resolved;
      }
    }

    if (renderer == null) {
      renderer = new RendererUnknown();
    }
    return renderer;
  }

  private static void applyViewport(
      ReactRenderContext.Builder context,
      MegamapGrid.MegamapTile tile,
      MegamapGrid.MegamapViewport viewport
  ) {
    context.width(viewport.width())
        .height(viewport.height())
        .offsetX(viewport.offsetX())
        .offsetY(viewport.offsetY())
        .scaleX(viewport.scale())
        .scaleY(viewport.scale())
        .textScale(viewport.textScale())
        .gridWidth(Math.max(1, tile.gridWidth()))
        .gridHeight(Math.max(1, tile.gridHeight()));
  }

  private static void fillLetterbox(ReactRenderContext context, MegamapGrid.MegamapViewport viewport) {
    if (viewport.fullyCovers(ReactRenderer.CANVAS_SIZE)) {
      return;
    }

    int size = ReactRenderer.CANVAS_SIZE;
    int x0 = Math.min(size, Math.max(0, viewport.contentX()));
    int y0 = Math.min(size, Math.max(0, viewport.contentY()));
    int x1 = Math.max(x0, Math.min(size, viewport.contentX() + viewport.contentSpan()));
    int y1 = Math.max(y0, Math.min(size, viewport.contentY() + viewport.contentSpan()));
    byte index = MapColors.indexFor(MapTheme.SURFACE_0.toRGB());
    context.paintCanvasRect(0, 0, size, y0, index);
    context.paintCanvasRect(0, y1, size, size, index);
    context.paintCanvasRect(0, y0, x0, y1, index);
    context.paintCanvasRect(x1, y0, size, y1, index);
  }

  private record Assignment(MegamapGrid.MegamapTile tile, MegamapGrid.DefectReason defectReason) {
    private static final Assignment NONE = new Assignment(null, null);

    private boolean matches(MegamapGrid.MegamapTile otherTile, MegamapGrid.DefectReason otherDefectReason) {
      return Objects.equals(tile, otherTile) && defectReason == otherDefectReason;
    }
  }
}
