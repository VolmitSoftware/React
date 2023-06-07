package com.volmit.react.content.tweak;

import com.volmit.react.api.tweak.ReactTweak;
import org.bukkit.event.Listener;

public class TweakTestETick extends ReactTweak implements Listener {
    public static final String ID = "test-etick";

    public TweakTestETick() {
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
