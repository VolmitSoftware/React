package com.volmit.react.api.command;

public interface RCommand {
    //print that the command loaded
    default void onLoad() {
        RConst.info("Loaded Command: " + getClass().getSimpleName());
    }

}
