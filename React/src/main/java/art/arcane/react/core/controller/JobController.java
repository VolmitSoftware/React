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

package art.arcane.react.core.controller;

import art.arcane.chrono.ChronoLatch;
import art.arcane.chrono.PrecisionStopwatch;
import art.arcane.react.React;
import art.arcane.react.api.event.layer.ServerTickEvent;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.plugin.IController;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RollingSequence;
import lombok.Data;
import org.bukkit.Bukkit;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class JobController implements IController {
  private static final double MIN_COMPUTE_TIME_MS = 0.05;
  private static final double MIN_COMPUTE_TARGET_MS = 0.01;
  private static final double DEFAULT_MAX_COMPUTE_TIME_MS = 1D;
  private static final double DEFAULT_HIGH_UTILIZATION = 0.75;
  private static final double DEFAULT_LOW_UTILIZATION = 0.25;
  private transient final RollingSequence usageCyclePercent;
  private transient final ConcurrentLinkedDeque<Runnable> jobs;
  private transient final AtomicInteger queueDepth;
  private double maxComputeTime = DEFAULT_MAX_COMPUTE_TIME_MS;
  private long maxSpikeInterval = 250;
  private double currentComputeTarget = MIN_COMPUTE_TARGET_MS;
  private double highUtilizationThresholdPercent = DEFAULT_HIGH_UTILIZATION;
  private double lowUtilizationThresholdPercent = DEFAULT_LOW_UTILIZATION;
  private transient ServerTickEvent ste = new ServerTickEvent();
  private transient RollingSequence usage = new RollingSequence(20);
  private transient double costPerJob = 0.1;
  private transient ChronoLatch spikeLatch;
  private transient int code;
  private transient volatile double overBudget = 0;
  private transient boolean pollFromTail = false;

  public JobController() {
    usageCyclePercent = new RollingSequence(7);
    jobs = new ConcurrentLinkedDeque<>();
    queueDepth = new AtomicInteger(0);
  }

  @Override
  public String getName() {
    return "Job";
  }

  @Override
  public String getId() {
    return "job";
  }

  @Override
  public void start() {
    spikeLatch = new ChronoLatch(maxSpikeInterval);
    code = J.sr(this::execute, 0);
  }

  @Override
  public void stop() {
    J.csr(code);
    jobs.clear();
    queueDepth.set(0);
  }


  @Override
  public void postStart() {

  }

  @Override
  public boolean reloadFromDisk(boolean overwriteOnReadFailure) {
    boolean loaded = IController.super.reloadFromDisk(overwriteOnReadFailure);
    clampConfiguration();
    return loaded;
  }

  @Override
  public boolean applyConfigurationSnapshot(Object loadedObject) {
    boolean applied = IController.super.applyConfigurationSnapshot(loadedObject);
    clampConfiguration();
    return applied;
  }

  public double getQueuedComputeTime() {
    return getQueueSize() * costPerJob;
  }

  public int getQueueSize() {
    return queueDepth.get();
  }

  public void execute() {
    Bukkit.getPluginManager().callEvent(ste);
    if (overBudget > maxComputeTime || getQueueSize() <= 0) {
      overBudget = Math.max(0D, overBudget - maxComputeTime);
      usage.put(0);
      return;
    }

    int executed = 0;
    boolean alternatePoll = getQueueSize() > 5;
    PrecisionStopwatch p = PrecisionStopwatch.start();

    while (p.getMilliseconds() < currentComputeTarget) {
      Runnable job = pollJob(alternatePoll);
      if (job == null) {
        break;
      }

      try {
        job.run();
      } catch (Throwable e) {
        React.reportError("React scheduler job failed", e);
      }

      executed++;
    }

    double timeUsed = p.getMilliseconds();
    usage.put(timeUsed);
    if (timeUsed > currentComputeTarget) {
      overBudget += timeUsed - currentComputeTarget;
    }

    if (executed > 0) {
      costPerJob = timeUsed / (double) executed;
    }
    usageCyclePercent.put(timeUsed / currentComputeTarget);

    if (usageCyclePercent.getAverage() > highUtilizationThresholdPercent) {
      currentComputeTarget = M.lerp(currentComputeTarget, maxComputeTime, 0.01);
    } else if (usageCyclePercent.getAverage() < lowUtilizationThresholdPercent) {
      currentComputeTarget = M.lerp(currentComputeTarget, MIN_COMPUTE_TARGET_MS, 0.01);
    }

    currentComputeTarget = M.clip(currentComputeTarget, MIN_COMPUTE_TARGET_MS, maxComputeTime);
  }

  public void queue(Runnable r) {
    if (r == null) {
      return;
    }

    jobs.offerLast(r);
    queueDepth.incrementAndGet();
  }

  private void clampConfiguration() {
    maxComputeTime = Math.max(MIN_COMPUTE_TIME_MS, finiteOr(maxComputeTime, DEFAULT_MAX_COMPUTE_TIME_MS));
    currentComputeTarget = finiteOr(currentComputeTarget, MIN_COMPUTE_TARGET_MS);
    highUtilizationThresholdPercent = M.clip(finiteOr(highUtilizationThresholdPercent, DEFAULT_HIGH_UTILIZATION), 0D, 1D);
    lowUtilizationThresholdPercent = M.clip(finiteOr(lowUtilizationThresholdPercent, DEFAULT_LOW_UTILIZATION), 0D, 1D);
  }

  private static double finiteOr(double value, double fallback) {
    return Double.isFinite(value) ? value : fallback;
  }

  private Runnable pollJob(boolean alternatePoll) {
    Runnable job;
    if (alternatePoll && pollFromTail) {
      job = pollLast();
      if (job == null) {
        job = pollFirst();
      }
    } else {
      job = pollFirst();
      if (job == null && alternatePoll) {
        job = pollLast();
      }
    }

    if (alternatePoll && job != null) {
      pollFromTail = !pollFromTail;
    }

    return job;
  }

  private Runnable pollFirst() {
    Runnable job = jobs.pollFirst();
    if (job != null) {
      decrementQueueSize();
    }

    return job;
  }

  private Runnable pollLast() {
    Runnable job = jobs.pollLast();
    if (job != null) {
      decrementQueueSize();
    }

    return job;
  }

  private void decrementQueueSize() {
    while (true) {
      int current = queueDepth.get();
      if (current <= 0) {
        return;
      }

      if (queueDepth.compareAndSet(current, current - 1)) {
        return;
      }
    }
  }
}
