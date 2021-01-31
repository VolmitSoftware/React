package com.volmit.react.api;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public enum Side {
    PLAYERS("players"),
    CONSOLE("the console");

    private final String ss;

    Side(String s) {
        ss = s;
    }

    public static Side get(CommandSender sender) {
        if (sender instanceof Player) {
            return Side.PLAYERS;
        }

        return Side.CONSOLE;
    }

    public String ss() {
        return ss;
    }
}
