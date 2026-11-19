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

package art.arcane.react.api.feature;

public class PressureGate {
  private volatile boolean engaged;
  private volatile long pressureSinceMs;
  private volatile long calmSinceMs;

  public boolean update(long nowMs, boolean pressured, boolean calm, long sustainEngageMs, long sustainReleaseMs) {
    if (!engaged) {
      if (!pressured) {
        pressureSinceMs = 0L;
        return false;
      }

      if (pressureSinceMs == 0L) {
        pressureSinceMs = nowMs;
        return false;
      }

      if (nowMs - pressureSinceMs >= Math.max(0L, sustainEngageMs)) {
        engaged = true;
        pressureSinceMs = 0L;
        calmSinceMs = 0L;
      }

      return engaged;
    }

    if (!calm) {
      calmSinceMs = 0L;
      return true;
    }

    if (calmSinceMs == 0L) {
      calmSinceMs = nowMs;
      return true;
    }

    if (nowMs - calmSinceMs >= Math.max(0L, sustainReleaseMs)) {
      engaged = false;
      pressureSinceMs = 0L;
      calmSinceMs = 0L;
    }

    return engaged;
  }

  public boolean isEngaged() {
    return engaged;
  }

  public void reset() {
    engaged = false;
    pressureSinceMs = 0L;
    calmSinceMs = 0L;
  }
}
