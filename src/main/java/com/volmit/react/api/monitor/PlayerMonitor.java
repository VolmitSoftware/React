package com.volmit.react.api.monitor;

import com.volmit.react.api.player.ReactPlayer;
import lombok.Data;
import lombok.Getter;
import org.bukkit.entity.Player;

public abstract class PlayerMonitor extends TickedMonitor {
    @Getter
    protected final ReactPlayer player;

    public PlayerMonitor(String type, ReactPlayer player, long interval) {
        super(player.getPlayer().getUniqueId() + ":" + type, interval);
        this.player = player;
    }

    @Override
    public abstract void flush();
}
