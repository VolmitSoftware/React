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

public class SamplerWorldSaveDuration extends ReactCachedSampler implements Listener {
  public static final String ID = "world-save-duration";

  private transient final Map<UUID, AtomicLong> saveStartNanosByWorld;
  private transient final AtomicLong lastSaveDurationMs;

  public SamplerWorldSaveDuration() {
    super(ID, 250);
    saveStartNanosByWorld = new ConcurrentHashMap<>();
    lastSaveDurationMs = new AtomicLong(0L);
  }

  @Override
  public Material getIcon() {
    return Material.WRITABLE_BOOK;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(WorldSaveEvent event) {
    if (event.getWorld() == null) {
      return;
    }

    UUID worldId = event.getWorld().getUID();
    long now = System.nanoTime();
    AtomicLong start = saveStartNanosByWorld.get(worldId);
    if (start != null) {
      long prev = start.getAndSet(now);
      if (prev > 0L) {
        long durationMs = Math.max(0L, (now - prev) / 1_000_000L);
        lastSaveDurationMs.set(durationMs);
      }
    } else {
      saveStartNanosByWorld.put(worldId, new AtomicLong(now));
    }
  }

  @Override
  public double onSample() {
    return lastSaveDurationMs.get();
  }

  @Override
  public String formattedValue(double t) {
    return Form.duration(t, 1);
  }

  @Override
  public String formattedSuffix(double t) {
    return " SAV";
  }
}
