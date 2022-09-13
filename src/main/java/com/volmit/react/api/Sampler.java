package com.volmit.react.api;

import com.volmit.react.React;
import com.volmit.react.util.J;
import com.volmit.react.util.tick.Ticked;
import org.bukkit.Bukkit;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface Sampler {
    double sample();

    String format(double t);

    void start();

    void stop();

    String getId();

    default <T> T  executeSync(Supplier<T> executor) {
        if(Bukkit.isPrimaryThread()) {
            return executor.get();
        }

        AtomicReference<T> result = new AtomicReference<>();
        J.s(() -> result.set(executor.get()));

        while(result.get() == null) {
            J.sleep(5);
        }

        return result.get();
    }

    default Sampler getSampler(String id) {
        return React.instance.getSampleController().getSamplers().get(id);
    }

    default String sampleFormatted(){
        return format(sample());
    }
}
