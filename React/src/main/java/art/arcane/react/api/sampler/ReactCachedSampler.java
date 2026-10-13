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
import art.arcane.react.util.common.scheduling.J;
import com.google.common.util.concurrent.AtomicDouble;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public abstract class ReactCachedSampler implements Sampler {
  protected transient final long sampleDelay;
  private transient final ChronoLatch slatch;
  private transient final AtomicDouble slast;
  private transient final Object sampleLock;
  private transient final String sid;
  private transient final AtomicBoolean mainRefreshInFlight;
  private transient volatile double mainRefreshValue;
  private transient long sampledAtMs;
  private transient volatile boolean mainRefreshUsed;
  private transient volatile boolean mainRefreshReady;
  private transient volatile boolean runtimeStarted;
  private transient volatile SampleController sampleController;
  private transient final SamplerHealth health = new SamplerHealth();

  public ReactCachedSampler(String id, long sampleDelay) {
    this.sid = id;
    this.sampleDelay = sampleDelay;
    this.slatch = new ChronoLatch(sampleDelay, true);
    this.slast = new AtomicDouble();
    this.sampleLock = new Object();
    this.mainRefreshInFlight = new AtomicBoolean(false);
    this.runtimeStarted = false;
  }

  // Refresh-behind main-thread sampling: returns the last computed value immediately
  // and queues a single-flight recompute on the main thread. Never blocks the caller,
  // unlike executeSync which sleep-polls until the main thread runs the job.
  protected double sampleOnMainThread(Supplier<Double> compute) {
    mainRefreshUsed = true;
    if (J.isPrimaryThread()) {
      Double value = computeRefresh(compute);
      if (value != null) {
        publishMainRefresh(value);
      }
      return mainRefreshValue;
    }

    if (mainRefreshInFlight.compareAndSet(false, true)) {
      boolean dispatched = false;
      long submittedNanos = System.nanoTime();

      try {
        J.s(() -> {
          try {
            health.queued(submittedNanos);
            Double value = computeRefresh(compute);
            if (value != null) {
              publishMainRefresh(value);
            }
          } finally {
            mainRefreshInFlight.set(false);
          }
        });
        dispatched = true;
      } catch (RuntimeException | Error failure) {
        health.finish(health.begin(), false);
        throw failure;
      } finally {
        if (!dispatched) {
          mainRefreshInFlight.set(false);
        }
      }
    } else {
      health.skipped();
    }

    return mainRefreshValue;
  }

  private Double computeRefresh(Supplier<Double> compute) {
    long startedNanos = health.begin();
    boolean success = false;
    try {
      Double value = compute.get();
      success = value != null && Double.isFinite(value);
      return value;
    } finally {
      health.finish(startedNanos, success);
    }
  }

  @Override
  public Metadata metadata() {
    return SamplerMetadata.forId(getId(), sampleDelay);
  }

  @Override
  public Diagnostics diagnostics() {
    return health.snapshot();
  }

  @Override
  public String availabilityReason() {
    if (!runtimeStarted) {
      return "stopped";
    }
    if (health.failed()) {
      return "sampling-failed";
    }
    return sampledAtMs <= 0L ? "warming-up" : "source-unavailable";
  }

  @Override
  public void start() {
    sampleController = null;
    mainRefreshInFlight.set(false);
    synchronized (sampleLock) {
      sampledAtMs = 0L;
      mainRefreshReady = false;
    }
    runtimeStarted = true;
  }

  @Override
  public void stop() {
    runtimeStarted = false;
    sampleController = null;
  }

  public abstract double onSample();

  @Override
  public double sample() {
    markDemand();
    return read();
  }

  @Override
  public double capture() {
    return read();
  }

  @Override
  public Reading captureReading() {
    synchronized (sampleLock) {
      double value = read();
      return reading(value, readingTimestampMs(), runtimeStarted && sampledAtMs > 0L && !health.failed() && isSampleAvailable());
    }
  }

  protected long readingTimestampMs() {
    return sampledAtMs;
  }

  private double read() {
    if (!canSampleNow()) {
      return slast.get();
    }
    if (!slatch.couldFlip()) {
      health.cacheHit();
      return slast.get();
    }

    synchronized (sampleLock) {
      if (slatch.flip()) {
        long startedNanos = System.nanoTime();
        boolean success = false;
        try {
          double value = onSample();
          success = Double.isFinite(value);
          slast.set(value);
          if (!mainRefreshUsed && success) {
            sampledAtMs = System.currentTimeMillis();
          }
        } finally {
          if (!mainRefreshUsed) {
            health.begin();
            health.finish(startedNanos, success);
          }
        }
      } else {
        health.cacheHit();
      }
      return slast.get();
    }
  }

  @Override
  public boolean isSampleAvailable() {
    return !health.failed() && (!mainRefreshUsed || mainRefreshReady);
  }

  public String getId() {
    return sid;
  }

  private void publishMainRefresh(double value) {
    synchronized (sampleLock) {
      mainRefreshValue = value;
      mainRefreshReady = true;
      sampledAtMs = System.currentTimeMillis();
      slast.set(value);
    }
  }

  private boolean canSampleNow() {
    if (!runtimeStarted) {
      return false;
    }

    try {
      SampleController controller = sampleController;
      if (controller == null) {
        controller = React.controller(SampleController.class);
        sampleController = controller;
      }

      return controller == null || controller.canSample(this);
    } catch (Throwable ignored) {
      return runtimeStarted;
    }
  }
}
