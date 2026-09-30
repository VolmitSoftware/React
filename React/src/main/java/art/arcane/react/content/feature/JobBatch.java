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

package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.controller.JobController;
import art.arcane.react.util.common.scheduling.J;

import java.util.ArrayDeque;
import java.util.Objects;

final class JobBatch {
  static final int MAX_UNITS_PER_RUN = 64;
  static final long MAX_NANOS_PER_RUN = 250_000L;
  private static final double DEFAULT_TICK_BUDGET_MS = 1D;
  private static final int TICK_BUDGET_SHARES = 4;

  private final Limits limits;
  private final Runnable onComplete;
  private final ArrayDeque<Runnable> units = new ArrayDeque<>();
  private boolean sealed;

  JobBatch(Limits limits, Runnable onComplete) {
    this.limits = Objects.requireNonNull(limits);
    this.onComplete = Objects.requireNonNull(onComplete);
  }

  static Limits scanLimits() {
    JobController controller = React.controller(JobController.class);
    return Limits.forTickBudget(controller == null ? DEFAULT_TICK_BUDGET_MS : controller.getMaxComputeTime());
  }

  void submit(Runnable unit) {
    if (sealed) {
      throw new IllegalStateException("Cannot add work to a sealed job batch");
    }
    units.addLast(Objects.requireNonNull(unit));
  }

  void seal() {
    sealed = true;
    if (units.isEmpty()) {
      onComplete.run();
      return;
    }
    schedule();
  }

  private void drain() {
    long startedAt = System.nanoTime();
    int ran = 0;
    while (true) {
      runUnit(units.pollFirst());
      ran++;
      if (units.isEmpty()) {
        onComplete.run();
        return;
      }
      if (ran >= limits.maxUnitsPerRun() || System.nanoTime() - startedAt >= limits.maxNanosPerRun()) {
        schedule();
        return;
      }
    }
  }

  private void schedule() {
    try {
      J.s(this::drain);
    } catch (RuntimeException | Error failure) {
      units.clear();
      onComplete.run();
      throw failure;
    }
  }

  private void runUnit(Runnable unit) {
    try {
      unit.run();
    } catch (Throwable failure) {
      React.reportError("React scheduler job failed", failure);
    }
  }

  record Limits(int maxUnitsPerRun, long maxNanosPerRun) {
    Limits {
      maxUnitsPerRun = Math.max(1, maxUnitsPerRun);
      maxNanosPerRun = Math.max(1L, maxNanosPerRun);
    }

    static Limits forTickBudget(double tickBudgetMs) {
      double budgetMs = Double.isFinite(tickBudgetMs) && tickBudgetMs > 0D ? tickBudgetMs : DEFAULT_TICK_BUDGET_MS;
      long shareNanos = Math.round(budgetMs * 1_000_000D / TICK_BUDGET_SHARES);
      return new Limits(MAX_UNITS_PER_RUN, Math.min(MAX_NANOS_PER_RUN, shareNanos));
    }
  }
}
