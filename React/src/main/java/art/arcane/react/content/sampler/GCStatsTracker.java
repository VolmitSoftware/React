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

import art.arcane.react.core.telemetry.SlidingCounterWindow;
import com.sun.management.GarbageCollectionNotificationInfo;

import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GCStatsTracker {
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

  private GCStatsTracker() {
  }

  static void acquire() {
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

  static void release() {
    synchronized (LOCK) {
      references = Math.max(0, references - 1);
      if (references > 0 || !collecting) {
        return;
      }

      collecting = false;
      stopListeners();
      pauses.clear();
      collectionTime.clear();
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

  static List<GarbageCollectorMXBean> pauseBeans(List<GarbageCollectorMXBean> beans) {
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
      while (pauses.size() > MAX_PAUSES) {
        pauses.removeFirst();
      }
      evictExpired(tsMs);
    }
  }

  private static void evictExpired(long nowMs) {
    long cutoff = nowMs - PAUSE_WINDOW_MS;
    while (!pauses.isEmpty() && pauses.peekFirst().tsMs() < cutoff) {
      pauses.removeFirst();
    }
  }

  private static void startListeners() {
    for (GarbageCollectorMXBean bean : pauseBeans(ManagementFactory.getGarbageCollectorMXBeans())) {
      if (!(bean instanceof NotificationEmitter emitter)) {
        continue;
      }

      NotificationListener listener = (notification, handback) -> {
        if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
          return;
        }

        Object data = notification.getUserData();
        if (!(data instanceof CompositeData compositeData)) {
          return;
        }

        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(compositeData);
        if (info == null || info.getGcInfo() == null) {
          return;
        }

        recordPause(System.currentTimeMillis(), info.getGcInfo().getDuration());
      };

      try {
        emitter.addNotificationListener(listener, null, null);
        listeners.put(emitter, listener);
      } catch (Throwable ignored) {
      }
    }
  }

  private static void stopListeners() {
    for (Map.Entry<NotificationEmitter, NotificationListener> entry : listeners.entrySet()) {
      try {
        entry.getKey().removeNotificationListener(entry.getValue());
      } catch (Throwable ignored) {
      }
    }

    listeners.clear();
  }

  private static long readTotalCollectionTimeMS() {
    return totalCollectionTimeMS(ManagementFactory.getGarbageCollectorMXBeans());
  }

  private record Pause(long tsMs, double pauseMs) {
  }
}
