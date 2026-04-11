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

package art.arcane.react.content.feature;

import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.data.B;
import art.arcane.react.util.project.world.FastWorld;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
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
 * Reduces entity spawns / garbage by teleporting drops and xp from blocks and
 * entities directly into your inventory
 */
@art.arcane.react.util.project.config.ConfigDescription("Configuration for Fast Explosions feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureFastExplosions extends ReactFeature implements Listener {
  public static final String ID = "fast-explosions";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum primes allowed per tick in fast explosions.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxPrimesPerTick = 3;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Fuse spread added between chained primed TNT entities in fast explosions (ticks).", impact = "Higher values stagger explosions more; lower values keep chain timing tighter.")
  private int spreadPrimedFuseTicks = 7;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum explosion chains allowed per tick in fast explosions.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxExplosionChainsPerTick = 3;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast explosions applies fast block updates.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean fastBlockUpdates = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast explosions applies disable entity chain reactions.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean disableEntityChainReactions = false;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast explosions applies explosion chain reactions.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
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

    int overflow = preprimes - maxPrimesPerTick;
    if (overflow > 0 && !e.blockList().isEmpty()) {
      int removeCount = Math.min(overflow, e.blockList().size());
      e.blockList().subList(0, removeCount).clear();
      preprimes -= removeCount;
    }

    processExplosionBlocks(b, e.getYield());
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

    int overflow = preprimes - maxPrimesPerTick;
    if (overflow > 0 && !e.blockList().isEmpty()) {
      int removeCount = Math.min(overflow, e.blockList().size());
      e.blockList().subList(0, removeCount).clear();
      preprimes -= removeCount;
    }

    processExplosionBlocks(b, e.getYield());
  }

  private void processExplosionBlocks(Iterable<Block> blocks, float yield) {
    for (Block block : blocks) {
      if (block == null) {
        continue;
      }

      Location location = block.getLocation().clone();
      J.s(location, () -> processExplosionBlock(location, yield), 0);
    }
  }

  private void processExplosionBlock(Location location, float yield) {
    if (location == null || location.getWorld() == null) {
      return;
    }

    Block block = location.getBlock();
    if (block.getType().equals(Material.TNT)) {
      if (explosionChainReactions) {
        FastWorld.set(block, B.getAir(), fastBlockUpdates);
        if (maxExplosionChainsPerTick > explosionChains++) {
          block.getWorld().createExplosion(block.getLocation(), 4f, false, true);
        }
      }
      return;
    }

    if (M.r((double) yield)) {
      if (block.getState() instanceof Container container) {
        ItemStack[] contents = container.getInventory().getContents();
        for (ItemStack item : contents) {
          if (item != null) {
            block.getWorld().dropItemNaturally(block.getLocation(), item);
          }
        }
        container.getInventory().clear();
      } else {
        block.getDrops(null).forEach(drop -> block.getWorld().dropItemNaturally(block.getLocation(), drop));
      }
    }

    FastWorld.set(block, B.getAir(), fastBlockUpdates);
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
