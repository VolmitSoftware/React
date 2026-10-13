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
import art.arcane.react.core.telemetry.HostTelemetrySnapshot;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.util.function.Supplier;

public class SamplerMemoryPressure extends ReactCachedSampler {
  public static final String ID = "memory-pressure";
  private transient final Supplier<HostTelemetrySnapshot> host;

  public SamplerMemoryPressure() {
    this(TelemetryController::currentHostSnapshot);
  }

  SamplerMemoryPressure(Supplier<HostTelemetrySnapshot> host) {
    super(ID, 1000L);
    this.host = host;
  }

  @Override
  public Material getIcon() {
    return Material.WATER_BUCKET;
  }

  @Override
  public String getName() {
    return "JVM Allocation Rate";
  }

  @Override
  public Metadata metadata() {
    return HostMetric.ALLOCATION.metadata();
  }

  @Override
  public boolean isSampleAvailable() {
    return host.get().reading(HostMetric.ALLOCATION).fresh(HostMetric.ALLOCATION, System.currentTimeMillis()).available();
  }

  @Override
  public Reading captureReading() {
    return host.get().reading(HostMetric.ALLOCATION).fresh(HostMetric.ALLOCATION, System.currentTimeMillis()).reading(HostMetric.ALLOCATION);
  }

  @Override
  public double onSample() {
    return host.get().reading(HostMetric.ALLOCATION).value();
  }

  @Override
  public String formattedValue(double value) {
    return Form.memSizeSplit(Math.max(0L, (long) value), 1)[0];
  }

  @Override
  public String formattedSuffix(double value) {
    return Form.memSizeSplit(Math.max(0L, (long) value), 1)[1] + "/s";
  }
}
