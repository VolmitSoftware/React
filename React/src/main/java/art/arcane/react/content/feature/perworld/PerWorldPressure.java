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

package art.arcane.react.content.feature.perworld;

import org.bukkit.World;

import java.util.Objects;

public final class PerWorldPressure {
  public static final PerWorldPressure NORMAL = new PerWorldPressure(Mode.NORMAL, 0D);

  private final Mode mode;
  private final double meanMs;

  public PerWorldPressure(Mode mode, double meanMs) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.meanMs = meanMs;
  }

  public Mode getMode() {
    return mode;
  }

  public double getMeanMs() {
    return meanMs;
  }

  public boolean isPressure() {
    return mode == Mode.PRESSURE || mode == Mode.PANIC;
  }

  public boolean isPanic() {
    return mode == Mode.PANIC;
  }

  public static PerWorldPressure get(World world) {
    if (world == null) {
      return NORMAL;
    }
    return ReactScopedPressure.localPressure(world);
  }

  public enum Mode {
    NORMAL,
    PRESSURE,
    PANIC
  }
}
