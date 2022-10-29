package com.volmit.react.sampler;

import art.arcane.curse.Curse;
import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedSampler;
import com.volmit.react.util.Form;
import com.volmit.react.util.RollingSequence;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredListener;

import java.util.ArrayList;
import java.util.EnumMap;

public class SamplerEventListeners extends ReactCachedSampler implements Listener {
    public static final String ID = "events-listeners";

    public SamplerEventListeners() {
        super(ID, 50);
    }

    @Override
    public double onSample() {
        return React.instance.getEventController().getListenerCount();
    }

    @Override
    public String formattedValue(double t) {
        return Form.f(t);
    }

    @Override
    public String formattedSuffix(double t) {
        return "LISTENERS";
    }
}
