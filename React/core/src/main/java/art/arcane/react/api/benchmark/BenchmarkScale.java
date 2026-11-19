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

public final class BenchmarkScale {
  public static final int REFERENCE_SCORE = 100;
  public static final int MAXIMUM_SCORE = 999;

  public static final double CPU_INTEGER_MOPS = 190.0;
  public static final double CPU_FLOATING_MOPS = 85.0;
  public static final double CPU_CACHE_MOPS = 42.0;
  public static final double CPU_MULTI_CORE_MOPS = 2000.0;

  public static final double MEMORY_WRITE_GIGABYTES = 36.0;
  public static final double MEMORY_READ_GIGABYTES = 22.0;
  public static final double MEMORY_COPY_GIGABYTES = 21.0;
  public static final double MEMORY_LATENCY_NANOS = 25.0;

  public static final double DRIVE_WRITE_MEGABYTES = 1400.0;
  public static final double DRIVE_FLUSH_MILLIS = 5.5;

  private BenchmarkScale() {
  }

  public static int higherIsBetter(double measured, double reference) {
    if (!Double.isFinite(measured) || measured <= 0.0) {
      return 0;
    }
    return clamp(Math.round(REFERENCE_SCORE * measured / reference));
  }

  public static int lowerIsBetter(double measured, double reference) {
    if (!Double.isFinite(measured) || measured <= 0.0) {
      return 0;
    }
    return clamp(Math.round(REFERENCE_SCORE * reference / measured));
  }

  public static int blend(int... scores) {
    if (scores.length == 0) {
      return 0;
    }
    long total = 0L;
    for (int score : scores) {
      total += score;
    }
    return clamp(Math.round((double) total / scores.length));
  }

  private static int clamp(long score) {
    if (score < 0L) {
      return 0;
    }
    return score > MAXIMUM_SCORE ? MAXIMUM_SCORE : (int) score;
  }
}
