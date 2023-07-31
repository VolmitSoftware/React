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

package com.volmit.react.api.event;

import art.arcane.chrono.PrecisionStopwatch;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.jetbrains.annotations.NotNull;

public class NaughtyRegisteredListener extends RegisteredListener {
    public double timeHighest;
    public double time;
    public int calls;

    public NaughtyRegisteredListener(@NotNull final Listener listener, @NotNull final EventExecutor executor,
                                     @NotNull final EventPriority priority, @NotNull final Plugin plugin, final boolean ignoreCancelled) {
        super(listener, executor, priority, plugin, ignoreCancelled);
    }

    /**
     * Calls the event executor
     *
     * @param event The event
     * @throws EventException If an event handler throws an exception.
     */
    public void callEvent(@NotNull final Event event) throws EventException {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        super.callEvent(event);
        p.end();
        time = p.getMilliseconds();
        timeHighest = Math.max(timeHighest, time);
        calls++;
    }
}
