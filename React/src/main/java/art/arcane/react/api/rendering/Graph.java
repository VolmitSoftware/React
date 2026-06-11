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
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class Graph {
  // Pushing on a fixed cadence keeps the graph time-axis stable no matter how many
  // viewers trigger renders, and stops samplers from being polled per viewer.
  private static final long PUSH_INTERVAL_MS = 500L;
  private static final Map<String, Graph> graphs = new ConcurrentHashMap<>();
  private final DoubleList sequence;
  private final RollingSequence rs = new RollingSequence(3);
  private long lastPushMs;
  private double cachedMin;
  private double cachedMax;
  private boolean minMaxDirty = true;

  public Graph() {
    sequence = new DoubleArrayList();
  }

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
    return sequence.size() > index ? sequence.getDouble(index) : 0;
  }

  public synchronized void push(double v) {
    rs.put(v);
    sequence.add(0, rs.getAverage());
    while (sequence.size() > 128) {
      sequence.removeDouble(sequence.size() - 1);
    }
    minMaxDirty = true;
  }

  public double getPaddedMax(double percent) {
    return getMax() + ((getMax() - getMin()) * percent);
  }

  public double getPaddedMin(double percent) {
    return getMin() - ((getMax() - getMin()) * percent);
  }

  public double getRange() {
    return getMax() - getMin();
  }

  public synchronized double getMax() {
    refreshMinMax();
    return cachedMax;
  }

  public synchronized double getMin() {
    refreshMinMax();
    return cachedMin;
  }

  private void refreshMinMax() {
    if (!minMaxDirty) {
      return;
    }

    double min = 0D;
    double max = 0D;
    int size = sequence.size();
    if (size > 0) {
      min = Double.MAX_VALUE;
      max = -Double.MAX_VALUE;
      for (int i = 0; i < size; i++) {
        double v = sequence.getDouble(i);
        min = Math.min(min, v);
        max = Math.max(max, v);
      }
    }

    cachedMin = min;
    cachedMax = max;
    minMaxDirty = false;
  }
}
