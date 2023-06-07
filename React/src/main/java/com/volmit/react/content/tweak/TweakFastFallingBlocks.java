package com.volmit.react.content.tweak;

import art.arcane.chrono.PrecisionStopwatch;
import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.util.data.B;
import com.volmit.react.util.math.RNG;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TweakFastFallingBlocks extends ReactTweak implements Listener {
    public static final String ID = "fast-falling-blocks";
    private transient List<Runnable> jobs;
    private transient Set<Block> queued;
    private transient int ticker;
    private double maxFallMS = 1.5;

    public TweakFastFallingBlocks() {
        super(ID);
    }

    @Override
    public void onActivate() {
        jobs = new ArrayList<>();
        queued = new HashSet<>();
        ticker = J.sr(() -> {
            if (jobs.isEmpty()) {
                return;
            }

            PrecisionStopwatch p = PrecisionStopwatch.start();

            while (p.getMilliseconds() < maxFallMS && !jobs.isEmpty()) {
                if (jobs.size() > 3) {
                    jobs.remove(RNG.r.i(jobs.size() - 1)).run();
                } else {
                    jobs.remove(0).run();
                }
            }
        }, 0);
    }

    public void fallEffect(Location at, BlockData item) {
        at.getWorld().playSound(at, item.getSoundGroup().getBreakSound(), 1f, 1f);
        at.getWorld().spawnParticle(Particle.FALLING_DUST, at.getBlock().getLocation().add(0.5, 0.5, 0.5), 6, 0.25, 0.5, 0.25, 0.1, item);
    }

    public void landEffect(Location at, BlockData item) {
        at.getWorld().playSound(at, item.getSoundGroup().getPlaceSound(), 1f, 1f);
        at.getWorld().spawnParticle(Particle.ITEM_CRACK, at.getBlock().getLocation().add(0.5, -0.5, 0.5), 24, 0.6, 0.6, 0.6, 0.15, new ItemStack(item.getMaterial(), 1));
    }

    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.MONITOR)
    public void on(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof FallingBlock f) {
            e.setCancelled(true);
            BlockData d = f.getBlockData();
            Block b = e.getBlock().getLocation().getBlock();
            int bonusx = 0;

            for (int i = b.getY() + 1; i < b.getWorld().getMaxHeight() - 1; i++) {
                if (b.getWorld().getBlockAt(b.getX(), i, b.getZ()).getBlockData().equals(d)) {
                    bonusx++;
                } else {
                    break;
                }
            }

            if (queued.contains(b)) {
                return;
            }

            int bonus = bonusx;
            queued.add(b);
            jobs.add(() -> {
                if (!b.getBlockData().equals(d)) {
                    return;
                }

                b.setBlockData(B.getAir());

                for (int i = 0; i < bonus; i++) {
                    Block bx = b.getWorld().getBlockAt(b.getX(), b.getY() + 1 + i, b.getZ());
                    if (!bx.getBlockData().equals(d)) {
                        return;
                    }

                    bx.setBlockData(B.getAir(), false);
                }

                queued.remove(b);
                fallEffect(e.getBlock().getLocation(), d);
                for (int i = b.getY(); i > b.getWorld().getMinHeight() + 1; i--) {
                    Block bb = b.getWorld().getBlockAt(b.getX(), i, b.getZ());

                    if (bb.getRelative(BlockFace.DOWN).getType().isSolid()) {
                        for (int j = 0; j < bonus; j++) {
                            bb.getWorld().getBlockAt(bb.getX(), bb.getY() + 1 + j, bb.getZ()).setBlockData(d, false);
                        }

                        bb.setBlockData(d, false);
                        landEffect(bb.getLocation(), d);
                        break;
                    }

                    if (!bb.isEmpty() && bb.isPassable() && !B.isFluid(bb.getBlockData())) {
                        bb.getWorld().dropItemNaturally(bb.getLocation(), new ItemStack(d.getMaterial(), 1 + bonus));
                        break;
                    }
                }
            });
        }
    }

    @Override
    public void onDeactivate() {
        for (Runnable i : jobs) {
            i.run();
        }
    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}
