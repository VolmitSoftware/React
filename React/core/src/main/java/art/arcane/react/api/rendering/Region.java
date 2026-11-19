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

public record Region(int x, int y, int width, int height) {
  public static final Region EMPTY = new Region(0, 0, 0, 0);

  public Region {
    width = Math.max(0, width);
    height = Math.max(0, height);
  }

  public static Region of(int x, int y, int width, int height) {
    return new Region(x, y, width, height);
  }

  public int right() {
    return x + width;
  }

  public int bottom() {
    return y + height;
  }

  public int centerX() {
    return x + (width / 2);
  }

  public int centerY() {
    return y + (height / 2);
  }

  public boolean isEmpty() {
    return width <= 0 || height <= 0;
  }

  public Region inset(int all) {
    return inset(all, all, all, all);
  }

  public Region inset(int left, int top, int right, int bottom) {
    int newWidth = width - left - right;
    int newHeight = height - top - bottom;
    if (newWidth <= 0 || newHeight <= 0) {
      return new Region(x + left, y + top, 0, 0);
    }
    return new Region(x + left, y + top, newWidth, newHeight);
  }

  public Region topBand(int bandHeight) {
    return new Region(x, y, width, Math.min(Math.max(0, bandHeight), height));
  }

  public Region bottomBand(int bandHeight) {
    int clamped = Math.min(Math.max(0, bandHeight), height);
    return new Region(x, bottom() - clamped, width, clamped);
  }

  public Region leftBand(int bandWidth) {
    return new Region(x, y, Math.min(Math.max(0, bandWidth), width), height);
  }

  public Region rightBand(int bandWidth) {
    int clamped = Math.min(Math.max(0, bandWidth), width);
    return new Region(right() - clamped, y, clamped, height);
  }

  public Region withoutTop(int bandHeight) {
    int clamped = Math.min(Math.max(0, bandHeight), height);
    return new Region(x, y + clamped, width, height - clamped);
  }

  public Region withoutBottom(int bandHeight) {
    int clamped = Math.min(Math.max(0, bandHeight), height);
    return new Region(x, y, width, height - clamped);
  }

  public Region withoutLeft(int bandWidth) {
    int clamped = Math.min(Math.max(0, bandWidth), width);
    return new Region(x + clamped, y, width - clamped, height);
  }

  public Region withoutRight(int bandWidth) {
    int clamped = Math.min(Math.max(0, bandWidth), width);
    return new Region(x, y, width - clamped, height);
  }

  public Region translate(int dx, int dy) {
    return new Region(x + dx, y + dy, width, height);
  }

  public Region rowAt(int index, int rowHeight) {
    return new Region(x, y + (index * rowHeight), width, Math.max(0, rowHeight));
  }

  public int rowCapacity(int rowHeight) {
    return rowHeight <= 0 ? 0 : height / rowHeight;
  }

  public Region intersect(Region other) {
    if (other == null) {
      return EMPTY;
    }

    int x0 = Math.max(x, other.x);
    int y0 = Math.max(y, other.y);
    int x1 = Math.min(right(), other.right());
    int y1 = Math.min(bottom(), other.bottom());
    if (x0 >= x1 || y0 >= y1) {
      return EMPTY;
    }
    return new Region(x0, y0, x1 - x0, y1 - y0);
  }

  public boolean intersects(Region other) {
    if (other == null || isEmpty() || other.isEmpty()) {
      return false;
    }
    return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
  }

  public boolean contains(int px, int py) {
    return px >= x && py >= y && px < right() && py < bottom();
  }

  public Region[] splitColumns(int gutter, int... weights) {
    if (weights == null || weights.length == 0) {
      return new Region[0];
    }

    int columns = weights.length;
    int spacing = Math.max(0, gutter);
    int available = width - (spacing * (columns - 1));
    if (available <= 0) {
      Region[] empty = new Region[columns];
      for (int i = 0; i < columns; i++) {
        empty[i] = new Region(x, y, 0, height);
      }
      return empty;
    }

    long total = 0L;
    for (int weight : weights) {
      total += Math.max(0, weight);
    }
    if (total <= 0L) {
      total = columns;
    }

    Region[] parts = new Region[columns];
    int cursor = x;
    int consumed = 0;
    for (int i = 0; i < columns; i++) {
      int slice;
      if (i == columns - 1) {
        slice = available - consumed;
      } else {
        slice = (int) ((available * (long) Math.max(0, weights[i])) / total);
      }
      slice = Math.max(0, slice);
      parts[i] = new Region(cursor, y, slice, height);
      consumed += slice;
      cursor += slice + spacing;
    }
    return parts;
  }

  public Region[] splitRows(int gutter, int... weights) {
    if (weights == null || weights.length == 0) {
      return new Region[0];
    }

    int rows = weights.length;
    int spacing = Math.max(0, gutter);
    int available = height - (spacing * (rows - 1));
    if (available <= 0) {
      Region[] empty = new Region[rows];
      for (int i = 0; i < rows; i++) {
        empty[i] = new Region(x, y, width, 0);
      }
      return empty;
    }

    long total = 0L;
    for (int weight : weights) {
      total += Math.max(0, weight);
    }
    if (total <= 0L) {
      total = rows;
    }

    Region[] parts = new Region[rows];
    int cursor = y;
    int consumed = 0;
    for (int i = 0; i < rows; i++) {
      int slice;
      if (i == rows - 1) {
        slice = available - consumed;
      } else {
        slice = (int) ((available * (long) Math.max(0, weights[i])) / total);
      }
      slice = Math.max(0, slice);
      parts[i] = new Region(x, cursor, width, slice);
      consumed += slice;
      cursor += slice + spacing;
    }
    return parts;
  }
}
