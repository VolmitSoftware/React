package art.arcane.react.volmlib;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Stopwatch and latch scenarios reduced to clock-independent outcomes. The same code ran against
 * the Chrono 22.9.10 classes to produce the expectations asserted by the characterization tests.
 */
final class TimingScenarios {
  interface Watch {
    void begin();

    void end();

    void reset();

    double getMilliseconds();

    double getTicks();

    double getSeconds();

    double getMinutes();

    double getHours();

    long getNanoseconds();

    long getNanos();

    long getStartNano();

    long getMillis();

    long getStartMillis();

    double getTime();

    boolean isProfiling();
  }

  interface WatchFactory {
    Watch create();

    Watch start();
  }

  interface Latch {
    boolean flip();

    boolean couldFlip();

    void flipDown();
  }

  interface LatchFactory {
    Latch create(long interval);

    Latch create(long interval, boolean openedAtStart);
  }

  private TimingScenarios() {
  }

  static Map<String, String> stopwatch(WatchFactory factory) {
    Map<String, String> results = new LinkedHashMap<>();

    Watch fresh = factory.create();
    results.put("fresh", state(fresh));

    Watch neverBegun = factory.create();
    neverBegun.end();
    results.put("endWithoutBegin", state(neverBegun));

    Watch unstarted = factory.create();
    double unstartedMilliseconds = unstarted.getMilliseconds();
    results.put("unstartedReadsEpochScale", Boolean.toString(unstartedMilliseconds > 1.0E11D));
    results.put("unstartedReadKeepsProfilingOff", Boolean.toString(!unstarted.isProfiling()));

    Watch started = factory.start();
    results.put("startIsProfiling", Boolean.toString(started.isProfiling()));
    results.put("startStampsNano", Boolean.toString(started.getStartNano() != -1L));
    results.put("startStampsMillis", Boolean.toString(Math.abs(System.currentTimeMillis() - started.getStartMillis()) < 5_000L));
    results.put("startLeavesElapsedUnset", started.getNanos() + "," + started.getMillis() + "," + started.getTime());

    Watch timed = factory.create();
    timed.begin();
    sleep(40L);
    timed.end();
    double endedTime = timed.getTime();
    long endedNanos = timed.getNanos();
    long endedMillis = timed.getMillis();
    results.put("endStopsProfiling", Boolean.toString(!timed.isProfiling()));
    results.put("endTimeIsMilliseconds", Boolean.toString(endedTime >= 35D && endedTime < 5_000D));
    results.put("endNanosAreNanoseconds", Boolean.toString(endedNanos >= 35_000_000L && endedNanos < 5_000_000_000L));
    results.put("endMillisAreMilliseconds", Boolean.toString(endedMillis >= 30L && endedMillis < 5_000L));
    results.put("nanosecondsDeriveFromTime", Boolean.toString(timed.getNanoseconds() == (long) (endedTime * 1_000_000D)));
    results.put("timeIsStableWithoutRead", Boolean.toString(timed.getTime() == endedTime));
    timed.end();
    results.put("secondEndIsIgnored", Boolean.toString(timed.getNanos() == endedNanos && timed.getTime() == endedTime));
    sleep(25L);
    double afterEnd = timed.getMilliseconds();
    results.put("readAfterEndKeepsCounting", Boolean.toString(afterEnd >= endedTime + 20D));
    results.put("readOverwritesTime", Boolean.toString(timed.getTime() == afterEnd));
    results.put("readOverwritesNanos", Boolean.toString(timed.getNanos() > endedNanos));

    Watch running = factory.start();
    sleep(20L);
    double first = running.getMilliseconds();
    results.put("readWhileRunningKeepsProfiling", Boolean.toString(running.isProfiling()));
    results.put("readWhileRunningIsMilliseconds", Boolean.toString(first >= 15D && first < 5_000D));
    results.put("nanosMatchMilliseconds", Boolean.toString(Math.abs((running.getNanos() / 1_000_000D) - first) < 2.5D));
    results.put("millisMatchMilliseconds", Boolean.toString(Math.abs(running.getMillis() - first) < 2.5D));
    double hours = running.getHours();
    double minutes = running.getMinutes();
    double seconds = running.getSeconds();
    double ticks = running.getTicks();
    double last = running.getMilliseconds();
    results.put("unitsAreOrdered", Boolean.toString(ordered(first, hours * 3_600_000D) && ordered(hours * 60D, minutes)
        && ordered(minutes * 60D, seconds) && ordered(seconds * 20D, ticks) && ordered(ticks * 50D, last)));
    results.put("unitsStayNearMilliseconds", Boolean.toString((last - first) < 1_000D));

    Watch restarted = factory.start();
    sleep(200L);
    restarted.begin();
    results.put("beginRestarts", Boolean.toString(restarted.getMilliseconds() < 150D));

    Watch cleared = factory.start();
    sleep(2L);
    cleared.getMilliseconds();
    cleared.reset();
    results.put("resetAfterBegin", state(cleared));
    return results;
  }

