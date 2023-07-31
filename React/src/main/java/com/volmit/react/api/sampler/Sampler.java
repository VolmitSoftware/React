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

package com.volmit.react.api.sampler;

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.react.React;
import com.volmit.react.api.rendering.Graph;
import com.volmit.react.api.rendering.ReactRenderer;
import com.volmit.react.core.controller.ObserverController;
import com.volmit.react.core.controller.SampleController;
import com.volmit.react.util.data.TinyColor;
import com.volmit.react.util.math.M;
import com.volmit.react.util.registry.Registered;
import com.volmit.react.util.scheduling.J;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public interface Sampler extends Registered, ReactRenderer {
    double sample();

    default double sample(Chunk c) {
        return React.controller(ObserverController.class).sample(c, this).orElse(0D);
    }

    default void render() {
        clear(new TinyColor(0, 0, 0));
        Graph g = Graph.of(this);
        double min = g.getMin();
        double max = g.getMax();
        double pmax = g.getPaddedMax(0.15);
        double pmin = g.getPaddedMin(0.15);

        for (int ig = 0; ig < 128; ig++) {
            int i = 127 - ig;
            int v = (int) M.lerp(127, 0, M.lerpInverse(pmin, pmax, g.get(i)));

            for (int igx = v; igx < 128; igx++) {
                set(ig, igx, new TinyColor((128 - (igx)) + 127, (128 - (igx)) + 127, (128 - (igx)) + 127));
            }
        }

        String s = format(g.get(0));
        String smin = format(min);
        String smax = format(max);
        textNear((127 + textWidth(s)) / 2, (127 + textHeight()) / 2, s);
        textNear(0, 127, smin);
        textNear(0, 0, smax);
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

        AtomicReference<T> result = new AtomicReference<>();
        J.s(() -> result.set(executor.get()));

        while (result.get() == null) {
            J.sleep(5);
        }

        return result.get();
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
