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

package art.arcane.react.content.tweak;

import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.ChainedColumn;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Fast Columns tweak. Collapses plant columns in chained updates to reduce repeated per-block physics churn.")
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
    Block block = e.getBlock();
    Location location = block.getLocation();
    if (bamboo.is(block)) {
      J.s(location, () -> triggerIfEmpty(location, bamboo), 2);
    } else if (sugarCane.is(block)) {
      J.s(location, () -> triggerIfEmpty(location, sugarCane), 2);
    } else if (cactus.is(block)) {
      J.s(location, () -> triggerIfEmpty(location, cactus), 2);
    } else if (kelp.is(block)) {
      J.s(location, () -> triggerIfEmpty(location, kelp), 2);
    }
  }

  private void triggerIfEmpty(Location location, ChainedColumn column) {
    Block block = location.getBlock();
    if (column.isEmpty(block)) {
      column.trigger(block, maxColumnSize);
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
