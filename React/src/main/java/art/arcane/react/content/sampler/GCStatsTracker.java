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

import com.sun.management.GarbageCollectionNotificationInfo;

import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;

final class GCStatsTracker {
  private static final Object LOCK = new Object();
  private static final int MAX_PAUSES = 512;
  private static final ArrayDeque<Double> pauses = new ArrayDeque<>(MAX_PAUSES + 8);
  private static final Map<NotificationEmitter, NotificationListener> listeners = new HashMap<>();
  private static int references = 0;
  private static long lastCollectionTotalMS = 0;
  private static long lastCollectionSampleMS = 0;
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
      lastCollectionTotalMS = readTotalCollectionTimeMS();
      lastCollectionSampleMS = System.currentTimeMillis();
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
      lastCollectionTotalMS = 0;
      lastCollectionSampleMS = 0;
    }
  }

  static List<Double> snapshotPauses() {
    synchronized (LOCK) {
      return new ArrayList<>(pauses);
    }
  }

  static double sampleGcTimePercent() {
    synchronized (LOCK) {
      long now = System.currentTimeMillis();
      long total = readTotalCollectionTimeMS();

      if (lastCollectionSampleMS == 0) {
        lastCollectionSampleMS = now;
        lastCollectionTotalMS = total;
        return 0;
      }

      long elapsedMS = Math.max(1, now - lastCollectionSampleMS);
      long deltaCollectionMS = Math.max(0, total - lastCollectionTotalMS);
      lastCollectionSampleMS = now;
      lastCollectionTotalMS = total;
      return SamplerMath.clip((deltaCollectionMS * 100D) / elapsedMS, 0, 100);
    }
  }

  private static void startListeners() {
    for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
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

        recordPause(info.getGcInfo().getDuration());
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

  private static void recordPause(long pauseMS) {
    synchronized (LOCK) {
      pauses.addLast((double) Math.max(0L, pauseMS));
      while (pauses.size() > MAX_PAUSES) {
        pauses.removeFirst();
      }
    }
  }

  private static long readTotalCollectionTimeMS() {
    long total = 0;
    for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
      long time = bean.getCollectionTime();
      if (time > 0) {
        total += time;
      }
    }

    return total;
  }
}
