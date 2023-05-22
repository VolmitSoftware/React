package com.volmit.react.core.controller;

import com.volmit.react.React;
import com.volmit.react.content.command.CommandReact;
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
public class CommandController implements IController, DecreeSystem, Listener {
    private transient final KMap<String, CompletableFuture<String>> futures = new KMap<>();
    private transient final AtomicCache<VirtualDecreeCommand> commandCache = new AtomicCache<>();
    private transient CompletableFuture<String> consoleFuture = null;

    public CommandController() {
        start();
    }

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
        React.instance.registerListener(this);
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
