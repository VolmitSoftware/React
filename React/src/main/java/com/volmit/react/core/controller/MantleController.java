package com.volmit.react.core.controller;

import com.volmit.react.React;
import com.volmit.react.model.SampledServer;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.WorldUnloadEvent;

@EqualsAndHashCode(callSuper = true)
@Data
public class MantleController extends TickedObject implements IController {
    private transient final SampledServer sampled;

    public MantleController() {
        super("react", "mantle", 1000);
        sampled = new SampledServer();
        start();
    }

    @Override
    public void onTick() {

    }

    @Override
    public String getName() {
        return "Mantle";
    }

    @Override
    public void start() {
        React.instance.registerListener(this);
    }

    @Override
    public void stop() {
        React.instance.unregisterListener(this);
    }

    @Override
    public void postStart() {

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(WorldUnloadEvent event) {

    }
}
