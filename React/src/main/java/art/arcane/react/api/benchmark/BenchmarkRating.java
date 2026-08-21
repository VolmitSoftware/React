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

import art.arcane.react.localization.catalog.BenchmarkMessages;
import art.arcane.volmlib.util.localization.TextKey;

public enum BenchmarkRating {
  ULTRA_SLOW(0, BenchmarkMessages.SPEED_ULTRA_SLOW, "dark_red"),
  VERY_SLOW(30, BenchmarkMessages.SPEED_VERY_SLOW, "red"),
  SLOW(45, BenchmarkMessages.SPEED_SLOW, "gold"),
  AVERAGE(60, BenchmarkMessages.SPEED_AVERAGE, "yellow"),
  GOOD(80, BenchmarkMessages.SPEED_GOOD, "green"),
  FAST(100, BenchmarkMessages.SPEED_FAST, "green"),
  VERY_FAST(125, BenchmarkMessages.SPEED_VERY_FAST, "aqua"),
  ULTRA_FAST(160, BenchmarkMessages.SPEED_ULTRA_FAST, "aqua"),
  INSANELY_FAST(200, BenchmarkMessages.SPEED_INSANELY_FAST, "light_purple");

  private final int minimumScore;
  private final TextKey message;
  private final String color;

  BenchmarkRating(int minimumScore, TextKey message, String color) {
    this.minimumScore = minimumScore;
    this.message = message;
    this.color = color;
  }

  public static BenchmarkRating of(int score) {
    BenchmarkRating[] ratings = values();
    for (int index = ratings.length - 1; index > 0; index--) {
      if (score >= ratings[index].minimumScore) {
        return ratings[index];
      }
    }
    return ULTRA_SLOW;
  }

  public int minimumScore() {
    return minimumScore;
  }

  public TextKey message() {
    return message;
  }

  public String color() {
    return color;
  }
}
