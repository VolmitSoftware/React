package com.volmit.react.api.sampler;

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.react.React;
import com.volmit.react.api.ReactComponent;
import com.volmit.react.util.scheduling.J;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public interface Sampler extends ReactComponent {
    double sample();

    default double sample(Chunk c) {
        return React.instance.getObserverController().sample(c, this).orElse(0D);
    }

    default String format(double t) {
        return formattedValue(t) + " " + formattedSuffix(t);
    }

    default AtomicDouble getChunkCounter(Chunk c) {
        return React.instance.getObserverController().get(c, this);
    }

    default AtomicDouble getChunkCounter(Block b) {
        return React.instance.getObserverController().get(b, this);
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
        return React.instance.getSampleController().getSamplers().get(id);
    }

    default String sampleFormatted() {
        return format(sample());
    }

    default String sampleFormatted(Chunk c) {
        return format(sample(c));
    }
}
