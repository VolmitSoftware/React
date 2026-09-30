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

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

final class CropActivityLedger {
  private static final long MS_PER_TICK = 50L;

  private final Long2LongOpenHashMap lastInsideMs = new Long2LongOpenHashMap();
  private final Long2LongOpenHashMap pendingTicks = new Long2LongOpenHashMap();

  static long pack(int chunkX, int chunkZ) {
    return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
  }

  static int chunkX(long chunk) {
    return (int) (chunk >> 32);
  }

  static int chunkZ(long chunk) {
    return (int) chunk;
  }

  synchronized void seed(long chunk, long now) {
    lastInsideMs.putIfAbsent(chunk, now);
  }

  synchronized void loaded(long chunk, long now) {
    lastInsideMs.put(chunk, now);
    pendingTicks.remove(chunk);
  }

  synchronized void unloaded(long chunk) {
    lastInsideMs.remove(chunk);
    pendingTicks.remove(chunk);
  }

  synchronized void stampInside(
      int centerX,
      int centerZ,
      int radius,
      long now,
      long previousPassMs,
      long warmTicks,
      long maxTicks
  ) {
    for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
      for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
        stamp(pack(chunkX, chunkZ), now, previousPassMs, warmTicks, maxTicks);
      }
    }
  }

  synchronized long takePending(long chunk) {
    return pendingTicks.remove(chunk);
  }

  synchronized long[] pendingChunks(int maximum) {
    int count = Math.min(Math.max(0, maximum), pendingTicks.size());
    long[] chunks = new long[count];
    LongIterator iterator = pendingTicks.keySet().iterator();
    for (int index = 0; index < count && iterator.hasNext(); index++) {
      chunks[index] = iterator.nextLong();
    }
    return chunks;
  }

  synchronized int size() {
    return lastInsideMs.size();
  }

  synchronized int collectStamps(long[] into, int offset) {
    int written = offset;
    ObjectIterator<Long2LongMap.Entry> iterator = lastInsideMs.long2LongEntrySet().fastIterator();
    while (iterator.hasNext() && written < into.length) {
      into[written++] = iterator.next().getLongValue();
    }
    return written;
  }

  synchronized int evictAtOrBefore(long cutoff, int limit) {
    int evicted = 0;
    ObjectIterator<Long2LongMap.Entry> iterator = lastInsideMs.long2LongEntrySet().fastIterator();
    while (iterator.hasNext() && evicted < limit) {
      Long2LongMap.Entry entry = iterator.next();
      if (entry.getLongValue() <= cutoff) {
        pendingTicks.remove(entry.getLongKey());
        iterator.remove();
        evicted++;
      }
    }
    return evicted;
  }

  private void stamp(long chunk, long now, long previousPassMs, long warmTicks, long maxTicks) {
    long previousInside = lastInsideMs.put(chunk, now);
    if (previousInside == 0L || previousInside >= previousPassMs) {
      return;
    }

    long outsideTicks = (previousPassMs - previousInside) / MS_PER_TICK;
    if (outsideTicks < warmTicks) {
      return;
    }

    long accumulated = Math.min(maxTicks, pendingTicks.get(chunk) + outsideTicks);
    pendingTicks.put(chunk, accumulated);
  }
}
