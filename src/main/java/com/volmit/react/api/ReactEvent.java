package com.volmit.react.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ReactEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
