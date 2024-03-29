/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.volmit.react.content.feature;

import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.util.data.B;
import com.volmit.react.util.math.M;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.world.FastWorld;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;

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
    private boolean explosionChainReactions = false;
    private transient int primes = 0;
    private transient int preprimes = 0;
    private transient int explosionChains = 0;

    public FeatureFastExplosions() {
        super(ID);
    }

    @Override
    public void onActivate() {

    }

    @Override
    public void onDeactivate() {

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntitySpawnEvent e) {
        if (spreadPrimedFuseTicks > 0 && e.getEntity() instanceof TNTPrimed tnt) {
            tnt.setFuseTicks((primes * spreadPrimedFuseTicks) + tnt.getFuseTicks());
        }
        primes++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(BlockExplodeEvent e) {
        var b = new ArrayList<>(e.blockList());

        if (disableEntityChainReactions) {
            e.blockList().clear();
        } else {
            e.blockList().removeIf((i) -> !i.getType().equals(Material.TNT));
        }

        preprimes += e.blockList().size();

        while (preprimes > maxPrimesPerTick && e.blockList().size() > 0) {
            e.blockList().remove(0);
            preprimes--;
        }

        J.s(() -> {
            for (Block i : b) {
                if (i.getType().equals(Material.TNT)) {
                    if (explosionChainReactions) {
                        FastWorld.set(i, B.getAir(), fastBlockUpdates);

                        if (maxExplosionChainsPerTick > explosionChains++) {
                            J.s(() -> i.getWorld().createExplosion(i.getLocation(), 4f, false, true));
                        }
                    }

                    continue;
                }

                if (M.r((double) e.getYield())) {
                    if (i.getState() instanceof Container) {
                        Container container = (Container) i.getState();
                        ItemStack[] contents = container.getInventory().getContents();
                        for (ItemStack item : contents) {
                            if (item != null) {
                                i.getWorld().dropItemNaturally(i.getLocation(), item);
                            }
                        }
                        container.getInventory().clear();
                    } else {
                        i.getDrops(null).forEach((f) -> i.getWorld().dropItemNaturally(i.getLocation(), f));
                    }
                }


                FastWorld.set(i, B.getAir(), fastBlockUpdates);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityExplodeEvent e) {
        var b = new ArrayList<>(e.blockList());

        if (disableEntityChainReactions) {
            e.blockList().clear();
        } else {
            e.blockList().removeIf((i) -> !i.getType().equals(Material.TNT));
        }

        preprimes += e.blockList().size();

        while (preprimes > maxPrimesPerTick && e.blockList().size() > 0) {
            e.blockList().remove(0);
            preprimes--;
        }

        J.s(() -> {
            for (Block i : b) {
                if (i.getType().equals(Material.TNT)) {
                    if (explosionChainReactions) {
                        FastWorld.set(i, B.getAir(), fastBlockUpdates);

                        if (maxExplosionChainsPerTick > explosionChains++) {
                            J.s(() -> i.getWorld().createExplosion(i.getLocation(), 4f, false, true));
                        }
                    }

                    continue;
                }

                if (M.r((double) e.getYield())) {
                    if (i.getState() instanceof Container) {
                        Container container = (Container) i.getState();
                        ItemStack[] contents = container.getInventory().getContents();
                        for (ItemStack item : contents) {
                            if (item != null) {
                                i.getWorld().dropItemNaturally(i.getLocation(), item);
                            }
                        }
                        container.getInventory().clear();
                    } else {
                        i.getDrops(null).forEach((f) -> i.getWorld().dropItemNaturally(i.getLocation(), f));
                    }
                }


                FastWorld.set(i, B.getAir(), fastBlockUpdates);
            }
        });
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
