package com.volmit.react.core.controller;

import art.arcane.curse.Curse;
import com.volmit.react.React;
import com.volmit.react.api.event.NaughtyRegisteredListener;
import com.volmit.react.content.command.CommandReact;
import com.volmit.react.util.cache.AtomicCache;
import com.volmit.react.util.collection.KMap;
import com.volmit.react.util.decree.DecreeSystem;
import com.volmit.react.util.decree.virtual.VirtualDecreeCommand;
import com.volmit.react.util.format.C;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.plugin.VolmitSender;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.RegisteredListener;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
public class CommandController implements IController, DecreeSystem, Listener {
    public CommandController() {
        start();
    }

    @Override
    public String getName() {
        return "Command";
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

    private final KMap<String, CompletableFuture<String>> futures = new KMap<>();
    private final transient AtomicCache<VirtualDecreeCommand> commandCache = new AtomicCache<>();
    private CompletableFuture<String> consoleFuture = null;


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
