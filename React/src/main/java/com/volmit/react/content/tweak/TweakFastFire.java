package com.volmit.react.content.tweak;

import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.world.FastWorld;
import org.bukkit.block.data.type.Fire;
import org.bukkit.block.data.type.Snow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockSpreadEvent;

public class TweakFastFire extends ReactTweak implements Listener {
    public static final String ID = "fast-fire";

    public TweakFastFire() {
        super(ID);
    }

    @Override
    public void onActivate() {

    }

    @EventHandler
    public void on(BlockSpreadEvent e) {
        if(e.getBlock().getBlockData() instanceof Fire f) {
            e.setCancelled(true);
            J.s(() -> FastWorld.set(e.getBlock(), f));
        }
    }

    @EventHandler
    public void on(BlockFadeEvent e) {
        if(e.getBlock().getBlockData() instanceof Fire) {
            e.setCancelled(true);
            J.s(() -> FastWorld.breakNaturally(e.getBlock()));
        }
    }

    @EventHandler
    public void on(BlockBurnEvent e) {
        e.setCancelled(true);
        J.s(() -> FastWorld.breakNaturally(e.getBlock()));
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
