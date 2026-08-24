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
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;

import java.util.concurrent.atomic.AtomicLong;

public class SamplerTicksPerSecond extends ReactTickedSampler {
  public static final String ID = "ticks-per-second";
  private transient final AtomicLong lastTick;
  private transient final AtomicLong lastTickDuration;
  private transient final AtomicLong lastTickDurationSync;
  private transient int syncTaskId;
  private int countUpTickTimeThresholdMS = 3000;

  public SamplerTicksPerSecond() {
    super(ID, 50, 7);
    this.lastTickDuration = new AtomicLong(50);
    this.lastTickDurationSync = new AtomicLong(50);
    this.lastTick = new AtomicLong(System.currentTimeMillis());
    this.syncTaskId = -1;
  }

  @Override
  public void start() {
    super.start();
    if (syncTaskId < 0) {
      lastTick.set(System.currentTimeMillis());
      lastTickDuration.set(50L);
      lastTickDurationSync.set(50L);
      syncTaskId = J.sr(this::onSyncTick, 0);
    }
  }

  @Override
  public Material getIcon() {
    return Material.NAUTILUS_SHELL;
  }

  private void onSyncTick() {
    onSyncTick(System.currentTimeMillis());
  }

  void onSyncTick(long now) {
    long previousTick = lastTick.getAndSet(now);
    lastTickDurationSync.set(Math.max(0L, now - previousTick));
  }

  @Override
  public double onSample() {
    return onSample(System.currentTimeMillis());
  }

  double onSample(long now) {
    lastTickDuration.set(Math.max(0L, now - lastTick.get()));
    return 1000D / Math.max(50D, Math.max((double) lastTickDuration.get(), (double) lastTickDurationSync.get()));
  }

  @Override
  public String formattedValue(double t) {
    long dur = System.currentTimeMillis() - lastTick.get();

    if (dur > Math.max(1, countUpTickTimeThresholdMS)) {
      return Form.durationSplit(dur, 1)[0];
    }

    if (t > 19.98) {
      return "20";
    }

    return Form.f(Math.round(t), 0);
  }

  @Override
  public String formattedSuffix(double t) {
    long dur = System.currentTimeMillis() - lastTick.get();

    if (dur > Math.max(1, countUpTickTimeThresholdMS)) {
      return Form.durationSplit(dur, 1)[1];
    }

    return "TPS";
  }

  @Override
  public void unregister() {
    if (syncTaskId >= 0) {
      J.csr(syncTaskId);
      syncTaskId = -1;
    }
    super.unregister();
  }
}
