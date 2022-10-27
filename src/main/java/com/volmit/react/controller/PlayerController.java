package com.volmit.react.controller;

import com.volmit.react.api.monitor.ActionBarMonitor;
import com.volmit.react.api.monitor.Monitor;
import com.volmit.react.api.player.ReactPlayer;
import com.volmit.react.sampler.SamplerChunksLoaded;
import com.volmit.react.sampler.SamplerMemoryUsedAfterGC;
import com.volmit.react.sampler.SamplerTicksPerSecond;
import com.volmit.react.util.IController;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
public class PlayerController implements IController {
    private final Map<Player, ReactPlayer> players = new HashMap<>();

    @Override
    public String getName() {
        return "Player";
    }

    @Override
    public void start() {
        for(Player i : Bukkit.getOnlinePlayers()) {
            join(i);
        }
    }

    public ReactPlayer get(Player player) {
        return players.get(player);
    }

    @Override
    public void stop() {
        for(Player i : getPlayers().k()) {
            quit(i);
        }
    }

    @EventHandler
    public void on(PlayerJoinEvent e) {
        join(e.getPlayer());
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        quit(e.getPlayer());
    }

    public void join(Player e) {
        ReactPlayer p = new ReactPlayer(e);
        players.put(e, p);
        p.onJoin();
    }

    public void quit(Player e) {
        ReactPlayer p = players.remove(e);
        p.onQuit();
        p.unregister();
    }

    @Override
    public void tick() {

    }

    @Override
    public int getTickInterval() {
        return 1000;
    }

    @Override
    public void l(Object l) {

    }

    @Override
    public void v(Object l) {

    }
}
