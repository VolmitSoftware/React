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

package art.arcane.react.api.benchmark;

import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;
import java.util.Objects;

public record BenchmarkResult(TextKey name, List<BenchmarkMetric> metrics, int score, long elapsedMillis) {
  public BenchmarkResult {
    Objects.requireNonNull(name, "Benchmark name cannot be null");
    metrics = List.copyOf(metrics);
  }

  public BenchmarkRating rating() {
    return BenchmarkRating.of(score);
  }
}
