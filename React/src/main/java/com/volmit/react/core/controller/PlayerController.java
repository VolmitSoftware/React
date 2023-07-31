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

package com.volmit.react.core.controller;

import com.volmit.react.model.ReactPlayer;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class PlayerController extends TickedObject implements IController {
    private transient final Map<Player, ReactPlayer> players = new HashMap<>();

    public PlayerController() {
        super("react", "player", 30000);
    }

    @Override
    public void onTick() {

    }

    @Override
    public String getName() {
        return "Player";
    }

    public ReactPlayer getPlayer(Player player) {
        return getPlayers().get(player);
    }

    @Override
    public void start() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            join(i);
        }
    }

    public ReactPlayer get(Player player) {
        return players.get(player);
    }

    @Override
    public void stop() {
        for (Player i : new ArrayList<>(getPlayers().keySet())) {
            quit(i);
        }
    }

    @Override
    public void postStart() {

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
        if (!e.hasPermission("react.use")) {
            return;
        }

        ReactPlayer p = new ReactPlayer(e);
        players.put(e, p);
        p.onJoin();
    }

    public void quit(Player e) {
        if (!e.hasPermission("react.use")) {
            return;
        }

        ReactPlayer p = players.remove(e);

        if (p != null) {
            p.onQuit();
            p.unregister();
        }
    }

    public void updateMonitors() {
        for (ReactPlayer i : getPlayers().values()) {
            i.updateMonitors();
        }
    }
}
