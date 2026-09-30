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

import art.arcane.react.util.common.scheduling.J;

import java.util.concurrent.atomic.AtomicInteger;

final class JobFanout {
  private final AtomicInteger pending = new AtomicInteger(1);
  private final Runnable onComplete;

  JobFanout(Runnable onComplete) {
    this.onComplete = onComplete;
  }

  void submit(Runnable unit) {
    pending.incrementAndGet();
    try {
      J.s(() -> runUnit(unit));
    } catch (RuntimeException | Error failure) {
      complete();
      throw failure;
    }
  }

  void seal() {
    complete();
  }

  private void runUnit(Runnable unit) {
    try {
      unit.run();
    } finally {
      complete();
    }
  }

  private void complete() {
    if (pending.decrementAndGet() == 0) {
      onComplete.run();
    }
  }
}
