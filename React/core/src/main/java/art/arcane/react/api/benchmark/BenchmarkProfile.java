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

public record BenchmarkProfile(long warmupMillis, long sampleMillis, int rounds, int workingSetBytes) {
  public BenchmarkProfile {
    if (warmupMillis < 0) {
      throw new IllegalArgumentException("Warmup cannot be negative");
    }
    if (sampleMillis <= 0) {
      throw new IllegalArgumentException("Sample window must be positive");
    }
    if (rounds <= 0) {
      throw new IllegalArgumentException("Round count must be positive");
    }
    if (workingSetBytes < 65536) {
      throw new IllegalArgumentException("Working set must be at least 64 KiB");
    }
  }

  public static BenchmarkProfile standard() {
    return new BenchmarkProfile(150L, 400L, 2, 8 * 1024 * 1024);
  }

  public static BenchmarkProfile quick() {
    return new BenchmarkProfile(5L, 25L, 1, 1024 * 1024);
  }

  public long estimatedMillis(int phases) {
    return (warmupMillis + sampleMillis * rounds) * phases;
  }
}
