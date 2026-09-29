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

import art.arcane.react.api.sampler.ReactTickedSampler;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public class SamplerMemoryPressure extends ReactTickedSampler {
  public static final String ID = "memory-pressure";
  private static final long WAKE_GAP_NANOS = TimeUnit.SECONDS.toNanos(1L);
  private static final double NANOS_PER_SECOND = 1_000_000_000D;
  private transient final AtomicLong lastMemory;
  private transient final AtomicLong lastSampleNanos;
  private transient final LongSupplier usedMemory;
  private transient final LongSupplier nanoClock;

  public SamplerMemoryPressure() {
    this(SamplerMemoryPressure::readUsedMemory, System::nanoTime);
  }

  SamplerMemoryPressure(LongSupplier usedMemory, LongSupplier nanoClock) {
    super(ID, 50, 20);
    this.usedMemory = usedMemory;
    this.nanoClock = nanoClock;
    this.lastMemory = new AtomicLong(usedMemory.getAsLong());
    this.lastSampleNanos = new AtomicLong(nanoClock.getAsLong());
  }

  @Override
  public Material getIcon() {
    return Material.WATER_BUCKET;
  }

  @Override
  public double onSample() {
    long now = nanoClock.getAsLong();
    long mem = usedMemory.getAsLong();
    long elapsedNanos = now - lastSampleNanos.getAndSet(now);
    long allocated = mem - lastMemory.getAndSet(mem);
    if (elapsedNanos <= 0L || elapsedNanos > WAKE_GAP_NANOS || allocated <= 0L) {
      return 0D;
    }

    return allocated * (NANOS_PER_SECOND / elapsedNanos);
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
    return Form.memSizeSplit((long) t, 1)[1] + "/s";
  }

  private static long readUsedMemory() {
    Runtime runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
  }
}
