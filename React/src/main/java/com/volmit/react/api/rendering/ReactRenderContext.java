package com.volmit.react.api.rendering;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapView;

import java.util.HashMap;
import java.util.Map;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ReactRenderContext {
    private static Map<Long, ReactRenderContext> contexts = new HashMap<>();
    private Player player;
    private MapView view;
    private MapCanvas canvas;
    private int width;
    private int height;

    public static void push(ReactRenderContext context) {
        contexts.put(Thread.currentThread().getId(), context);
    }

    public static ReactRenderContext of() {
        return contexts.get(Thread.currentThread().getId());
    }
}
