package com.volmit.react.content.tweak;

import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.content.sampler.SamplerHopperTickTime;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

public class TweakHopperLimit extends ReactTweak implements Listener {
    public static final String ID = "hopper-limit";
    private double maxHopperTickTime = 0.75;
    private boolean optimizeHopperTransfers = true;

    public TweakHopperLimit() {
        super(ID);
    }

    @Override
    public void onActivate() {
        React.instance.registerListener(this);
    }
    @Override
    public void onDeactivate() {
        React.instance.unregisterListener(this);
    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @EventHandler
    public void on(InventoryMoveItemEvent e) {
        if(optimizeHopperTransfers) {

        }

        if (e.getDestination().getHolder() instanceof Hopper h) {
            if(React.sampler(SamplerHopperTickTime.class).sample() > maxHopperTickTime) {
                e.setCancelled(true);
            }
        }
    }

    @Override
    public void onTick() {

    }
}
