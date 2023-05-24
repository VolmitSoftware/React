package com.volmit.react.content.tweak;

import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.content.sampler.SamplerHopperTickTime;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

public class TweakHopperLimit extends ReactTweak implements Listener {
    public static final String ID = "hopper-limit";
    private static final BlockFace[] directions = new BlockFace[]{
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST
    };
    private double maxHopperTickTime = 0.75;

    public TweakHopperLimit() {
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

    /**
     * This is the method that is called when a hopper moves an item.
     * and it denies the hopper from moving the item if the tick time is too high.
     */
    @EventHandler
    public void on(InventoryMoveItemEvent e) {
        if (e.getDestination().getHolder() instanceof Hopper h) {
            if (React.sampler(SamplerHopperTickTime.class).sample() > maxHopperTickTime) {
                e.setCancelled(true);
            }
        }
    }

    @Override
    public void onTick() {

    }
}
