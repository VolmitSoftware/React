package com.volmit.react.api.rendering;

import com.volmit.react.util.data.TinyColor;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;

import java.awt.*;

public interface ReactRenderer {
    default void render(MapView map, MapCanvas canvas, Player player) {
        ReactRenderContext.push(ReactRenderContext.builder()
                .view(map).canvas(canvas).player(player)
                .width(128).height(128)
                .build());
        render();
    }

    String getId();

    void render();

    default int textHeight() {
        return MinecraftFont.Font.getHeight();
    }

    default int textWidth(String w) {
        return MinecraftFont.Font.getWidth(w);
    }

    default void text(int x, int y, String text) {
        canvas().drawText(x, y, MinecraftFont.Font, text);
    }

    default void line(int x1, int y1, int x2, int y2, TinyColor color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int steps = Math.max(dx, dy);
        int x = x1;
        int y = y1;

        for (int i = 0; i <= steps; i++) {
            if (x < 0 || y < 0 || x >= width() || y >= height()) {
                break;
            }

            if (i > 256) {
                break;
            }

            set(x, y, color);
            int e2 = 2 * err;

            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }

            if (e2 < dx) {
                err += dx;
                y += sy;
            }

            if (x == x2 && y == y2) {
                break;
            }
        }
    }

    default void set(int x, int y, int rgb) {
        set(x, y, new Color(rgb));
    }

    default void set(int x, int y, Color color) {
        canvas().setPixelColor(x, y, color);
    }

    default int x(int x) {
        return Math.max(4, Math.min(width() - 1, x - 4));
    }

    default int y(int y) {
        return Math.max(4, Math.min(height() - 1, y - 4));
    }

    default void textNear(int x, int y, String text) {
        text(x(x - textWidth(text)), y(y - textHeight()), text);
    }

    default void set(int x, int y, TinyColor color) {
        set(x, y, color.getColor());
    }

    default void set(int x, int y, org.bukkit.Color color) {
        set(x, y, color.asRGB());
    }

    default MapCanvas canvas() {
        return ReactRenderContext.of().getCanvas();
    }

    default MapView view() {
        return ReactRenderContext.of().getView();
    }

    default Player player() {
        return ReactRenderContext.of().getPlayer();
    }

    default int width() {
        return ReactRenderContext.of().getWidth();
    }

    default int height() {
        return ReactRenderContext.of().getHeight();
    }

    default void set(int x, int y, int w, int h, TinyColor c) {
        for (int i = x; i < x + w; i++) {
            for (int j = y; j < y + h; j++) {
                set(i, j, c);
            }
        }
    }

    default void clear(TinyColor color) {
        for (int i = 0; i < width(); i++) {
            for (int j = 0; j < height(); j++) {
                set(i, j, color);
            }
        }
    }
}
