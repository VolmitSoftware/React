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
import art.arcane.react.core.telemetry.HostTelemetrySnapshot;
import art.arcane.react.core.telemetry.HostMetric;
import art.arcane.react.core.telemetry.HostReading;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.util.function.Supplier;

public class SamplerProcessorOutsideLoad extends ReactCachedSampler {
  public static final String ID = "processor-outside";
  private transient final Supplier<HostTelemetrySnapshot> host;

  public SamplerProcessorOutsideLoad() {
    this(TelemetryController::currentHostSnapshot);
  }

  SamplerProcessorOutsideLoad(Supplier<HostTelemetrySnapshot> host) {
    super(ID, 250);
    this.host = host;
  }

  private static double normalizeCpuLoad(double raw) {
    if (!Double.isFinite(raw) || raw <= 0D) {
      return 0D;
    }

    if (raw <= 1D) {
      return raw;
    }

    if (raw <= 100D) {
      return raw / 100D;
    }

    return 1D;
  }

  @Override
  public Material getIcon() {
    return Material.GREEN_CANDLE;
  }

  @Override
  public Metadata metadata() {
    return new Metadata(1_000L, 1_000L, Quality.ESTIMATED, "ratio");
  }

  @Override
  public boolean isSampleAvailable() {
    HostTelemetrySnapshot snapshot = host.get();
    return snapshot.reading(HostMetric.CPU_SYSTEM).fresh(HostMetric.CPU_SYSTEM, System.currentTimeMillis()).available() && snapshot.reading(HostMetric.CPU_PROCESS).fresh(HostMetric.CPU_PROCESS, System.currentTimeMillis()).available();
  }

  @Override
  public Reading captureReading() {
    HostTelemetrySnapshot snapshot = host.get();
    HostReading system = snapshot.reading(HostMetric.CPU_SYSTEM).fresh(HostMetric.CPU_SYSTEM, System.currentTimeMillis());
    HostReading process = snapshot.reading(HostMetric.CPU_PROCESS).fresh(HostMetric.CPU_PROCESS, System.currentTimeMillis());
    return reading(Math.max(0D, system.value() - process.value()), Math.min(system.sampledAtMs(), process.sampledAtMs()),
        system.available() && process.available(), system.available() ? process.unavailableReason() : system.unavailableReason());
  }

  @Override
  public double onSample() {
    HostTelemetrySnapshot snapshot = host.get();
    return Math.max(0D, snapshot.reading(HostMetric.CPU_SYSTEM).value() - snapshot.reading(HostMetric.CPU_PROCESS).value());
  }

  @Override
  public String formattedValue(double t) {
    return Form.pc(normalizeCpuLoad(t), 0);
  }

  @Override
  public String formattedSuffix(double t) {
    return "xCPU";
  }
}
