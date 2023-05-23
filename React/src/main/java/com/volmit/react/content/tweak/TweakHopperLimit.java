package com.volmit.react.content.tweak;

import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.content.sampler.SamplerHopperTickTime;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;

public class TweakHopperLimit extends ReactTweak implements Listener {
    private static final BlockFace[] directions = new BlockFace[] {
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST
    };
    public static final String ID = "hopper-limit";
    private double maxHopperTickTime = 0.75;

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

    public boolean shouldCompare(Block b) {
        if(b.getType().equals(Material.COMPARATOR)) {
            return true;
        }

        return false;
    }

    @EventHandler
    public void on(InventoryMoveItemEvent e) {
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
