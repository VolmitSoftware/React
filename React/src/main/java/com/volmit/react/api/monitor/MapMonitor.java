package com.volmit.react.api.monitor;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

public class MapMonitor extends MapRenderer {
    private MapView view;

    public MapMonitor(MapView view) {
        view.addRenderer(this);
    }

    @Override
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {

    }
}
