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

package com.volmit.react.content.tweak;

import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.world.ChainedColumn;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;

public class TweakFastColumns extends ReactTweak implements Listener {
    public static final String ID = "fast-columns";
    private transient ChainedColumn bamboo;
    private transient ChainedColumn sugarCane;
    private transient ChainedColumn kelp;
    private transient ChainedColumn cactus;
    private transient int maxColumnSize = 16;

    public TweakFastColumns() {
        super(ID);
    }

    @Override
    public void onActivate() {
        bamboo = new ChainedColumn(Material.BAMBOO, Material.BAMBOO, false);
        sugarCane = new ChainedColumn(Material.SUGAR_CANE, Material.SUGAR_CANE, false);
        cactus = new ChainedColumn(Material.CACTUS, Material.CACTUS, false);
        kelp = new ChainedColumn(Material.KELP_PLANT, Material.KELP, true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        if (bamboo.is(e.getBlock())) {
            bamboo.trigger(e.getPlayer(), e.getBlock(), maxColumnSize);
        } else if (sugarCane.is(e.getBlock())) {
            sugarCane.trigger(e.getPlayer(), e.getBlock(), maxColumnSize);
        } else if (cactus.is(e.getBlock())) {
            cactus.trigger(e.getPlayer(), e.getBlock(), maxColumnSize);
        } else if (kelp.is(e.getBlock())) {
            kelp.trigger(e.getPlayer(), e.getBlock(), maxColumnSize);
        }
    }

    @EventHandler
    public void on(BlockPhysicsEvent e) {
        if (bamboo.is(e.getBlock())) {
            J.ss(() -> {
                if (bamboo.isEmpty(e.getBlock())) {
                    bamboo.trigger(e.getBlock(), maxColumnSize);
                }
            }, 2);
        } else if (sugarCane.is(e.getBlock())) {
            J.ss(() -> {
                if (sugarCane.isEmpty(e.getBlock())) {
                    sugarCane.trigger(e.getBlock(), maxColumnSize);
                }
            }, 2);
        } else if (cactus.is(e.getBlock())) {
            J.ss(() -> {
                if (cactus.isEmpty(e.getBlock())) {
                    cactus.trigger(e.getBlock(), maxColumnSize);
                }
            }, 2);
        } else if (kelp.is(e.getBlock())) {
            J.ss(() -> {
                if (kelp.isEmpty(e.getBlock())) {
                    kelp.trigger(e.getBlock(), maxColumnSize);
                }
            }, 2);
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
