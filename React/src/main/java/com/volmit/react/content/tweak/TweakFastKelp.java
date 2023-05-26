package com.volmit.react.content.tweak;

import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.util.data.B;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.inventory.ItemStack;

public class TweakFastKelp extends ReactTweak implements Listener {
    public static final String ID = "fast-kelp";

    public TweakFastKelp() {
        super(ID);
    }

    @Override
    public void onActivate() {

    }

    @EventHandler
    public void on(BlockPhysicsEvent e) {
        if(e.getBlock().getType().equals(Material.KELP_PLANT)) {
            J.ss(() -> {
                if(e.getBlock().getType().equals(Material.WATER)) {
                    e.setCancelled(true);
                    demolish(e.getSourceBlock());
                }
            },2);
        }
    }

    public void demolish(Block kelp) {
        int f = 0;

        boolean latched = false;

        for(int i = kelp.getY()+1; i < kelp.getWorld().getMaxHeight()-1; i++) {
            Block x = kelp.getWorld().getBlockAt(kelp.getX(), i, kelp.getZ());
            Block xp = kelp.getWorld().getBlockAt(kelp.getX(), i + 1, kelp.getZ());
            if(x.getType().equals(Material.KELP_PLANT)) {
                latched = true;
                x.setType(Material.WATER, !xp.getType().equals(Material.KELP_PLANT));
                f++;
            }

            else if(latched) {
                break;
            }
        }

        if(f > 0) {
            kelp.getWorld().dropItemNaturally(kelp.getLocation(), new ItemStack(Material.KELP, f));
        }
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
