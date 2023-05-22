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
import com.volmit.react.util.scheduling.Observable;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public interface Sampler extends Registered, ReactRenderer {
    double sample();

    default double sample(Chunk c) {
        return React.controller(ObserverController.class).sample(c, this).orElse(0D);
    }

    default void render() {
        clear(new TinyColor(0,0,0));
        Graph g = Graph.of(this);
        double min = g.getMin();
        double max = g.getMax();
        double pmax = g.getPaddedMax(0.15);
        double pmin = g.getPaddedMin(0.15);

        for(int ig = 0; ig < 128; ig++) {
            int i = 127 - ig;
            int v = (int) M.lerp(127, 0, M.lerpInverse(pmin, pmax, g.get(i)));

            if(i > 0) {
                int ov = (int) M.lerp(127, 0, M.lerpInverse(pmin, pmax, g.get(i-1)));
                line(ig, ov, ig, v, new TinyColor(255, 255, 255));
            }

            else {
                set(ig, v, new TinyColor(255, 255, 255));
            }
        }

        String s = format(g.get(0));
        String smin = format(min);
        String smax = format(max);
        textNear((127+textWidth(s))/2, (127+textHeight())/2, s);
        textNear(0,127, smin);
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
