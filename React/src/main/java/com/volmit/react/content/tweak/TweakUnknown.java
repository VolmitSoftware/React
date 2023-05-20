package com.volmit.react.content.tweak;

import com.volmit.react.api.tweak.ReactTweak;

public class TweakUnknown extends ReactTweak {
    public static final String ID = "unknown";

    public TweakUnknown() {
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
