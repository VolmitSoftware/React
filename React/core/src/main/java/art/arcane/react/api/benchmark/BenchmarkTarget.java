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

public enum BenchmarkTarget {
  PROCESSOR(BenchmarkMessages.NAME_CPU),
  MEMORY(BenchmarkMessages.NAME_MEMORY),
  DRIVE(BenchmarkMessages.NAME_DRIVE);

  private final TextKey message;

  BenchmarkTarget(TextKey message) {
    this.message = message;
  }

  public TextKey message() {
    return message;
  }
}
