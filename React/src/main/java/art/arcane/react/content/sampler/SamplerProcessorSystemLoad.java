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
import art.arcane.react.util.atomics.AsyncRequest;
import art.arcane.react.util.reflect.Platform;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

public class SamplerProcessorSystemLoad extends ReactCachedSampler {
  public static final String ID = "processor-system-load";
  private transient final AsyncRequest<Double> poller;

  public SamplerProcessorSystemLoad() {
    super(ID, 100);
    poller = new AsyncRequest<>(Platform.CPU::getCPULoad, 0D);
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
    return Material.RED_CANDLE;
  }

  @Override
  public double onSample() {
    return normalizeCpuLoad(poller.request());
  }

  @Override
  public String formattedValue(double t) {
    return Form.pc(normalizeCpuLoad(t), 0);
  }

  @Override
  public String formattedSuffix(double t) {
    return "CPU";
  }
}
