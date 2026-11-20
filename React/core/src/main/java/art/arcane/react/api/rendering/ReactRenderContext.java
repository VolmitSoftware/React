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

import art.arcane.react.platform.DirtyRect;
import art.arcane.react.platform.MapRenderTarget;

import java.util.Arrays;

public final class ReactRenderContext {
  public static final byte UNTOUCHED = (byte) -1;
  public static final int SURFACE_LENGTH = ReactRenderer.CANVAS_SIZE * ReactRenderer.CANVAS_SIZE;

  private static final int INITIAL_CLIP_STACK = 32;
  private static final ThreadLocal<ReactRenderContext> CONTEXT = new ThreadLocal<ReactRenderContext>();

  private final MapRenderTarget target;
  private final byte[] surface;
  private final byte[] shadow;
  private final int width;
  private final int height;
  private final int offsetX;
  private final int offsetY;
  private final int scaleX;
  private final int scaleY;
  private final int textScale;
  private final int gridWidth;
  private final int gridHeight;
  private int clipX0;
  private int clipY0;
  private int clipX1;
  private int clipY1;
  private int[] clipStack;
  private int clipDepth;
  private int dirtyX0;
  private int dirtyY0;
  private int dirtyX1;
  private int dirtyY1;

  private ReactRenderContext(Builder builder) {
    this.target = builder.target == null ? MapRenderTarget.DETACHED : builder.target;
    this.surface = adopt(builder.surface);
    this.shadow = adopt(builder.shadow);
    this.width = builder.width;
    this.height = builder.height;
    this.offsetX = builder.offsetX;
    this.offsetY = builder.offsetY;
    this.scaleX = Math.max(1, builder.scaleX);
    this.scaleY = Math.max(1, builder.scaleY);
    this.textScale = Math.max(1, builder.textScale);
    this.gridWidth = Math.max(1, builder.gridWidth);
    this.gridHeight = Math.max(1, builder.gridHeight);
    this.clipX0 = 0;
    this.clipY0 = 0;
    this.clipX1 = Math.max(0, builder.width);
    this.clipY1 = Math.max(0, builder.height);
    if (builder.fullFlush) {
      this.dirtyX0 = 0;
      this.dirtyY0 = 0;
      this.dirtyX1 = ReactRenderer.CANVAS_SIZE;
      this.dirtyY1 = ReactRenderer.CANVAS_SIZE;
    } else {
      clearDirty();
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static byte[] newSurface() {
    byte[] created = new byte[SURFACE_LENGTH];
    Arrays.fill(created, UNTOUCHED);
    return created;
  }

  public static void push(ReactRenderContext context) {
    CONTEXT.set(context);
  }

  public static void pop() {
    CONTEXT.remove();
  }

  public static ReactRenderContext of() {
    return CONTEXT.get();
  }

  public MapRenderTarget getTarget() {
    return target;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public int getOffsetX() {
    return offsetX;
  }

  public int getOffsetY() {
    return offsetY;
  }

  public int getScaleX() {
    return scaleX;
  }

  public int getScaleY() {
    return scaleY;
  }

  public int getTextScale() {
    return textScale;
  }

  public int getGridWidth() {
    return gridWidth;
  }

  public int getGridHeight() {
    return gridHeight;
  }

  public int getClipX0() {
    return clipX0;
  }

  public int getClipY0() {
    return clipY0;
  }

  public int getClipX1() {
    return clipX1;
  }

  public int getClipY1() {
    return clipY1;
  }

  public int getClipDepth() {
    return clipDepth;
  }

  public byte surfacePixel(int x, int y) {
    if (x < 0 || y < 0 || x >= ReactRenderer.CANVAS_SIZE || y >= ReactRenderer.CANVAS_SIZE) {
      return UNTOUCHED;
    }

    return surface[(y * ReactRenderer.CANVAS_SIZE) + x];
  }

  public DirtyRect flush() {
    return flush(null);
  }

  /**
   * Publishes painted changes into the shadow and returns their bounding rect. The sink, when given, receives
   * every changed pixel.
   */
  public DirtyRect flush(PixelSink sink) {
    if (dirtyX0 >= dirtyX1 || dirtyY0 >= dirtyY1) {
      clearDirty();
      return DirtyRect.EMPTY;
    }

    int size = ReactRenderer.CANVAS_SIZE;
    int changedX0 = size;
    int changedY0 = size;
    int changedX1 = 0;
    int changedY1 = 0;
    for (int row = dirtyY0; row < dirtyY1; row++) {
      int base = row * size;
      int offset = Arrays.mismatch(surface, base + dirtyX0, base + dirtyX1, shadow, base + dirtyX0, base + dirtyX1);
      if (offset < 0) {
        continue;
      }

      int first = dirtyX0 + offset;
      int last = dirtyX1 - 1;
      while (last > first && surface[base + last] == shadow[base + last]) {
        last--;
      }

      if (sink == null) {
        System.arraycopy(surface, base + first, shadow, base + first, (last - first) + 1);
      } else {
        for (int column = first; column <= last; column++) {
          byte value = surface[base + column];
          if (value != shadow[base + column]) {
            sink.setPixel(column, row, value);
            shadow[base + column] = value;
          }
        }
      }
      changedX0 = Math.min(changedX0, first);
      changedX1 = Math.max(changedX1, last + 1);
      changedY0 = Math.min(changedY0, row);
      changedY1 = row + 1;
    }

    clearDirty();
    return changedX0 >= changedX1 ? DirtyRect.EMPTY : new DirtyRect(changedX0, changedY0, changedX1, changedY1);
  }

  public void pushClip(int x, int y, int w, int h) {
    if (clipStack == null) {
      clipStack = new int[INITIAL_CLIP_STACK];
    }
    if ((clipDepth * 4) + 4 > clipStack.length) {
      int[] grown = new int[clipStack.length * 2];
      System.arraycopy(clipStack, 0, grown, 0, clipStack.length);
      clipStack = grown;
    }

    int base = clipDepth * 4;
    clipStack[base] = clipX0;
    clipStack[base + 1] = clipY0;
    clipStack[base + 2] = clipX1;
    clipStack[base + 3] = clipY1;
    clipDepth++;

    int x0 = Math.max(clipX0, x);
    int y0 = Math.max(clipY0, y);
    int x1 = Math.min(clipX1, x + w);
    int y1 = Math.min(clipY1, y + h);
    if (x0 >= x1 || y0 >= y1) {
      clipX0 = x0;
      clipY0 = y0;
      clipX1 = x0;
      clipY1 = y0;
      return;
    }

    clipX0 = x0;
    clipY0 = y0;
    clipX1 = x1;
    clipY1 = y1;
  }

  public void popClip() {
    if (clipDepth <= 0 || clipStack == null) {
      return;
    }

    clipDepth--;
    int base = clipDepth * 4;
    clipX0 = clipStack[base];
    clipY0 = clipStack[base + 1];
    clipX1 = clipStack[base + 2];
    clipY1 = clipStack[base + 3];
  }

  public void resetClip() {
    clipDepth = 0;
    clipX0 = 0;
    clipY0 = 0;
    clipX1 = Math.max(0, width);
    clipY1 = Math.max(0, height);
  }

  public void paintCanvasRect(int x0, int y0, int x1, int y1, byte paletteIndex) {
    int size = ReactRenderer.CANVAS_SIZE;
    int px0 = Math.max(0, x0);
    int py0 = Math.max(0, y0);
    int px1 = Math.min(size, x1);
    int py1 = Math.min(size, y1);
    if (px0 >= px1 || py0 >= py1) {
      return;
    }

    for (int row = py0; row < py1; row++) {
      int base = row * size;
      Arrays.fill(surface, base + px0, base + px1, paletteIndex);
    }

    markDirty(px0, py0, px1, py1);
  }

  private void markDirty(int x0, int y0, int x1, int y1) {
    if (x0 < dirtyX0) {
      dirtyX0 = x0;
    }
    if (y0 < dirtyY0) {
      dirtyY0 = y0;
    }
    if (x1 > dirtyX1) {
      dirtyX1 = x1;
    }
    if (y1 > dirtyY1) {
      dirtyY1 = y1;
    }
  }

  private void clearDirty() {
    dirtyX0 = ReactRenderer.CANVAS_SIZE;
    dirtyY0 = ReactRenderer.CANVAS_SIZE;
    dirtyX1 = 0;
    dirtyY1 = 0;
  }

  private static byte[] adopt(byte[] supplied) {
    if (supplied == null) {
      return newSurface();
    }
    if (supplied.length != SURFACE_LENGTH) {
      throw new IllegalArgumentException("Render surface must be " + SURFACE_LENGTH + " bytes, got " + supplied.length);
    }
    return supplied;
  }

  public static final class Builder {
    private MapRenderTarget target;
    private byte[] surface;
    private byte[] shadow;
    private boolean fullFlush;
    private int width;
    private int height;
    private int offsetX;
    private int offsetY;
    private int scaleX = 1;
    private int scaleY = 1;
    private int textScale = 1;
    private int gridWidth = 1;
    private int gridHeight = 1;

    private Builder() {
    }

    public Builder target(MapRenderTarget target) {
      this.target = target;
      return this;
    }

    public Builder surface(byte[] surface) {
      this.surface = surface;
      return this;
    }

    public Builder shadow(byte[] shadow) {
      this.shadow = shadow;
      return this;
    }

    public Builder fullFlush(boolean fullFlush) {
      this.fullFlush = fullFlush;
      return this;
    }

    public Builder width(int width) {
      this.width = width;
      return this;
    }

    public Builder height(int height) {
      this.height = height;
      return this;
    }

    public Builder offsetX(int offsetX) {
      this.offsetX = offsetX;
      return this;
    }

    public Builder offsetY(int offsetY) {
      this.offsetY = offsetY;
      return this;
    }

    public Builder scaleX(int scaleX) {
      this.scaleX = scaleX;
      return this;
    }

    public Builder scaleY(int scaleY) {
      this.scaleY = scaleY;
      return this;
    }

    public Builder textScale(int textScale) {
      this.textScale = textScale;
      return this;
    }

    public Builder gridWidth(int gridWidth) {
      this.gridWidth = gridWidth;
      return this;
    }

    public Builder gridHeight(int gridHeight) {
      this.gridHeight = gridHeight;
      return this;
    }

    public ReactRenderContext build() {
      return new ReactRenderContext(this);
    }
  }

  @FunctionalInterface
  public interface PixelSink {
    void setPixel(int x, int y, byte paletteIndex);
  }
}
