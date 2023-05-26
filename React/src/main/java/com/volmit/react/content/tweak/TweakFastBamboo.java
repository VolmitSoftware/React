package com.volmit.react.content.tweak;

import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.util.data.B;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.world.FastWorld;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bamboo;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class TweakFastBamboo extends ReactTweak implements Listener {
    public static final String ID = "fast-bamboo";

    public TweakFastBamboo() {
        super(ID);
    }

    @Override
    public void onActivate() {

    }

    @EventHandler
    public void on(BlockPhysicsEvent e) {
        if(e.getBlock().getBlockData() instanceof Bamboo) {
            J.ss(() -> {
                if(e.getBlock().isEmpty()) {
                    e.setCancelled(true);
                    demolish(e.getSourceBlock());
                }
            },2);
        }
    }

    public void demolish(Block bamboo) {
        int f = 0;

        boolean latched = false;

        for(int i = bamboo.getY()+1; i < bamboo.getWorld().getMaxHeight()-1; i++) {
            Block x = bamboo.getWorld().getBlockAt(bamboo.getX(), i, bamboo.getZ());
            Block xp = bamboo.getWorld().getBlockAt(bamboo.getX(), i + 1, bamboo.getZ());
            if(x.getBlockData() instanceof Bamboo) {
                latched = true;
                x.setBlockData(B.getAir(), !(xp.getBlockData() instanceof Bamboo));
                f++;
            }

            else if(latched) {
                break;
            }
        }

        if(f > 0) {
            bamboo.getWorld().dropItemNaturally(bamboo.getLocation(), new ItemStack(Material.BAMBOO, f));
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
