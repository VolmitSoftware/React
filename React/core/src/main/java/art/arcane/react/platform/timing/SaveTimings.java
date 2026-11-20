package art.arcane.react.platform.timing;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Durations of full world saves, autosaves and manual saves alike. {@link #begin}, {@link #worldSaved} and
 * {@link #end} run on the thread that saves; every other method may be called from any thread.
 */
public final class SaveTimings {
  public static final long MAX_WINDOW_MS = 15L * 60_000L;
  private static final long HOUR_MS = 60L * 60_000L;
  private static final int HISTORY = 64;
  private static final int RATE_GAPS = 8;

  private final LongSupplier clockMs;
  private final ArrayDeque<Save> history = new ArrayDeque<>(HISTORY);
  private volatile Save last;
  private boolean saving;
  private Kind kind = Kind.AUTOSAVE;
  private long startedNanos;
  private String slowestWorld = "";
  private long slowestWorldNanos;

  public enum Kind {
    AUTOSAVE,
    MANUAL
  }

  /**
   * One finished save. {@code slowestWorld} is the world whose own save took longest, empty when none was timed.
   */
  public record Save(Kind kind, long endedAtMs, double durationMs, String slowestWorld, double slowestWorldMs) {
  }

  public SaveTimings(LongSupplier clockMs) {
    this.clockMs = clockMs;
  }

  public void begin(Kind saveKind, long nowNanos) {
    saving = true;
    kind = saveKind == null ? Kind.AUTOSAVE : saveKind;
    startedNanos = nowNanos;
    slowestWorld = "";
    slowestWorldNanos = 0L;
  }

  /**
   * One world's part of the save in progress; ignored outside a save.
   */
  public void worldSaved(String world, long elapsedNanos) {
    if (saving && elapsedNanos > slowestWorldNanos) {
      slowestWorldNanos = elapsedNanos;
      slowestWorld = world == null ? "" : world;
    }
  }

  /**
   * Finishes the save in progress, or returns null when none was started.
   */
  public Save end(long nowNanos) {
    if (!saving) {
      return null;
    }
    saving = false;
    Save save = new Save(kind, clockMs.getAsLong(), Math.max(0L, nowNanos - startedNanos) / 1_000_000D,
        slowestWorld, slowestWorldNanos / 1_000_000D);
    synchronized (history) {
      if (history.size() >= HISTORY) {
        history.removeFirst();
      }
      history.addLast(save);
    }
    last = save;
    return save;
  }

  public Optional<Save> last() {
    return Optional.ofNullable(last);
  }

  /**
   * The last save when it finished within {@code windowMs} of now.
   */
  public Optional<Save> endedWithin(long windowMs) {
    Save save = last;
    return save != null && clockMs.getAsLong() - save.endedAtMs() <= windowMs ? Optional.of(save) : Optional.empty();
  }

  public double maxDurationMs() {
    long cutoff = clockMs.getAsLong() - MAX_WINDOW_MS;
    double max = 0D;
    synchronized (history) {
      for (Save save : history) {
        if (save.endedAtMs() >= cutoff) {
          max = Math.max(max, save.durationMs());
        }
      }
    }
    return max;
  }

  /**
   * Saves per hour from the mean gap between the latest saves. A gap longer than that mean since the last save
   * lowers the rate, so it falls toward zero once saving stops.
   */
  public double savesPerHour() {
    long previous = 0L;
    long latest = 0L;
    long gapTotal = 0L;
    int gaps = 0;
    synchronized (history) {
      Iterator<Save> newestFirst = history.descendingIterator();
      while (newestFirst.hasNext() && gaps < RATE_GAPS) {
        long ended = newestFirst.next().endedAtMs();
        if (latest == 0L) {
          latest = ended;
        } else {
          gapTotal += Math.max(0L, previous - ended);
          gaps++;
        }
        previous = ended;
      }
    }
    if (gaps == 0) {
      return 0D;
    }
    double meanGapMs = Math.max(1D, gapTotal / (double) gaps);
    double sinceLastMs = Math.max(0L, clockMs.getAsLong() - latest);
    return HOUR_MS / Math.max(meanGapMs, sinceLastMs);
  }

  public void clear() {
    synchronized (history) {
      history.clear();
    }
    last = null;
    saving = false;
  }
}
