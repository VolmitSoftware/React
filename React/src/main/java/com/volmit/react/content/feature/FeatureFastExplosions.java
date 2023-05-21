package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.util.data.B;
import com.volmit.react.util.math.M;
import com.volmit.react.util.math.RNG;
import com.volmit.react.util.scheduling.J;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reduces entity spawns / garbage by teleporting drops and xp from blocks and entities directly into your inventory
 */
public class FeatureFastExplosions extends ReactFeature implements Listener {
    public static final String ID = "fast-explosions";
    private int maxPrimesPerTick = 3;
    private int spreadPrimedFuseTicks = 7;
    private int maxExplosionChainsPerTick = 3;
    private boolean fastBlockUpdates = true;
    private boolean disableEntityChainReactions = false;
    private boolean explosionChainReactions = false ;
    private transient int primes = 0;
    private transient int preprimes = 0;
    private transient int explosionChains = 0;

    public FeatureFastExplosions() {
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntitySpawnEvent e) {
        if(spreadPrimedFuseTicks > 0 && e.getEntity() instanceof TNTPrimed tnt) {
            tnt.setFuseTicks((primes * spreadPrimedFuseTicks) + tnt.getFuseTicks());
        }
        primes++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(BlockExplodeEvent e) {
        var b = new ArrayList<>(e.blockList());

        if(disableEntityChainReactions) {
            e.blockList().clear();
        }

        else {
            e.blockList().removeIf((i) -> !i.getType().equals(Material.TNT));
        }

        preprimes+= e.blockList().size();

        while(preprimes > maxPrimesPerTick && e.blockList().size() > 0) {
            e.blockList().remove(0);
            preprimes--;
        }

        J.s(() -> {
            for(Block i :b) {
                if(i.getType().equals(Material.TNT)) {
                    if(explosionChainReactions) {
                        i.setBlockData(B.getAir(), !fastBlockUpdates);

                        if(maxExplosionChainsPerTick > explosionChains++) {
                            J.s(() -> i.getWorld().createExplosion(i.getLocation(), 4f, false, true));
                        }
                    }

                    continue;
                }

                if(M.r((double)e.getYield())) {
                    i.getDrops(null).forEach((f) -> i.getLocation().getWorld().dropItem(i.getLocation(), f));
                }

                i.setBlockData(B.getAir(), !fastBlockUpdates);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityExplodeEvent e) {
        var b = new ArrayList<>(e.blockList());

        if(disableEntityChainReactions) {
            e.blockList().clear();
        }

        else {
            e.blockList().removeIf((i) -> !i.getType().equals(Material.TNT));
        }

        preprimes+= e.blockList().size();

        while(preprimes > maxPrimesPerTick && e.blockList().size() > 0) {
            e.blockList().remove(0);
            preprimes--;
        }

        J.s(() -> {
            for(Block i :b) {
                if(i.getType().equals(Material.TNT)) {
                    if(explosionChainReactions) {
                        i.setBlockData(B.getAir(), !fastBlockUpdates);
                        if(maxExplosionChainsPerTick > explosionChains++) {
                            J.s(() -> i.getWorld().createExplosion(i.getLocation(), 4f, false, true));
                        }
                    }

                    continue;
                }

                if(M.r((double)e.getYield())) {
                    i.getDrops(null).forEach((f) -> i.getLocation().getWorld().dropItem(i.getLocation(), f));
                }

                i.setBlockData(B.getAir(), !fastBlockUpdates);
            }
        });
    }

    public void fastExplosion(Location at, float yield) {

    }

    @Override
    public int getTickInterval() {
        return 250;
    }

    @Override
    public void onTick() {
        primes = 0;
        preprimes = 0;
        explosionChains = 0;
    }
}
