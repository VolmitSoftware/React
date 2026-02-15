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

package art.arcane.react.api.sampler;

import com.google.common.util.concurrent.AtomicDouble;
import art.arcane.react.React;
import art.arcane.react.api.rendering.Graph;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.math.M;
import art.arcane.react.util.registry.Registered;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

public interface Sampler extends Registered, ReactRenderer {
    double sample();

    default double sample(Chunk c) {
        return React.controller(ObserverController.class).sample(c, this).orElse(0D);
    }

    default void render() {
        String normalizedId = normalizeSamplerId(getId());
        TinyColor[] palette = paletteFor(normalizedId);
        TinyColor header = palette[0];
        TinyColor fillLow = palette[1];
        TinyColor fillHigh = palette[2];
        TinyColor line = palette[3];
        TinyColor marker = palette[4];
        TinyColor backgroundTop = palette[5];
        TinyColor backgroundBottom = palette[6];

        for (int y = 0; y < height(); y++) {
            double n = y / (double) Math.max(1, height() - 1);
            set(0, y, 128, 1, gradient(n, backgroundTop, backgroundBottom));
        }
        set(0, 0, 128, 10, header);
        text(3, 2, getName());

        Graph g = Graph.of(this);
        double min = g.getMin();
        double max = g.getMax();
        double pmax = g.getPaddedMax(0.15);
        double pmin = g.getPaddedMin(0.15);
        double range = pmax - pmin;
        if (!Double.isFinite(range) || Math.abs(range) < 1.0E-9D) {
            range = 1D;
            pmax = max + 0.5D;
            pmin = min - 0.5D;
        }

        for (int y = 16; y <= 112; y += 16) {
            for (int x = 0; x < 128; x++) {
                set(x, y, new TinyColor(18, 26, 34));
            }
        }

        int prevX = -1;
        int prevY = -1;
        for (int x = 0; x < 128; x++) {
            double normalized = (g.get(x) - pmin) / range;
            if (!Double.isFinite(normalized)) {
                normalized = 0.5D;
            }
            normalized = M.clip(normalized, 0D, 1D);
            int y = (int) M.lerp(116, 14, normalized);
            y = Math.max(14, Math.min(116, y));

            for (int fill = 116; fill >= y; fill--) {
                double depth = (116D - fill) / 102D;
                set(x, fill, gradient(depth, fillLow, fillHigh));
            }

            if (prevX >= 0) {
                line(prevX, prevY, x, y, line);
            }
            prevX = x;
            prevY = y;
        }

        if (prevX >= 0 && prevY >= 0) {
            set(prevX, prevY, marker);
            set(Math.max(0, prevX - 1), prevY, marker);
            set(prevX, Math.max(0, prevY - 1), marker);
        }

        String s = format(g.get(0));
        text(3, 116, "Now: " + s);
        text(3, 106, "Min: " + format(min));
        text(3, 96, "Max: " + format(max));
    }

    private TinyColor gradient(double normalized, TinyColor low, TinyColor high) {
        double n = M.clip(normalized, 0D, 1D);
        int r = (int) Math.round((low.getColor().getRed() * (1D - n)) + (high.getColor().getRed() * n));
        int g = (int) Math.round((low.getColor().getGreen() * (1D - n)) + (high.getColor().getGreen() * n));
        int b = (int) Math.round((low.getColor().getBlue() * (1D - n)) + (high.getColor().getBlue() * n));
        return new TinyColor(r, g, b);
    }

    private String normalizeSamplerId(String id) {
        return id == null ? "" : id.toLowerCase();
    }

