package com.volmit.react.api.event;

import com.volmit.react.React;
import com.volmit.react.util.PrecisionStopwatch;
import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
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
