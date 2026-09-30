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

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Fast Columns tweak. Collapses plant columns in chained updates to reduce repeated per-block physics churn.")
public class TweakFastColumns extends ReactTweak implements Listener {
  public static final String ID = "fast-columns";
  private transient ChainedColumn bamboo;
  private transient ChainedColumn sugarCane;
  private transient ChainedColumn kelp;
  private transient ChainedColumn cactus;
  private transient int maxColumnSize = 16;
  private transient final Set<ColumnCheck> pendingChecks = ConcurrentHashMap.newKeySet();

  public TweakFastColumns() {
    super(ID);
  }

  @Override
  public void onActivate() {
    pendingChecks.clear();
    bamboo = new ChainedColumn(Material.BAMBOO, Material.BAMBOO, false);
    sugarCane = new ChainedColumn(Material.SUGAR_CANE, Material.SUGAR_CANE, false);
    cactus = new ChainedColumn(Material.CACTUS, Material.CACTUS, false);
    kelp = new ChainedColumn(Material.KELP_PLANT, Material.KELP, true);
  }

  @Override
  public void onDeactivate() {
    pendingChecks.clear();
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    Block block = e.getBlock();
    ChainedColumn column = columnFor(block.getType());
    if (column != null) {
      column.trigger(e.getPlayer(), block, maxColumnSize);
    }
  }

  @EventHandler
  public void on(BlockPhysicsEvent e) {
    Block block = e.getBlock();
    ChainedColumn column = columnFor(block.getType());
    if (column == null) {
      return;
    }

    ColumnCheck check = new ColumnCheck(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    if (!pendingChecks.add(check)) {
      return;
    }

    Location location = block.getLocation();
    try {
      J.s(location, () -> runCheck(check, location, column), 2);
    } catch (RuntimeException failure) {
      pendingChecks.remove(check);
      throw failure;
    }
  }

  private ChainedColumn columnFor(Material type) {
    return switch (type) {
      case BAMBOO -> bamboo;
      case SUGAR_CANE -> sugarCane;
      case CACTUS -> cactus;
      case KELP_PLANT -> kelp;
      default -> null;
    };
  }

  private void runCheck(ColumnCheck check, Location location, ChainedColumn column) {
    pendingChecks.remove(check);
    triggerIfEmpty(location, column);
  }

  private void triggerIfEmpty(Location location, ChainedColumn column) {
    Block block = location.getBlock();
    if (column.isEmpty(block)) {
      column.trigger(block, maxColumnSize);
    }
  }

  private record ColumnCheck(UUID worldId, int x, int y, int z) {
  }
}
