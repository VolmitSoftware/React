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
 */

package art.arcane.react.util.common.scheduling;

import art.arcane.react.React;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;

public final class PromptWait {
  public static final long DEFAULT_TIMEOUT_MS = 120_000L;
  private static final long POLL_MS = 50L;

  private PromptWait() {
  }

  public static Outcome await(BooleanSupplier done) {
    return await(done, PromptWait::reactAvailable, DEFAULT_TIMEOUT_MS, System::nanoTime, J::sleep);
  }

  public static Outcome await(BooleanSupplier done, BooleanSupplier available, long timeoutMS, LongSupplier nanoClock, LongPredicate sleep) {
    long deadline = nanoClock.getAsLong() + TimeUnit.MILLISECONDS.toNanos(timeoutMS);
    while (!done.getAsBoolean()) {
      if (!available.getAsBoolean()) {
        return Outcome.UNAVAILABLE;
      }
      if (nanoClock.getAsLong() >= deadline) {
        return Outcome.TIMED_OUT;
      }
      if (!sleep.test(POLL_MS)) {
        return Outcome.INTERRUPTED;
      }
    }

    return Outcome.COMPLETED;
  }

  public static boolean reactAvailable() {
    React react = React.instance;
    return react != null && react.isEnabled() && react.isReady();
  }

  public enum Outcome {
    COMPLETED,
    TIMED_OUT,
    UNAVAILABLE,
    INTERRUPTED
  }
}
