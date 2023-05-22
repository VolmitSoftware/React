package com.volmit.react.api.feature;

import com.volmit.react.util.registry.Registered;

public interface Feature extends Registered {
    void onActivate();

    boolean isEnabled();

    void onDeactivate();

    int getTickInterval();

    void onTick();

    @Override
    default String getConfigCategory() {
        return "feature";
    }
}