    private TinyColor[] paletteFor(String normalizedId) {
        if (containsAny(normalizedId, "redstone")) {
            return palette(
                    new TinyColor(156, 44, 36),
                    new TinyColor(96, 18, 14),
                    new TinyColor(226, 74, 56),
                    new TinyColor(255, 188, 72),
                    new TinyColor(255, 230, 120),
                    new TinyColor(18, 10, 10),
                    new TinyColor(26, 14, 14)
            );
        }
        if (containsAny(normalizedId, "hopper", "fluid", "physics")) {
            return palette(
                    new TinyColor(44, 128, 140),
                    new TinyColor(18, 62, 78),
                    new TinyColor(72, 198, 214),
                    new TinyColor(180, 236, 250),
                    new TinyColor(226, 252, 255),
                    new TinyColor(10, 16, 20),
                    new TinyColor(12, 24, 28)
            );
        }
        if (containsAny(normalizedId, "event", "listener", "plugin")) {
            return palette(
                    new TinyColor(162, 92, 46),
                    new TinyColor(94, 48, 22),
                    new TinyColor(228, 128, 62),
                    new TinyColor(255, 206, 108),
                    new TinyColor(255, 236, 170),
                    new TinyColor(18, 14, 10),
                    new TinyColor(28, 20, 12)
            );
        }
        if (containsAny(normalizedId, "memory", "gc")) {
            return palette(
                    new TinyColor(116, 78, 168),
                    new TinyColor(58, 40, 98),
                    new TinyColor(166, 122, 224),
                    new TinyColor(228, 192, 255),
                    new TinyColor(248, 230, 255),
                    new TinyColor(12, 10, 18),
                    new TinyColor(20, 16, 30)
            );
        }
        if (containsAny(normalizedId, "tick", "incident", "spike", "mspt", "tps")) {
            return palette(
                    new TinyColor(166, 94, 42),
                    new TinyColor(96, 52, 20),
                    new TinyColor(236, 132, 62),
                    new TinyColor(255, 196, 94),
                    new TinyColor(255, 228, 138),
                    new TinyColor(18, 14, 12),
                    new TinyColor(26, 20, 16)
            );
        }
        if (containsAny(normalizedId, "entity", "spawn", "player", "ping")) {
            return palette(
                    new TinyColor(132, 108, 42),
                    new TinyColor(76, 62, 20),
                    new TinyColor(198, 170, 72),
                    new TinyColor(236, 214, 130),
                    new TinyColor(250, 238, 184),
                    new TinyColor(16, 14, 10),
                    new TinyColor(24, 22, 14)
            );
        }
        if (containsAny(normalizedId, "chunk", "world")) {
            return palette(
                    new TinyColor(68, 126, 176),
                    new TinyColor(24, 62, 100),
                    new TinyColor(102, 176, 236),
                    new TinyColor(180, 220, 255),
                    new TinyColor(232, 248, 255),
                    new TinyColor(10, 14, 20),
                    new TinyColor(16, 22, 30)
            );
        }
        if (containsAny(normalizedId, "iris")) {
            return palette(
                    new TinyColor(64, 138, 98),
                    new TinyColor(30, 74, 56),
                    new TinyColor(94, 186, 136),
                    new TinyColor(178, 236, 200),
                    new TinyColor(224, 252, 238),
                    new TinyColor(10, 16, 12),
                    new TinyColor(14, 24, 18)
            );
        }
        if (containsAny(normalizedId, "adapt")) {
            return palette(
                    new TinyColor(152, 78, 132),
                    new TinyColor(82, 42, 74),
                    new TinyColor(212, 112, 188),
                    new TinyColor(244, 180, 232),
                    new TinyColor(252, 220, 246),
                    new TinyColor(18, 10, 18),
                    new TinyColor(26, 14, 26)
            );
        }
        if (containsAny(normalizedId, "processor", "cpu", "load", "react", "job", "queue", "backlog")) {
            return palette(
                    new TinyColor(46, 126, 154),
                    new TinyColor(18, 64, 86),
                    new TinyColor(82, 182, 214),
                    new TinyColor(168, 226, 246),
                    new TinyColor(220, 248, 255),
                    new TinyColor(10, 14, 18),
                    new TinyColor(16, 20, 24)
            );
        }

        return palette(
                new TinyColor(34, 98, 156),
                new TinyColor(18, 72, 122),
                new TinyColor(54, 174, 210),
                new TinyColor(245, 192, 80),
                new TinyColor(255, 110, 58),
                new TinyColor(8, 12, 18),
                new TinyColor(14, 20, 28)
        );
    }

    private TinyColor[] palette(
            TinyColor header,
            TinyColor fillLow,
            TinyColor fillHigh,
            TinyColor line,
            TinyColor marker,
            TinyColor backgroundTop,
            TinyColor backgroundBottom
    ) {
        return new TinyColor[]{header, fillLow, fillHigh, line, marker, backgroundTop, backgroundBottom};
    }

    private boolean containsAny(String value, String... words) {
        if (value == null || words == null) {
            return false;
        }

        for (String word : words) {
            if (word != null && !word.isBlank() && value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    default String format(double t) {
        return formattedValue(t) + " " + formattedSuffix(t);
    }

    default AtomicDouble getChunkCounter(Chunk c) {
        return React.controller(ObserverController.class).get(c, this);
    }

    default AtomicDouble getChunkCounter(Block b) {
        return React.controller(ObserverController.class).get(b, this);
    }

    @Override
    default String getConfigCategory() {
        return "sampler";
    }

    default Component format(Component value, Component suffix) {
        return Component.empty().append(value).append(suffix);
    }

    String formattedValue(double t);

    String formattedSuffix(double t);

    void start();

    void stop();

    default <T> T executeSync(Supplier<T> executor) {
        if (Bukkit.isPrimaryThread()) {
            return executor.get();
        }

        Future<T> future = Bukkit.getScheduler().callSyncMethod(React.instance, executor::get);
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new RuntimeException(cause == null ? e : cause);
        }
    }

    default Sampler getSampler(String id) {
        return React.controller(SampleController.class).getSamplers().get(id);
    }

    default String sampleFormatted() {
        return format(sample());
    }

    default String sampleFormatted(Chunk c) {
        return format(sample(c));
    }
}
