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

import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.RollingSequence;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

abstract class SamplerChunkEventDurationBase extends ReactCachedSampler implements Listener {
  private transient volatile RollingSequence average;
  private final transient ConcurrentHashMap<Integer, Long> starts;
  private final transient ConcurrentHashMap<Integer, Long> startCreated;
  private int maxHistory = 48;
  private int staleStartMS = 10000;

  protected SamplerChunkEventDurationBase(String id) {
    super(id, 1000);
    this.average = createAverage();
    this.starts = new ConcurrentHashMap<>();
    this.startCreated = new ConcurrentHashMap<>();
  }

  @Override
  public void start() {
    average = createAverage();
    starts.clear();
    startCreated.clear();
    super.start();
  }

  @Override
  public Material getIcon() {
    return Material.COMPASS;
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onStart(ChunkLoadEvent event) {
    if (!include(event)) {
      return;
    }

    int key = System.identityHashCode(event);
    long now = System.currentTimeMillis();
    starts.put(key, System.nanoTime());
    startCreated.put(key, now);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onEnd(ChunkLoadEvent event) {
    if (!include(event)) {
      return;
    }

    int key = System.identityHashCode(event);
    Long started = starts.remove(key);
    startCreated.remove(key);

    if (started == null) {
      return;
    }

    double durationMS = Math.max(0D, (System.nanoTime() - started) / 1_000_000D);
    getChunkCounter(event.getChunk()).addAndGet(durationMS);
    RollingSequence currentAverage = average;
    synchronized (currentAverage) {
      currentAverage.put(durationMS);
    }
  }

  @Override
  public double onSample() {
    cleanupStarts(System.currentTimeMillis());
    RollingSequence currentAverage = average;
    synchronized (currentAverage) {
      return currentAverage.getAverage();
    }
  }

  private RollingSequence createAverage() {
    return new RollingSequence(Math.max(1, maxHistory));
  }

  private void cleanupStarts(long now) {
    int effectiveStaleStartMS = Math.max(0, staleStartMS);
    Iterator<Map.Entry<Integer, Long>> iterator = startCreated.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<Integer, Long> entry = iterator.next();
      if (now - entry.getValue() > effectiveStaleStartMS) {
        starts.remove(entry.getKey());
        iterator.remove();
      }
    }
  }

  protected abstract boolean include(ChunkLoadEvent event);

  @Override
  public String formattedValue(double t) {
    return Form.f(t, 2);
  }
}
