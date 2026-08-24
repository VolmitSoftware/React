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

import art.arcane.chrono.RollingSequence;
import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.TickedObject;

import java.util.concurrent.atomic.AtomicLong;

public abstract class ReactTickedSampler extends TickedObject implements Sampler {
  private static final long FAILURE_LOG_INTERVAL_MS = 10_000L;
  private transient final RollingSequence ssequence;
  private transient final AtomicLong slastSample;
  private transient final AtomicLong slastFailureLog;
  private transient final long sactiveInterval;
  private transient final Object sequenceLock;
  private transient volatile boolean ssleeping;
  private transient volatile boolean sregistered;

  public ReactTickedSampler(String id, long tickInterval, int memory) {
    super("sampler", id, tickInterval);
    this.ssequence = new RollingSequence(memory);
    this.sactiveInterval = tickInterval;
    this.slastSample = new AtomicLong(0);
    this.slastFailureLog = new AtomicLong(0L);
    this.sequenceLock = new Object();
    this.ssleeping = true;
    this.sregistered = true;
  }

  @Override
  public void start() {
    if (sregistered) {
      return;
    }

    React.instance.getTicker().register(this);
    sregistered = true;
  }

  public void stop() {
    if (!sregistered) {
      return;
    }

    unregister();
    sregistered = false;
  }

  public abstract double onSample();

  @Override
  public double sample() {
    slastSample.set(System.currentTimeMillis());

    if (ssleeping) {
      setSsleeping(false);
    }

    synchronized (sequenceLock) {
      return ssequence.getAverage();
    }
  }

  private void setSsleeping(boolean ssleeping) {
    boolean wasSleeping = this.ssleeping;
    this.ssleeping = ssleeping;
    setTinterval(ssleeping ? 1000 : sactiveInterval);
    if (!wasSleeping && ssleeping) {
      synchronized (sequenceLock) {
        ssequence.resetExtremes();
      }
    }
  }

  @Override
  public void onTick() {
    if (ssleeping) {
      return;
    }

    if (System.currentTimeMillis() - slastSample.get() > 1000) {
      setSsleeping(true);
      return;
    }

    try {
      double sample = onSample();
      synchronized (sequenceLock) {
        ssequence.put(sample);
      }
    } catch (Throwable e) {
      long now = System.currentTimeMillis();
      long last = slastFailureLog.get();
      if (now - last >= FAILURE_LOG_INTERVAL_MS && slastFailureLog.compareAndSet(last, now)) {
        React.reportError("Sampler " + getId() + " failed: " + e.getClass().getSimpleName()
            + (e.getMessage() == null ? "" : " - " + e.getMessage()), e);
      }
    }
  }

  public String getId() {
    return super.getTid();
  }
}
