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
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldSaveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SamplerWorldSaveEventInterval extends ReactCachedSampler implements Listener {
  public static final String ID = "world-save-event-interval";

  private transient final Map<UUID, AtomicLong> lastSaveNanosByWorld;
  private transient final AtomicLong lastSaveIntervalMs;

  public SamplerWorldSaveEventInterval() {
    super(ID, 250);
    lastSaveNanosByWorld = new ConcurrentHashMap<>();
    lastSaveIntervalMs = new AtomicLong(0L);
  }

  @Override
  public Material getIcon() {
    return Material.WRITABLE_BOOK;
  }

  @Override
  public void start() {
    lastSaveNanosByWorld.clear();
    lastSaveIntervalMs.set(0L);
    super.start();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(WorldSaveEvent event) {
    if (event.getWorld() == null) {
      return;
    }

    UUID worldId = event.getWorld().getUID();
    long now = System.nanoTime();
    AtomicLong lastSave = lastSaveNanosByWorld.get(worldId);
    if (lastSave != null) {
      long prev = lastSave.getAndSet(now);
      if (prev > 0L) {
        long intervalMs = Math.max(0L, (now - prev) / 1_000_000L);
        lastSaveIntervalMs.set(intervalMs);
      }
    } else {
      lastSaveNanosByWorld.put(worldId, new AtomicLong(now));
    }
  }

  @Override
  public double onSample() {
    return lastSaveIntervalMs.get();
  }

  @Override
  public String formattedValue(double t) {
    return Form.duration(t, 1);
  }

  @Override
  public String formattedSuffix(double t) {
    return " SAVE GAP";
  }
}
