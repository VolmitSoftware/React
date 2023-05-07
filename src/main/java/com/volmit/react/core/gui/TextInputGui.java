package com.volmit.react.core.gui;

import com.volmit.react.React;
import com.volmit.react.legacyutil.J;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TextInputGui implements Listener {
    private final Player player;
    private String response;
    private boolean responded;

    public TextInputGui(Player player) {
        this.player = player;
        Bukkit.getPluginManager().registerEvents(this, React.instance);
        responded = false;
        response = null;
    }

    public void on(PlayerQuitEvent e) {
        if(e.getPlayer().equals(player)) {
            responded = true;
            response = null;
            HandlerList.unregisterAll(this);
        }
    }

    @EventHandler(ignoreCancelled = false, priority = org.bukkit.event.EventPriority.HIGHEST)
    public void on(AsyncPlayerChatEvent e) {
        if(e.getPlayer().equals(player)) {
            e.setCancelled(true);
            response = e.getMessage();
            responded = true;
            HandlerList.unregisterAll(this);
        }
    }

    public static String captureText(Player p) {
        if(Bukkit.isPrimaryThread()) {
            throw new RuntimeException("Cannot open gui on main thread");
        }

        TextInputGui gui = new TextInputGui(p);

        while(!gui.responded) {
            J.sleep(50);
        }

        return gui.response;
    }
}
