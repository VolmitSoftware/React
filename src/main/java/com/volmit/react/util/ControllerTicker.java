package com.volmit.react.util;

import com.volmit.react.util.tick.TickedObject;

public class ControllerTicker extends TickedObject {
    private final IController controller;
    public ControllerTicker(IController controller, int ms)
    {
        super("react", "controller." + controller.getName(), ms);
        this.controller = controller;
    }

    @Override
    public void onTick() {
        controller.tick();
    }
}
