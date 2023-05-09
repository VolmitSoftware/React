package com.volmit.react.api.event.layer;

import com.volmit.react.api.event.ReactCancellableEvent;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Getter
public class MinecartSpawnEvent extends ReactCancellableEvent {
    private final Location at;
    private final Player player;

    public MinecartSpawnEvent(Location at, Player player) {
        this.player = player;
        this.at = at;
    }

    public MinecartSpawnEvent(Location at) {
        this(at, null);
    }
}
