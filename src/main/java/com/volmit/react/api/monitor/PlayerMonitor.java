package com.volmit.react.api.monitor;

import lombok.Data;
import lombok.Getter;
import org.bukkit.entity.Player;

public abstract class PlayerMonitor extends TickedMonitor {
    @Getter
    protected final Player player;

    public PlayerMonitor(String type, Player player, long interval) {
        super(player.getUniqueId() + ":" + type, interval);
        this.player = player;
    }

    @Override
    public abstract void flush();
}
