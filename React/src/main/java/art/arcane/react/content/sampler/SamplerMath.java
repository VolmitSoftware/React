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

package art.arcane.react.content.sampler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class SamplerMath {
  private SamplerMath() {
  }

  static double percentile(Collection<Double> values, double percentile) {
    if (values == null || values.isEmpty()) {
      return 0;
    }

    List<Double> sorted = new ArrayList<>(values.size());
    for (Double value : values) {
      if (value != null && Double.isFinite(value)) {
        sorted.add(value);
      }
    }

    if (sorted.isEmpty()) {
      return 0;
    }

    Collections.sort(sorted);
    double p = clip(percentile, 0, 1);
    double rank = p * (sorted.size() - 1);
    int low = (int) Math.floor(rank);
    int high = (int) Math.ceil(rank);
    if (low == high) {
      return sorted.get(low);
    }

    double weight = rank - low;
    return (sorted.get(low) * (1D - weight)) + (sorted.get(high) * weight);
  }

  static double clip(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}
