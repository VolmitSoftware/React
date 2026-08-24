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

package art.arcane.react.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.LongAdder;

public class NaughtyRegisteredListener extends RegisteredListener {
  public final String pluginName;
  private final long instrumentationOwner;
  private final LongAdder timeNanos;
  private final LongAdder calls;

  public NaughtyRegisteredListener(@NotNull final Listener listener, @NotNull final EventExecutor executor,
                                   @NotNull final EventPriority priority, @NotNull final Plugin plugin,
                                   final boolean ignoreCancelled, long instrumentationOwner) {
    super(listener, executor, priority, plugin, ignoreCancelled);
    this.pluginName = resolvePluginName(plugin);
    this.instrumentationOwner = instrumentationOwner;
    this.timeNanos = new LongAdder();
    this.calls = new LongAdder();
  }

  private static String resolvePluginName(Plugin plugin) {
    if (plugin == null || plugin.getName() == null) {
      return "Unknown";
    }

    String name = plugin.getName().trim();
    return name.isBlank() ? "Unknown" : name;
  }

  /**
   * Calls the event executor
   *
   * @param event The event
   * @throws EventException If an event handler throws an exception.
   */
  public void callEvent(@NotNull final Event event) throws EventException {
    long start = System.nanoTime();
    try {
      super.callEvent(event);
    } finally {
      record(System.nanoTime() - start);
    }
  }

  public boolean isOwnedBy(long owner) {
    return instrumentationOwner == owner;
  }

  public CounterSnapshot drainCounters() {
    return new CounterSnapshot(timeNanos.sumThenReset(), calls.sumThenReset());
  }

  private void record(long elapsedNanos) {
    timeNanos.add(Math.max(0L, elapsedNanos));
    calls.increment();
  }

  public record CounterSnapshot(long timeNanos, long calls) {
  }
}
