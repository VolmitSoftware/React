/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.api.rendering;

import art.arcane.react.React;
import art.arcane.react.content.feature.FeatureUnknown;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Getter
public class MapRendererPipe extends MapRenderer {
  private static final int DEFAULT_MAX_TILES = 16;

  private final String rendererId;
  private final String normalizedRendererId;
  private final AtomicLong nextDrawAtMs = new AtomicLong(0L);
  @Getter(AccessLevel.NONE)
  private final ReentrantLock composeLock = new ReentrantLock();
  private volatile ReactRenderer renderer;
  @Getter(AccessLevel.NONE)
  private byte[] surface;
  @Getter(AccessLevel.NONE)
  private byte[] shadow;
  @Getter(AccessLevel.NONE)
  private MapCanvas boundCanvas;

  public MapRendererPipe(ReactRenderer renderer) {
    this.renderer = renderer;
    this.rendererId = renderer == null ? FeatureUnknown.ID : renderer.getId();
    this.normalizedRendererId = Objects.toString(this.rendererId, "").toLowerCase(Locale.ROOT).trim();
  }

  @Override
  public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
    try {
      MapController controller = React.controller(MapController.class);
      if (controller != null && !controller.shouldRenderForPlayer(map, player)) {
        return;
      }

      // The canvas is shared across all viewers; the renderer is invoked once per viewer per
      // update by the server, so skipping within the redraw interval collapses N-viewer
      // redraws to one without changing what any viewer sees.
      long now = System.currentTimeMillis();
      long nextDrawAt = nextDrawAtMs.get();
      if (now < nextDrawAt) {
        return;
      }

      long interval = controller == null ? 0L : controller.redrawIntervalMsFor(map);
      if (!nextDrawAtMs.compareAndSet(nextDrawAt, now + interval)) {
        return;
      }

      ReactRenderer resolved = resolveRenderer();
      MegamapGrid.MegamapTile tile = controller == null ? null : controller.megamapTileFor(map);
      ReactRenderContext.Builder context = ReactRenderContext.builder()
          .player(player)
          .view(map)
          .canvas(canvas);

      MegamapGrid.MegamapViewport letterbox = null;
      boolean covered = true;
      String notice;
      if (tile == null) {
        context.width(ReactRenderer.CANVAS_SIZE).height(ReactRenderer.CANVAS_SIZE);
        notice = noticeForDefect(controller == null ? null : controller.megamapDefectFor(map));
      } else {
        MegamapGrid.MegamapViewport viewport = MegamapGrid.viewportFor(
            tile,
            resolved.megamapCapability(),
            megamapTileBudget(controller),
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

      if (!composeLock.tryLock()) {
        return;
      }

      try {
        ReactRenderContext active = context
            .surface(surface())
            .shadow(shadow())
            .fullFlush(bindCanvas(canvas))
            .build();
        ReactRenderContext.push(active);
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
          active.flush();
          ReactRenderContext.pop();
        }
      } finally {
        composeLock.unlock();
      }
    } catch (Throwable e) {
      e.printStackTrace();
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

  private boolean bindCanvas(MapCanvas canvas) {
    if (boundCanvas == canvas) {
      return false;
    }

    boundCanvas = canvas;
    Arrays.fill(shadow(), ReactRenderContext.UNTOUCHED);
    return true;
  }

  private ReactRenderer resolveRenderer() {
    try {
      MapController controller = React.controller(MapController.class);
      if (controller != null) {
        ReactRenderer resolved = controller.getRendererById(rendererId);
        if (resolved != null) {
          renderer = resolved;
          return resolved;
        }
      }
    } catch (Throwable ignored) {
    }

    if (renderer == null) {
      renderer = new RendererUnknown();
    }

    return renderer;
  }

  private int megamapTileBudget(MapController controller) {
    return controller == null ? DEFAULT_MAX_TILES : Math.max(1, controller.getMegamapMaxTiles());
  }

  private void applyViewport(
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

  private void fillLetterbox(ReactRenderContext context, MegamapGrid.MegamapViewport viewport) {
    if (viewport.fullyCovers(ReactRenderer.CANVAS_SIZE)) {
      return;
    }

    int x0 = Math.min(ReactRenderer.CANVAS_SIZE, Math.max(0, viewport.contentX()));
    int y0 = Math.min(ReactRenderer.CANVAS_SIZE, Math.max(0, viewport.contentY()));
    int x1 = Math.max(x0, Math.min(ReactRenderer.CANVAS_SIZE, viewport.contentX() + viewport.contentSpan()));
    int y1 = Math.max(y0, Math.min(ReactRenderer.CANVAS_SIZE, viewport.contentY() + viewport.contentSpan()));

    byte index = MapColors.indexFor(MapTheme.SURFACE_0.toRGB());
    context.paintCanvasRect(0, 0, ReactRenderer.CANVAS_SIZE, y0, index);
    context.paintCanvasRect(0, y1, ReactRenderer.CANVAS_SIZE, ReactRenderer.CANVAS_SIZE, index);
    context.paintCanvasRect(0, y0, x0, y1, index);
    context.paintCanvasRect(x1, y0, ReactRenderer.CANVAS_SIZE, y1, index);
  }

  private String noticeForDefect(MegamapGrid.MegamapDefect defect) {
    if (defect == null) {
      return null;
    }

    return ReactLanguage.raw(MapMessages.defectLabel(defect.reason()));
  }
}
