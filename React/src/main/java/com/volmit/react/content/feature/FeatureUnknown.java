package com.volmit.react.content.feature;

import com.volmit.react.api.feature.ReactFeature;

public class FeatureUnknown extends ReactFeature {
    public static final String ID = "unknown";

    public FeatureUnknown() {
        super(ID);
    }

    @Override
    public void onActivate() {

    }

    @Override
    public void onDeactivate() {

    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}
