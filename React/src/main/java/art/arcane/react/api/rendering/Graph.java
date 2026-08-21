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

import art.arcane.react.api.sampler.Sampler;
import art.arcane.volmlib.util.math.RollingSequence;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class Graph {
  // Megamap walls render one sample per logical pixel, so the ring has to hold enough
  // history for the widest supported wall (4 tiles wide = 512px) at ~4KB per sampler.
  public static final int CAPACITY = 512;
  // Pushing on a fixed cadence keeps the graph time-axis stable no matter how many
  // viewers trigger renders, and stops samplers from being polled per viewer.
  private static final long PUSH_INTERVAL_MS = 500L;
  // Callers that do not ask for a window keep the historical 128-sample view, so a
  // single 128px map and the web history read exactly the same values as before.
  private static final int DEFAULT_WINDOW = 128;
  private static final Map<String, Graph> graphs = new ConcurrentHashMap<>();
  private final double[] sequence = new double[CAPACITY];
  private int head;
  private int size;
  private final RollingSequence rs = new RollingSequence(3);
  private long lastPushMs;
  private double cachedMin;
  private double cachedMax;
  private int cachedWindow = -1;
  private boolean minMaxDirty = true;

  public static Graph of(Sampler sampler) {
    Graph g = graphs.computeIfAbsent(sampler.getName(), (k) -> new Graph());
    long now = System.currentTimeMillis();
    synchronized (g) {
      if (now - g.lastPushMs >= PUSH_INTERVAL_MS) {
        g.lastPushMs = now;
        g.push(sampler.sample());
      }
    }
    return g;
  }

  public synchronized double get(int index) {
    if (size <= 0) {
      return 0;
    }

    int clamped = index < 0 ? 0 : Math.min(index, size - 1);
    int slot = head - 1 - clamped;
    slot %= CAPACITY;
    if (slot < 0) {
      slot += CAPACITY;
    }

    return sequence[slot];
  }

  public synchronized void push(double v) {
    rs.put(v);
    sequence[head] = rs.getAverage();
    head = (head + 1) % CAPACITY;
    if (size < CAPACITY) {
      size++;
    }
    minMaxDirty = true;
  }

  public double getPaddedMax(double percent) {
    return getPaddedMax(percent, DEFAULT_WINDOW);
  }

  public double getPaddedMin(double percent) {
    return getPaddedMin(percent, DEFAULT_WINDOW);
  }

  public double getPaddedMax(double percent, int window) {
    double min = getMin(window);
    double max = getMax(window);
    return max + ((max - min) * percent);
  }

  public double getPaddedMin(double percent, int window) {
    double min = getMin(window);
    double max = getMax(window);
    return min - ((max - min) * percent);
  }

  public double getRange() {
    return getMax() - getMin();
  }

  public double getMax() {
    return getMax(DEFAULT_WINDOW);
  }

  public double getMin() {
    return getMin(DEFAULT_WINDOW);
  }

  public synchronized double getMax(int window) {
    refreshMinMax(clampWindow(window));
    return cachedMax;
  }

  public synchronized double getMin(int window) {
    refreshMinMax(clampWindow(window));
    return cachedMin;
  }

  private static int clampWindow(int window) {
    return Math.max(1, Math.min(window, CAPACITY));
  }

  // A chart only autoscales against the samples it actually draws, so the extremes are
  // cached per window; renders of the same graph all ask for the same window and hit it.
  private void refreshMinMax(int window) {
    if (!minMaxDirty && cachedWindow == window) {
      return;
    }

    double min = 0D;
    double max = 0D;
    int count = Math.min(size, window);
    if (count > 0) {
      min = Double.MAX_VALUE;
      max = -Double.MAX_VALUE;
      for (int i = 0; i < count; i++) {
        double v = get(i);
        min = Math.min(min, v);
        max = Math.max(max, v);
      }
    }

    cachedMin = min;
    cachedMax = max;
    cachedWindow = window;
    minMaxDirty = false;
  }
}
