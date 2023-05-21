package com.volmit.react.model;

import lombok.Getter;
import org.bukkit.ChatColor;

@Getter
public enum VisualizerType {
    REDSTONE(ChatColor.RED),
    FLUID(ChatColor.AQUA),
    HOPPER(ChatColor.GRAY),
    PHYSICS(ChatColor.YELLOW)

    ;

    private final ChatColor color;

    private VisualizerType(ChatColor c) {
        this.color = c;
    }
}
