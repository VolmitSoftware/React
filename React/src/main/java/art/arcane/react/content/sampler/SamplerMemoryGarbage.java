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

import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.core.controller.TelemetryController;
import art.arcane.react.core.telemetry.HostMetric;
import art.arcane.react.core.telemetry.HostReading;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

public class SamplerMemoryGarbage extends ReactCachedSampler {
  public static final String ID = "memory-garbage";

  public SamplerMemoryGarbage() {
    super(ID, 1000);
  }

  @Override
  public Material getIcon() {
    return Material.WATER_BUCKET;
  }

  @Override
  public String getName() {
    return "Heap Growth Since GC";
  }

  @Override
  public Metadata metadata() {
    return new Metadata(1_000L, 0L, Quality.ESTIMATED, "bytes");
  }

  @Override
  public boolean isSampleAvailable() {
    return TelemetryController.currentHostSnapshot().reading(HostMetric.GC_AFTER).available();
  }

  @Override
  public double onSample() {
    HostReading after = TelemetryController.currentHostSnapshot().reading(HostMetric.GC_AFTER);
    Runtime runtime = Runtime.getRuntime();
    return after.available() ? Math.max(runtime.totalMemory() - runtime.freeMemory() - after.value(), 0D) : 0D;
  }

  @Override
  public String formattedValue(double t) {
    String[] s = Form.memSizeSplit((long) t, 1);
    if (s[1].equalsIgnoreCase("mb")) {
      return Form.memSizeSplit((long) t, 0)[0];
    } else {
      return s[0];
    }
  }

  @Override
  public String formattedSuffix(double t) {
    return Form.memSizeSplit((long) t, 1)[1];
  }
}
