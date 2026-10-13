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
import art.arcane.react.core.telemetry.SlidingCounterWindow;
import com.sun.management.GarbageCollectionNotificationInfo;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.ListenerNotFoundException;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public final class GCStatsTracker {
  private static final Object LOCK = new Object();
  private static final long PAUSE_WINDOW_MS = 300_000L;
  private static final long GC_TIME_WINDOW_MS = 60_000L;
  private static final int MAX_PAUSES = 4096;
  private static final String CONCURRENT_CYCLE_SUFFIX = " Cycles";
  private static final ArrayDeque<Pause> pauses = new ArrayDeque<>(256);
  private static final Map<NotificationEmitter, NotificationListener> listeners = new HashMap<>();
  private static final SlidingCounterWindow collectionTime = new SlidingCounterWindow(GC_TIME_WINDOW_MS);
  private static int references = 0;
  private static boolean collecting = false;
  private static long heapAfterBytes = -1L;
  private static long reclaimedBytes = -1L;
  private static long memoryAtMs;
  private static long pauseAtMs;
  private static double latestPauseMs = Double.NaN;
  private static int pauseSources;
  private static long listenerEpoch;

  private GCStatsTracker() {
  }

  public static void acquire() {
    synchronized (LOCK) {
      references++;
      if (collecting) {
        return;
      }

      collecting = true;
      collectionTime.clear();
      startListeners();
    }
  }

  public static void release() {
    synchronized (LOCK) {
      references = Math.max(0, references - 1);
      if (references > 0 || !collecting) {
        return;
      }

      collecting = false;
      stopListeners();
      pauses.clear();
      collectionTime.clear();
      heapAfterBytes = -1L;
      reclaimedBytes = -1L;
      memoryAtMs = 0L;
      pauseAtMs = 0L;
      latestPauseMs = Double.NaN;
    }
  }

  public static Snapshot snapshot(long nowMs) {
    synchronized (LOCK) {
      evictExpired(nowMs);
      double maxPauseMs = pauseSources == 0 ? Double.NaN : 0D;
      for (Pause pause : pauses) {
        maxPauseMs = Math.max(maxPauseMs, pause.pauseMs());
      }
      return new Snapshot(heapAfterBytes, reclaimedBytes, memoryAtMs, latestPauseMs, pauseAtMs, maxPauseMs);
    }
  }

  static void recordMemory(long timestampMs, Map<String, MemoryUsage> before,
                           Map<String, MemoryUsage> after, Set<String> heapPools) {
    long usedBefore = 0L;
    long usedAfter = 0L;
    if (heapPools.isEmpty()) {
      return;
    }
    for (String name : heapPools) {
      MemoryUsage beforeUsage = before.get(name);
      MemoryUsage afterUsage = after.get(name);
      if (beforeUsage == null || afterUsage == null) {
        return;
      }
      usedBefore += beforeUsage.getUsed();
      usedAfter += afterUsage.getUsed();
    }
    synchronized (LOCK) {
      if (timestampMs >= memoryAtMs) {
        heapAfterBytes = usedAfter;
        reclaimedBytes = Math.max(0L, usedBefore - usedAfter);
        memoryAtMs = timestampMs;
      }
    }
  }

  static boolean hasPauseSamples() {
    synchronized (LOCK) {
      evictExpired(System.currentTimeMillis());
      return pauseSources > 0 && !pauses.isEmpty();
    }
  }

  static List<Double> snapshotPauses() {
    return snapshotPauses(System.currentTimeMillis());
  }

  static List<Double> snapshotPauses(long nowMs) {
    synchronized (LOCK) {
      evictExpired(nowMs);
      List<Double> snapshot = new ArrayList<>(pauses.size());
      for (Pause pause : pauses) {
        snapshot.add(pause.pauseMs());
      }

      return snapshot;
    }
  }

  static double sampleGcTimePercent() {
    return sampleGcTimePercent(ManagementFactory.getRuntimeMXBean().getUptime(), readTotalCollectionTimeMS());
  }

  static double sampleGcTimePercent(long uptimeMs, long totalCollectionMs) {
    synchronized (LOCK) {
      if (collectionTime.isEmpty()) {
        collectionTime.record(0L, 0L);
      }
      collectionTime.record(uptimeMs, totalCollectionMs);
      return SamplerMath.clip(collectionTime.fraction() * 100D, 0, 100);
    }
  }

  public static List<GarbageCollectorMXBean> pauseBeans(List<GarbageCollectorMXBean> beans) {
    List<GarbageCollectorMXBean> pauseBeans = new ArrayList<>(beans.size());
    for (GarbageCollectorMXBean bean : beans) {
      String name = bean.getName();
      if (name == null || !name.endsWith(CONCURRENT_CYCLE_SUFFIX)) {
        pauseBeans.add(bean);
      }
    }

    return pauseBeans;
  }

  static long totalCollectionTimeMS(List<GarbageCollectorMXBean> beans) {
    long total = 0;
    for (GarbageCollectorMXBean bean : pauseBeans(beans)) {
      long time = bean.getCollectionTime();
      if (time > 0) {
        total += time;
      }
    }

    return total;
  }

  static void recordPause(long tsMs, long pauseMS) {
    synchronized (LOCK) {
      pauses.addLast(new Pause(tsMs, Math.max(0L, pauseMS)));
      if (tsMs >= pauseAtMs) {
        latestPauseMs = Math.max(0L, pauseMS);
        pauseAtMs = tsMs;
      }
      while (pauses.size() > MAX_PAUSES) {
        pauses.removeFirst();
      }
      evictExpired(tsMs);
    }
  }

  private static void evictExpired(long nowMs) {
    long cutoff = nowMs - PAUSE_WINDOW_MS;
    pauses.removeIf(pause -> pause.tsMs() < cutoff);
  }

  private static void startListeners() {
    long activeEpoch = ++listenerEpoch;
    Set<String> heapPools = new HashSet<>();
    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
      if (pool.getType() == MemoryType.HEAP) {
        heapPools.add(pool.getName());
      }
    }
    long startedAtMs = ManagementFactory.getRuntimeMXBean().getStartTime();
    for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
      if (!(bean instanceof NotificationEmitter emitter)) {
        continue;
      }

      boolean pauseSource = bean.getName() == null || !bean.getName().endsWith(CONCURRENT_CYCLE_SUFFIX);
      CollectionSource source = new CollectionSource(activeEpoch, startedAtMs, pauseSource, heapPools);
      NotificationListener listener = (notification, handback) -> recordNotification(notification, source);

      try {
        emitter.addNotificationListener(listener, null, null);
        listeners.put(emitter, listener);
        if (pauseSource) {
          pauseSources++;
        }
      } catch (RuntimeException failure) {
        React.reportError("Could not subscribe to GC notifications for " + bean.getName(), failure);
      }
    }
  }

  private static void recordNotification(Notification notification, CollectionSource source) {
    if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())
        || !(notification.getUserData() instanceof CompositeData compositeData)) {
      return;
    }
    GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(compositeData);
    if (info == null || info.getGcInfo() == null) {
      return;
    }
    synchronized (LOCK) {
      if (!collecting || listenerEpoch != source.epoch()) {
        return;
      }
      long timestampMs = source.startedAtMs() + info.getGcInfo().getEndTime();
      if (source.pauseSource()) {
        recordPause(timestampMs, info.getGcInfo().getDuration());
      }
      recordMemory(timestampMs, info.getGcInfo().getMemoryUsageBeforeGc(),
          info.getGcInfo().getMemoryUsageAfterGc(), source.heapPools());
    }
  }

  private static void stopListeners() {
    for (Map.Entry<NotificationEmitter, NotificationListener> entry : listeners.entrySet()) {
      try {
        entry.getKey().removeNotificationListener(entry.getValue());
      } catch (ListenerNotFoundException | RuntimeException failure) {
        React.reportError("Could not remove GC notification listener", failure);
      }
    }

    listeners.clear();
    pauseSources = 0;
    listenerEpoch++;
  }

  private static long readTotalCollectionTimeMS() {
    return totalCollectionTimeMS(ManagementFactory.getGarbageCollectorMXBeans());
  }

  public record Snapshot(long heapAfterBytes, long reclaimedBytes, long memoryAtMs,
                         double latestPauseMs, long pauseAtMs, double maxPauseMs) {
  }

  private record CollectionSource(long epoch, long startedAtMs, boolean pauseSource, Set<String> heapPools) {
  }

  private record Pause(long tsMs, double pauseMs) {
  }
}
