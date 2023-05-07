package com.volmit.react.api.monitor;

import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.util.tick.Ticked;
import org.bukkit.event.Listener;

import java.util.Map;

public interface Monitor extends Ticked, Listener {
    Map<Sampler, Double> getSamplers();

    /**
     * Called when a sampler has changed
     */
    void flush();

    default Monitor sample(String samplerId) {
        return sample(React.instance.getSampleController().getSampler(samplerId));
    }

    default boolean isAlwaysFlushing() {
        return false;
    }

    default Monitor sample(Sampler sampler) {
        getSamplers().put(sampler, sampler.sample());
        return this;
    }

    default void start() {
        React.instance.getTicker().register(this);
        React.instance.registerListener(this);
    }

    default void stop() {
        unregister();
        React.instance.unregisterListener(this);
    }
}
