package com.volmit.react.api.tweak;

import com.volmit.react.api.ReactComponent;

public interface Tweak extends ReactComponent {
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

