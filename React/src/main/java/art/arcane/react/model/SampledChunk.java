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

package art.arcane.react.model;

import com.google.common.util.concurrent.AtomicDouble;
import lombok.Data;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleUnaryOperator;

@Data
public class SampledChunk {
  private static final double PRUNE_BELOW = 0.01D;
  private static final DoubleUnaryOperator HALF = (v) -> v * 0.5D;
  private final UUID worldId;
  private final String worldKey;
  private final int chunkX;
  private final int chunkZ;
  private final Map<String, AtomicDouble> values;

  public SampledChunk(UUID worldId, String worldKey, int chunkX, int chunkZ) {
    this.worldId = worldId;
    this.worldKey = worldKey;
    this.chunkX = chunkX;
    this.chunkZ = chunkZ;
    values = new ConcurrentHashMap<>();
  }

  public double highestSubScore() {
    double max = 0D;
    for (AtomicDouble value : values.values()) {
      double score = value.get();
      if (score > max) {
        max = score;
      }
    }
    return max;
  }

  public double totalScore() {
    double total = 0D;
    for (AtomicDouble value : values.values()) {
      total += value.get();
    }
    return total;
  }

  public Optional<AtomicDouble> optional(String key) {
    return Optional.ofNullable(values.get(key));
  }

  public AtomicDouble get(String key) {
    AtomicDouble value = values.get(key);
    return value != null ? value : values.computeIfAbsent(key, (k) -> new AtomicDouble(0D));
  }

  public AtomicDouble gauge(String key) {
    AtomicDouble value = values.get(key);
    return value != null ? value : values.computeIfAbsent(key, (k) -> new GaugeCounter());
  }

  public void decay() {
    for (Map.Entry<String, AtomicDouble> entry : values.entrySet()) {
      AtomicDouble value = entry.getValue();
      if (value instanceof GaugeCounter) {
        continue;
      }
      if (value.updateAndGet(HALF) < PRUNE_BELOW) {
        values.remove(entry.getKey(), value);
      }
    }
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  private static final class GaugeCounter extends AtomicDouble {
  }
}
