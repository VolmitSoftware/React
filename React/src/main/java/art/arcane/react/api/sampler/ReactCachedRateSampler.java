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

package art.arcane.react.api.sampler;

import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RollingSequence;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

public abstract class ReactCachedRateSampler extends ReactCachedSampler {
  private static final double D1_OVER_SECONDS = 1.0 / 1000D;
  private transient final LongSupplier clock;
  private transient LongAdder hits;
  private transient RollingSequence avg;
  private transient long lastSample = 0L;
  private int rollingAverageSamples = 5;

  public ReactCachedRateSampler(String id, long sampleDelay) {
    this(id, sampleDelay, M::ms);
  }

  ReactCachedRateSampler(String id, long sampleDelay, LongSupplier clock) {
    super(id, sampleDelay);
    this.clock = clock;
  }

  @Override
  public double onSample() {
    LongAdder localHits = hits;
    RollingSequence localAvg = avg;
    if (localHits == null || localAvg == null) {
      return 0D;
    }

    long t = clock.getAsLong();
    long r = localHits.sumThenReset();
    long dur = Math.max(t - lastSample, 1000);
    lastSample = t;
    localAvg.put(r / (dur * D1_OVER_SECONDS));

    return Math.max(0, localAvg.getAverage());
  }

  @Override
  public void start() {
    super.start();
    avg = new RollingSequence(Math.max(1, rollingAverageSamples));
    hits = new LongAdder();
    lastSample = clock.getAsLong();
  }

  @Override
  public void stop() {
    super.stop();
    avg = null;
    hits = null;
    lastSample = 0L;
  }

  public void increment(int amount) {
    LongAdder local = hits;
    if (local != null) {
      local.add(amount);
    }
  }

  public void increment() {
    LongAdder local = hits;
    if (local != null) {
      local.increment();
    }
  }
}
