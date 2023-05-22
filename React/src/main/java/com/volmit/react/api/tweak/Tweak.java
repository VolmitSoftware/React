package com.volmit.react.api.tweak;

import com.volmit.react.util.registry.Registered;

public interface Tweak extends Registered {
    void onActivate();

    boolean isEnabled();

    void onDeactivate();

    int getTickInterval();

    void onTick();

    @Override
    default String getConfigCategory() {
        return "tweak";
    }
}

