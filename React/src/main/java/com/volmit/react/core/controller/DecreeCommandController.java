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

import com.volmit.react.React;
import com.volmit.react.content.decreecommand.CommandReact;
import com.volmit.react.util.cache.AtomicCache;
import com.volmit.react.util.collection.KMap;
import com.volmit.react.util.decree.DecreeSystem;
import com.volmit.react.util.decree.virtual.VirtualDecreeCommand;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.scheduling.J;
import lombok.Data;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Data
public class DecreeCommandController implements IController, DecreeSystem, Listener {
    private transient final KMap<String, CompletableFuture<String>> futures = new KMap<>();
    private transient final AtomicCache<VirtualDecreeCommand> commandCache = new AtomicCache<>();
    private transient CompletableFuture<String> consoleFuture = null;

    @Override
    public String getName() {
        return "Command";
    }

    @Override
    public String getId() {
        return "command";
    }

    @Override
    public void start() {
        React.instance.getCommand("react").setExecutor(this);
        J.a(() -> getRoot().cacheAll());
    }

    @Override
    public void stop() {

    }

    @Override
    public void postStart() {

    }

    @EventHandler
    public void on(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().startsWith("/") ? e.getMessage().substring(1) : e.getMessage();

        if (msg.startsWith("reactdecree ")) {
            String[] args = msg.split("\\Q \\E");
            CompletableFuture<String> future = futures.get(args[1]);

            if (future != null) {
                future.complete(args[2]);
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void on(ServerCommandEvent e) {
        if (consoleFuture != null && !consoleFuture.isCancelled() && !consoleFuture.isDone()) {
            if (!e.getCommand().contains(" ")) {
                String pick = e.getCommand().trim().toLowerCase(Locale.ROOT);
                consoleFuture.complete(pick);
                e.setCancelled(true);
            }
        }
    }

    @Override
    public VirtualDecreeCommand getRoot() {
        return commandCache.aquireNastyPrint(() -> VirtualDecreeCommand.createRoot(new CommandReact()));
    }

    public void post(String password, CompletableFuture<String> future) {
        futures.put(password, future);
    }

    public void postConsole(CompletableFuture<String> future) {
        consoleFuture = future;
    }
}
