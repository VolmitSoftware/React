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

import java.util.concurrent.atomic.AtomicInteger;

public abstract class ReactCachedRateSampler extends ReactCachedSampler {
  private static final double D1_OVER_SECONDS = 1.0 / 1000D;
  private transient AtomicInteger hits;
  private transient RollingSequence avg;
  private transient long lastHit = 0L;
  private transient long lastSample = 0L;
  private int rollingAverageSamples = 5;

  public ReactCachedRateSampler(String id, long sampleDelay) {
    super(id, sampleDelay);
  }

  @Override
  public double onSample() {
    if (hits == null || avg == null) {
      return 0D;
    }

    if (lastSample == 0) {
      lastSample = M.ms();
    }

    long t = M.ms();

    if (t - lastHit > sampleDelay) {
      avg.put(0);
      lastHit = t;
    }

    int r = hits.getAndSet(0);
    long dur = Math.max(M.ms() - lastSample, 1000);
    lastSample = t;
    avg.put(r / (dur * D1_OVER_SECONDS));

    return Math.max(0, avg.getAverage());
  }

  @Override
  public void start() {
    super.start();
    avg = new RollingSequence(Math.max(1, rollingAverageSamples));
    hits = new AtomicInteger(0);
    lastHit = 0L;
    lastSample = 0L;
  }

  @Override
  public void stop() {
    super.stop();
    avg = null;
    hits = null;
    lastHit = 0L;
    lastSample = 0L;
  }

  public void increment(int amount) {
    AtomicInteger local = hits;
    if (local != null) {
      local.addAndGet(amount);
    }
  }

  public void increment() {
    AtomicInteger local = hits;
    if (local != null) {
      local.incrementAndGet();
    }
  }
}
