package com.volmit.react.api.rendering;

import com.volmit.react.api.rendering.ReactRenderer;
import com.volmit.react.util.function.Consumer3;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

@Getter
@AllArgsConstructor
public class MapRendererPipe extends MapRenderer {
    private final ReactRenderer renderer;

    @Override
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
        try {
            ReactRenderContext.push(ReactRenderContext.builder()
                .player(player)
                .view(map)
                .canvas(canvas)
                .width(128)
                .height(128)
                .build());
            renderer.render(map, canvas, player);
        }

        catch(Throwable e) {
            e.printStackTrace();
        }
    }
}