  static Map<String, String> latch(LatchFactory factory) {
    Map<String, String> results = new LinkedHashMap<>();

    Latch open = factory.create(60_000L);
    results.put("defaultCouldFlipTwice", open.couldFlip() + "," + open.couldFlip());
    results.put("defaultFirstFlip", Boolean.toString(open.flip()));
    results.put("defaultSecondFlip", Boolean.toString(open.flip()));
    results.put("defaultCouldFlipAfterFlip", Boolean.toString(open.couldFlip()));

    Latch explicitOpen = factory.create(60_000L, true);
    results.put("openedFirstFlip", explicitOpen.flip() + "," + explicitOpen.flip());

    Latch closed = factory.create(60_000L, false);
    results.put("closedCouldFlip", Boolean.toString(closed.couldFlip()));
    results.put("closedFirstFlip", Boolean.toString(closed.flip()));

    Latch flippedDown = factory.create(60_000L);
    flippedDown.flipDown();
    results.put("flipDownCloses", flippedDown.couldFlip() + "," + flippedDown.flip());

    Latch shortClosed = factory.create(40L, false);
    long started = System.nanoTime();
    boolean early = shortClosed.flip();
    boolean opened = await(shortClosed::flip, 5_000L);
    long waitedMs = (System.nanoTime() - started) / 1_000_000L;
    results.put("closedFlipBeforeInterval", Boolean.toString(early));
    results.put("closedFlipsAfterInterval", Boolean.toString(opened && waitedMs >= 39L));
    results.put("flipRearmsInterval", Boolean.toString(shortClosed.flip()));
    results.put("couldFlipAfterRearmWait", Boolean.toString(await(shortClosed::couldFlip, 5_000L)));
    results.put("couldFlipDoesNotConsume", shortClosed.couldFlip() + "," + shortClosed.flip() + "," + shortClosed.flip());

    boolean zeroBlocked = false;
    for (int attempt = 0; attempt < 200 && !zeroBlocked; attempt++) {
      Latch zero = factory.create(0L);
      zeroBlocked = !zero.flip();
    }
    results.put("zeroIntervalNeedsElapsedMillisecond", Boolean.toString(zeroBlocked));
    Latch zeroWaited = factory.create(0L);
    sleep(5L);
    results.put("zeroIntervalFlipsAfterMillisecond", Boolean.toString(zeroWaited.flip()));

    Latch negativeClosed = factory.create(-1L, false);
    results.put("negativeClosedAlwaysFlips", negativeClosed.flip() + "," + negativeClosed.flip() + "," + negativeClosed.flip());

    Latch never = factory.create(Long.MAX_VALUE);
    results.put("maxIntervalNeverOpens", never.couldFlip() + "," + never.flip());
    return results;
  }

  private static String state(Watch watch) {
    return "nanos=" + watch.getNanos()
        + " millis=" + watch.getMillis()
        + " startNano=" + watch.getStartNano()
        + " startMillis=" + watch.getStartMillis()
        + " time=" + watch.getTime()
        + " nanoseconds=" + watch.getNanoseconds()
        + " profiling=" + watch.isProfiling();
  }

  private static boolean ordered(double lower, double upper) {
    return lower <= upper + 1.0E-6D;
  }

  private static boolean await(BooleanSupplier condition, long timeoutMs) {
    long deadline = System.nanoTime() + (timeoutMs * 1_000_000L);
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return true;
      }
      sleep(1L);
    }
    return condition.getAsBoolean();
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }
}
