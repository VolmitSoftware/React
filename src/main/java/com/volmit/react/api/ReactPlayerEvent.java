package com.volmit.react.api;

import org.bukkit.entity.Player;

public class ReactPlayerEvent extends ReactEvent {
    private final Player player;

    public ReactPlayerEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }
}
