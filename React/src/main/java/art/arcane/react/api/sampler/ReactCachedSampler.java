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

import art.arcane.chrono.ChronoLatch;
import art.arcane.react.React;
import art.arcane.react.core.controller.SampleController;
import com.google.common.util.concurrent.AtomicDouble;

public abstract class ReactCachedSampler implements Sampler {
  protected transient final long sampleDelay;
  private transient final ChronoLatch slatch;
  private transient final AtomicDouble slast;
  private transient final Object sampleLock;
  private transient final String sid;
  private transient volatile boolean runtimeStarted;

  public ReactCachedSampler(String id, long sampleDelay) {
    this.sid = id;
    this.sampleDelay = sampleDelay;
    this.slatch = new ChronoLatch(sampleDelay, true);
    this.slast = new AtomicDouble();
    this.sampleLock = new Object();
    this.runtimeStarted = false;
  }

  @Override
  public void start() {
    runtimeStarted = true;
  }

  @Override
  public void stop() {
    runtimeStarted = false;
  }

  public abstract double onSample();

  @Override
  public double sample() {
    if (!canSampleNow() || !slatch.couldFlip()) {
      return slast.get();
    }

    synchronized (sampleLock) {
      if (slatch.flip()) {
        slast.set(onSample());
      }
      return slast.get();
    }
  }

  public String getId() {
    return sid;
  }

  private boolean canSampleNow() {
    if (!runtimeStarted) {
      return false;
    }

    try {
      SampleController controller = React.controller(SampleController.class);
      return controller == null || controller.canSample(this);
    } catch (Throwable ignored) {
      return runtimeStarted;
    }
  }
}
