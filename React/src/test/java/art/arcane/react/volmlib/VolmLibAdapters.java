package art.arcane.react.volmlib;

import art.arcane.volmlib.util.math.RollingSequence;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;

/** Binds the library-neutral scenarios to the VolmLib classes React ships with. */
final class VolmLibAdapters {
  private VolmLibAdapters() {
  }

  static RollingSequenceScenarios.Seq sequence(int size) {
    RollingSequence sequence = new RollingSequence(size);
    return new RollingSequenceScenarios.Seq() {
      @Override
      public void put(double value) {
        sequence.put(value);
      }

      @Override
      public double getAverage() {
        return sequence.getAverage();
      }

      @Override
      public double getMin() {
        return sequence.getMin();
      }

      @Override
      public double getMax() {
        return sequence.getMax();
      }

      @Override
      public double addLast(int amount) {
        return sequence.addLast(amount);
      }

      @Override
      public void resetExtremes() {
        sequence.resetExtremes();
      }

      @Override
      public void setPrecision(boolean precision) {
        sequence.setPrecision(precision);
      }

      @Override
      public int size() {
        return sequence.size();
      }

      @Override
      public boolean isDirty() {
        return sequence.isDirty();
      }
    };
  }

  static TimingScenarios.WatchFactory stopwatches() {
    return new TimingScenarios.WatchFactory() {
      @Override
      public TimingScenarios.Watch create() {
        return watch(new PrecisionStopwatch());
      }

      @Override
      public TimingScenarios.Watch start() {
        return watch(PrecisionStopwatch.start());
      }
    };
  }

  static TimingScenarios.LatchFactory latches() {
    return new TimingScenarios.LatchFactory() {
      @Override
      public TimingScenarios.Latch create(long interval) {
        return latch(new ChronoLatch(interval));
      }

      @Override
      public TimingScenarios.Latch create(long interval, boolean openedAtStart) {
        return latch(new ChronoLatch(interval, openedAtStart));
      }
    };
  }

  private static TimingScenarios.Watch watch(PrecisionStopwatch stopwatch) {
    return new TimingScenarios.Watch() {
      @Override
      public void begin() {
        stopwatch.begin();
      }

      @Override
      public void end() {
        stopwatch.end();
      }

      @Override
      public void reset() {
        stopwatch.reset();
      }

      @Override
      public double getMilliseconds() {
        return stopwatch.getMilliseconds();
      }

      @Override
      public double getTicks() {
        return stopwatch.getTicks();
      }

      @Override
      public double getSeconds() {
        return stopwatch.getSeconds();
      }

      @Override
      public double getMinutes() {
        return stopwatch.getMinutes();
      }

      @Override
      public double getHours() {
        return stopwatch.getHours();
      }

      @Override
      public long getNanoseconds() {
        return stopwatch.getNanoseconds();
      }

      @Override
      public long getNanos() {
        return stopwatch.getNanos();
      }

      @Override
      public long getStartNano() {
        return stopwatch.getStartNano();
      }

      @Override
      public long getMillis() {
        return stopwatch.getMillis();
      }

      @Override
      public long getStartMillis() {
        return stopwatch.getStartMillis();
      }

      @Override
      public double getTime() {
        return stopwatch.getTime();
      }

      @Override
      public boolean isProfiling() {
        return stopwatch.isProfiling();
      }
    };
  }

  private static TimingScenarios.Latch latch(ChronoLatch latch) {
    return new TimingScenarios.Latch() {
      @Override
      public boolean flip() {
        return latch.flip();
      }

      @Override
      public boolean couldFlip() {
        return latch.couldFlip();
      }

      @Override
      public void flipDown() {
        latch.flipDown();
      }
    };
  }
}
