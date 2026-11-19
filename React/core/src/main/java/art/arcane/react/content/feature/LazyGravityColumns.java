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

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicIntegerArray;

final class LazyGravityColumns {
  private static final int STRIPES = 64;
  private static final int OCCUPANCY_SLOTS = 1 << 14;

  private final Long2ObjectOpenHashMap<UUID>[] stripes;
  private final AtomicIntegerArray occupancy;

  @SuppressWarnings("unchecked")
  LazyGravityColumns() {
    stripes = new Long2ObjectOpenHashMap[STRIPES];
    for (int index = 0; index < STRIPES; index++) {
      stripes[index] = new Long2ObjectOpenHashMap<>();
    }
    occupancy = new AtomicIntegerArray(OCCUPANCY_SLOTS);
  }

  void put(long column, UUID entityId) {
    int hash = hash(column);
    Long2ObjectOpenHashMap<UUID> stripe = stripe(hash);
    synchronized (stripe) {
      if (stripe.put(column, entityId) == null) {
        occupancy.incrementAndGet(slot(hash));
      }
    }
  }

  UUID remove(long column) {
    int hash = hash(column);
    if (occupancy.get(slot(hash)) == 0) {
      return null;
    }

    Long2ObjectOpenHashMap<UUID> stripe = stripe(hash);
    synchronized (stripe) {
      UUID removed = stripe.remove(column);
      if (removed != null) {
        occupancy.decrementAndGet(slot(hash));
      }
      return removed;
    }
  }

  void removeOwned(long column, UUID entityId) {
    int hash = hash(column);
    Long2ObjectOpenHashMap<UUID> stripe = stripe(hash);
    synchronized (stripe) {
      if (entityId.equals(stripe.get(column))) {
        stripe.remove(column);
        occupancy.decrementAndGet(slot(hash));
      }
    }
  }

  void retainTracked(Map<UUID, ?> tracked) {
    for (Long2ObjectOpenHashMap<UUID> stripe : stripes) {
      synchronized (stripe) {
        ObjectIterator<Long2ObjectOpenHashMap.Entry<UUID>> iterator = stripe.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
          Long2ObjectOpenHashMap.Entry<UUID> entry = iterator.next();
          if (!tracked.containsKey(entry.getValue())) {
            occupancy.decrementAndGet(slot(hash(entry.getLongKey())));
            iterator.remove();
          }
        }
      }
    }
  }

  private Long2ObjectOpenHashMap<UUID> stripe(int hash) {
    return stripes[hash & (STRIPES - 1)];
  }

  private static int slot(int hash) {
    return (hash >>> 6) & (OCCUPANCY_SLOTS - 1);
  }

  private static int hash(long column) {
    return (int) HashCommon.mix(column);
  }
}
