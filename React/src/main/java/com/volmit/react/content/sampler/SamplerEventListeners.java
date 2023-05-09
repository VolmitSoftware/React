package com.volmit.react.content.sampler;

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.format.Form;
import org.bukkit.Material;
import org.bukkit.event.Listener;

public class SamplerEventListeners extends ReactCachedSampler implements Listener {
    public static final String ID = "events-listeners";

    public SamplerEventListeners() {
        super(ID, 50);
    }

    @Override
    public Material getIcon() {
        return Material.ENDER_PEARL;
    }

    @Override
    public double onSample() {
        return React.instance.getEventController().getListenerCount();
    }

    @Override
    public String formattedValue(double t) {
        return Form.f(Math.round(t));
    }

    @Override
    public String formattedSuffix(double t) {
        return "LISTENERS";
    }
}