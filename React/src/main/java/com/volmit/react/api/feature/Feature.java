package com.volmit.react.api.feature;

import com.volmit.react.api.ReactComponent;

public interface Feature extends ReactComponent {
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
