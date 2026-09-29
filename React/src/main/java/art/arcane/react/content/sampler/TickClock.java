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

package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.api.event.layer.ServerTickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class TickClock implements Listener {
  public static final double NOMINAL_TICK_MS = 50D;
  public static final double NOMINAL_TICKS_PER_SECOND = 20D;
  public static final int HISTORY_TICKS = 1200;
  static final long EPOCH_NANOS = 250_000_000L;
  static final long TPS_WINDOW_NANOS = 5_000_000_000L;
  private static final long NOMINAL_TICK_NANOS = 50_000_000L;
  private static final long STALL_GRACE_NANOS = EPOCH_NANOS + NOMINAL_TICK_NANOS;
  private static final double NANOS_PER_MS = 1_000_000D;
  private static final double[] EMPTY_DOUBLES = new double[0];
  private static final long[] EMPTY_LONGS = new long[0];
  private static final TickClock INSTANCE = new TickClock(TickClock::readServerTickTimes);

  private final Supplier<long[]> tickTimesSource;
  private final Set<Object> owners = new HashSet<>();
  private final long[] tickAtNanos = new long[HISTORY_TICKS];
  private final double[] gapMS = new double[HISTORY_TICKS];
  private final long[] workAtNanos = new long[HISTORY_TICKS];
  private final double[] workMS = new double[HISTORY_TICKS];
  private int tickHead;
  private int tickCount;
  private int workHead;
  private int workCount;
  private long[] previousTickTimes;
  private boolean workTimeUnsupported;
  private boolean hasEpoch;
  private long lastEpochNanos;
  private boolean registered;
  private volatile boolean hasLastTick;
  private volatile long lastTickNanos;
  private volatile Snapshot snapshot = Snapshot.EMPTY;

  TickClock(Supplier<long[]> tickTimesSource) {
    this.tickTimesSource = Objects.requireNonNull(tickTimesSource);
  }

  public static TickClock get() {
    return INSTANCE;
  }

  public void acquire(Object owner) {
    synchronized (owners) {
      if (!owners.add(owner) || owners.size() != 1) {
        return;
      }

      reset();
      register();
    }
  }

  public void release(Object owner) {
    synchronized (owners) {
      if (!owners.remove(owner) || !owners.isEmpty()) {
        return;
      }

      unregister();
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ServerTickEvent event) {
    tick(System.nanoTime());
  }

  public Snapshot snapshot() {
    return snapshot;
  }

  public long lastTickNanos() {
    return lastTickNanos;
  }

  public double millisSinceLastTick(long nowNanos) {
    if (!hasLastTick) {
      return 0D;
    }

    return Math.max(0D, (nowNanos - lastTickNanos) / NANOS_PER_MS);
  }

  void tick(long nowNanos) {
    if (hasLastTick) {
      recordTick(nowNanos, (nowNanos - lastTickNanos) / NANOS_PER_MS);
    }

    lastTickNanos = nowNanos;
    hasLastTick = true;
    if (hasEpoch && nowNanos - lastEpochNanos < EPOCH_NANOS) {
      return;
    }

    hasEpoch = true;
    lastEpochNanos = nowNanos;
    refreshWorkTimes(nowNanos);
    snapshot = buildSnapshot(nowNanos);
  }

  private void register() {
    React plugin = React.instance;
    if (plugin == null || registered) {
      return;
    }

    plugin.registerListener(this);
    registered = true;
  }

  private void unregister() {
    if (!registered) {
      return;
    }

    registered = false;
    React plugin = React.instance;
    if (plugin != null) {
      plugin.unregisterListener(this);
    }
  }

  private void reset() {
    tickHead = 0;
    tickCount = 0;
    workHead = 0;
    workCount = 0;
    previousTickTimes = null;
    workTimeUnsupported = false;
    hasEpoch = false;
    lastEpochNanos = 0L;
    hasLastTick = false;
    lastTickNanos = 0L;
    snapshot = Snapshot.EMPTY;
  }

  private void recordTick(long atNanos, double gap) {
    int index = (tickHead + tickCount) % HISTORY_TICKS;
    tickAtNanos[index] = atNanos;
    gapMS[index] = gap;
    if (tickCount < HISTORY_TICKS) {
      tickCount++;
    } else {
      tickHead = (tickHead + 1) % HISTORY_TICKS;
    }
  }

  private void recordWork(long atNanos, double work) {
    int index = (workHead + workCount) % HISTORY_TICKS;
    workAtNanos[index] = atNanos;
    workMS[index] = work;
    if (workCount < HISTORY_TICKS) {
      workCount++;
    } else {
      workHead = (workHead + 1) % HISTORY_TICKS;
    }
  }

  private void refreshWorkTimes(long nowNanos) {
    if (workTimeUnsupported) {
      return;
    }

    long[] times = tickTimesSource.get();
    if (times == null) {
      workTimeUnsupported = true;
      previousTickTimes = null;
      return;
    }

    long[] current = times.clone();
    if (previousTickTimes != null && previousTickTimes.length == current.length) {
      for (int i = 0; i < current.length; i++) {
        if (current[i] > 0L && current[i] != previousTickTimes[i]) {
          recordWork(nowNanos, current[i] / NANOS_PER_MS);
        }
      }
    }

    previousTickTimes = current;
  }

  private Snapshot buildSnapshot(long nowNanos) {
    boolean workTimeMode = !workTimeUnsupported;
    long[] ticks = new long[tickCount];
    double[] gaps = new double[tickCount];
    copyRing(tickAtNanos, gapMS, tickHead, tickCount, ticks, gaps);
    long[] workAt = new long[workCount];
    double[] work = new double[workCount];
    copyRing(workAtNanos, workMS, workHead, workCount, workAt, work);
    return new Snapshot(nowNanos, workTimeMode, workTimeMode ? sortedWorkTimes() : EMPTY_DOUBLES, ticks, gaps, workAt, work);
  }

  private double[] sortedWorkTimes() {
    if (previousTickTimes == null) {
      return EMPTY_DOUBLES;
    }

    int valid = 0;
    for (long time : previousTickTimes) {
      if (time > 0L) {
        valid++;
      }
    }

    double[] sorted = new double[valid];
    int index = 0;
    for (long time : previousTickTimes) {
      if (time > 0L) {
        sorted[index++] = time / NANOS_PER_MS;
      }
    }

    Arrays.sort(sorted);
    return sorted;
  }

  private static void copyRing(long[] atRing, double[] valueRing, int head, int count, long[] atOut, double[] valueOut) {
    int first = Math.min(count, HISTORY_TICKS - head);
    System.arraycopy(atRing, head, atOut, 0, first);
    System.arraycopy(valueRing, head, valueOut, 0, first);
    System.arraycopy(atRing, 0, atOut, first, count - first);
    System.arraycopy(valueRing, 0, valueOut, first, count - first);
  }

  private static long[] readServerTickTimes() {
    Server server = Bukkit.getServer();
    if (server == null) {
      return null;
    }

    try {
      return server.getTickTimes();
    } catch (NoSuchMethodError | AbstractMethodError e) {
      return null;
    }
  }

  public static final class Snapshot {
    static final Snapshot EMPTY = new Snapshot(0L, false, EMPTY_DOUBLES, EMPTY_LONGS, EMPTY_DOUBLES, EMPTY_LONGS, EMPTY_DOUBLES);

    private final long builtAtNanos;
    private final boolean workTimeMode;
    private final double[] sortedWorkMS;
    private final long[] tickAtNanos;
    private final double[] gapMS;
    private final long[] workAtNanos;
    private final double[] workMS;

    private Snapshot(
        long builtAtNanos,
        boolean workTimeMode,
        double[] sortedWorkMS,
        long[] tickAtNanos,
        double[] gapMS,
        long[] workAtNanos,
        double[] workMS
    ) {
      this.builtAtNanos = builtAtNanos;
      this.workTimeMode = workTimeMode;
      this.sortedWorkMS = sortedWorkMS;
      this.tickAtNanos = tickAtNanos;
      this.gapMS = gapMS;
      this.workAtNanos = workAtNanos;
      this.workMS = workMS;
    }

    public long builtAtNanos() {
      return builtAtNanos;
    }

    public boolean workTimeMode() {
      return workTimeMode;
    }

    public boolean hasTicks() {
      return tickAtNanos.length > 0;
    }

    public boolean hasHistory() {
      return workTimeMode ? sortedWorkMS.length > 0 : gapMS.length > 0;
    }

    public double percentile(double percentile, int historyTicks) {
      if (workTimeMode) {
        return SamplerMath.percentileSorted(sortedWorkMS, percentile);
      }

      double[] tail = gapTail(historyTicks);
      Arrays.sort(tail);
      return SamplerMath.percentileSorted(tail, percentile);
    }

    public double averageTickMS(int historyTicks) {
      return SamplerMath.mean(workTimeMode ? sortedWorkMS : gapTail(historyTicks));
    }

    public double spikesPerMinute(long nowNanos, double thresholdMS, long windowMS) {
      long safeWindowMS = Math.max(1L, windowMS);
      long windowNanos = safeWindowMS * 1_000_000L;
      int spikes = 0;
      if (workTimeMode) {
        for (int i = workMS.length - 1; i >= 0; i--) {
          if (nowNanos - workAtNanos[i] > windowNanos) {
            break;
          }
          if (workMS[i] > thresholdMS) {
            spikes++;
          }
        }
      } else {
        double gapThresholdMS = thresholdMS + NOMINAL_TICK_MS;
        for (int i = gapMS.length - 1; i >= 0; i--) {
          if (nowNanos - tickAtNanos[i] > windowNanos) {
            break;
          }
          if (gapMS[i] > gapThresholdMS) {
            spikes++;
          }
        }
      }

      return spikes * (60_000D / safeWindowMS);
    }

    public double ticksPerSecond(long nowNanos) {
      if (tickAtNanos.length == 0) {
        return 0D;
      }

      long reference = nowNanos - builtAtNanos <= STALL_GRACE_NANOS ? builtAtNanos : nowNanos;
      long windowStart = reference - TPS_WINDOW_NANOS;
      int count = 0;
      long oldest = reference;
      for (int i = tickAtNanos.length - 1; i >= 0; i--) {
        long at = tickAtNanos[i];
        if (at <= windowStart) {
          break;
        }
        if (at > reference) {
          continue;
        }
        count++;
        oldest = at;
      }

      if (count == 0) {
        return 0D;
      }

      double spanSeconds = ((reference - oldest) + NOMINAL_TICK_NANOS) / 1_000_000_000D;
      return Math.min(NOMINAL_TICKS_PER_SECOND, count / spanSeconds);
    }

    private double[] gapTail(int historyTicks) {
      int size = Math.min(Math.max(1, historyTicks), gapMS.length);
      return Arrays.copyOfRange(gapMS, gapMS.length - size, gapMS.length);
    }
  }
}
