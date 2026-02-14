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

import art.arcane.chrono.ChronoLatch;
import com.google.common.util.concurrent.AtomicDouble;
import lombok.Data;
import org.bukkit.Chunk;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;

@Data
public class SampledChunk {
    private static final DoubleUnaryOperator HALF = (v) -> v * 0.5D;
    private final Chunk chunk;
    private final SampledWorld world;
    private final ChronoLatch cleaner;
    private Map<String, AtomicDouble> values;

    public SampledChunk(Chunk chunk, SampledWorld world) {
        this.chunk = chunk;
        this.world = world;
        this.values = new HashMap<>();
        this.cleaner = new ChronoLatch(1000);
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
        if (cleaner.flip()) {
            cleanup();
        }

        return values.computeIfAbsent(key, (k) -> new AtomicDouble(0D));
    }

    private void cleanup() {
        String remove = null;

        for (Map.Entry<String, AtomicDouble> entry : values.entrySet()) {
            double v = entry.getValue().updateAndGet(HALF);

            if (remove == null && v <= 1) {
                remove = entry.getKey();
            }
        }

        if (remove != null) {
            values.remove(remove);
        }
    }
}
