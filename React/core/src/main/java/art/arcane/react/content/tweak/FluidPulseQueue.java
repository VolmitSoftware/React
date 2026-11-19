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

package art.arcane.react.content.tweak;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

final class FluidPulseQueue {
  static final int MAX_PENDING_PULSES = 16_384;
  private static final int MAX_TICKS_PER_PULSE = 16;

  private final Map<FluidPulseKey, FluidPulse> pending = new ConcurrentHashMap<>();
  private final Queue<FluidPulseKey> order = new ConcurrentLinkedQueue<>();
  private final AtomicInteger size = new AtomicInteger();

  boolean enqueue(UUID worldId, int x, int y, int z, int ticks) {
    if (ticks <= 0) {
      return false;
    }

    FluidPulseKey key = new FluidPulseKey(worldId, x, y, z);
    if (size.get() >= MAX_PENDING_PULSES && !pending.containsKey(key)) {
      return false;
    }

    pending.compute(key, (ignored, existing) -> {
      if (existing == null) {
        size.incrementAndGet();
        order.offer(key);
        return new FluidPulse(ticks);
      }

      existing.addTicks(ticks);
      return existing;
    });
    return true;
  }

  Map<FluidChunk, List<FluidBurst>> drain(int budget, int maxBurst) {
    Map<FluidChunk, List<FluidBurst>> buckets = new LinkedHashMap<>();
    int remaining = budget;
    int scanLimit = Math.max(budget * 8, 128);
    int scanned = 0;
    while (remaining > 0 && scanned < scanLimit) {
      FluidPulseKey key = order.poll();
      if (key == null) {
        break;
      }

      scanned++;
      FluidPulse pulse = pending.get(key);
      if (pulse == null) {
        continue;
      }

      int burstTicks = pulse.consumeUpTo(Math.min(maxBurst, remaining));
      if (burstTicks <= 0) {
        release(key, pulse);
        continue;
      }

      remaining -= burstTicks;
      buckets.computeIfAbsent(new FluidChunk(key.worldId(), key.x() >> 4, key.z() >> 4), ignored -> new ArrayList<>())
          .add(new FluidBurst(key.x(), key.y(), key.z(), burstTicks));
      if (pulse.hasRemaining()) {
        order.offer(key);
      } else {
        release(key, pulse);
      }
    }
    return buckets;
  }

  void discardWorld(UUID worldId) {
    for (Map.Entry<FluidPulseKey, FluidPulse> entry : pending.entrySet()) {
      if (entry.getKey().worldId().equals(worldId)) {
        release(entry.getKey(), entry.getValue());
      }
    }
  }

  int size() {
    return size.get();
  }

  boolean isEmpty() {
    return size.get() == 0;
  }

  void clear() {
    pending.clear();
    order.clear();
    size.set(0);
  }

  private void release(FluidPulseKey key, FluidPulse pulse) {
    if (pending.remove(key, pulse)) {
      size.decrementAndGet();
    }
  }

  record FluidChunk(UUID worldId, int x, int z) {
  }

  record FluidBurst(int x, int y, int z, int ticks) {
  }

  private record FluidPulseKey(UUID worldId, int x, int y, int z) {
  }

  private static final class FluidPulse {
    private final AtomicInteger remainingTicks;

    private FluidPulse(int ticks) {
      this.remainingTicks = new AtomicInteger(Math.min(MAX_TICKS_PER_PULSE, Math.max(0, ticks)));
    }

    private void addTicks(int ticks) {
      int safeTicks = Math.max(0, ticks);
      if (safeTicks == 0) {
        return;
      }

      remainingTicks.updateAndGet(value -> Math.max(0, Math.min(MAX_TICKS_PER_PULSE, value + safeTicks)));
    }

    private int consumeUpTo(int maxTicks) {
      if (maxTicks <= 0) {
        return 0;
      }

      while (true) {
        int current = remainingTicks.get();
        if (current <= 0) {
          return 0;
        }

        int consume = Math.min(current, maxTicks);
        if (remainingTicks.compareAndSet(current, current - consume)) {
          return consume;
        }
      }
    }

    private boolean hasRemaining() {
      return remainingTicks.get() > 0;
    }
  }
}
