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

import art.arcane.chrono.PrecisionStopwatch;
import art.arcane.react.api.tweak.ReactTweak;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.data.B;
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

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Fast Falling Blocks tweak. Accelerates long-lived falling-block entities when fall processing starts causing measurable tick cost.")
public class TweakFastFallingBlocks extends ReactTweak implements Listener {
  public static final String ID = "fast-falling-blocks";
  private transient List<Runnable> jobs;
  private transient Set<Block> queued;
  private transient int ticker;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum fall ms allowed by fast falling blocks.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private double maxFallMS = 1.5;

  public TweakFastFallingBlocks() {
    super(ID);
  }

  @Override
  public void onActivate() {
    jobs = new ArrayList<>();
    queued = new HashSet<>();
    ticker = J.sr(() -> {
      PrecisionStopwatch p = PrecisionStopwatch.start();

      while (p.getMilliseconds() < maxFallMS) {
        Runnable job;
        synchronized (jobs) {
          if (jobs.isEmpty()) {
            return;
          }

          job = jobs.remove(0);
        }

        job.run();
      }
    }, 0);
  }

  public void fallEffect(Location at, BlockData item) {
    at.getWorld().playSound(at, item.getSoundGroup().getBreakSound(), 1f, 1f);
    at.getWorld().spawnParticle(Particle.FALLING_DUST, at.getBlock().getLocation().add(0.5, 0.5, 0.5), 6, 0.25, 0.5, 0.25, 0.1, item);
  }

  public void landEffect(Location at, BlockData item) {
    at.getWorld().playSound(at, item.getSoundGroup().getPlaceSound(), 1f, 1f);
    if (item.getMaterial().isItem()) {
      at.getWorld().spawnParticle(Particle.ITEM, at.getBlock().getLocation().add(0.5, -0.5, 0.5), 24, 0.6, 0.6, 0.6, 0.15, new ItemStack(item.getMaterial(), 1));
    } else {
      at.getWorld().spawnParticle(Particle.BLOCK, at.getBlock().getLocation().add(0.5, -0.5, 0.5), 24, 0.6, 0.6, 0.6, 0.15, item);
    }
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

      synchronized (queued) {
        if (queued.contains(b)) {
          return;
        }

        queued.add(b);
      }

      int bonus = bonusx;
      Location blockLocation = b.getLocation().clone();
      synchronized (jobs) {
        jobs.add(() -> {
          J.s(blockLocation, () -> {
            try {
              Block target = blockLocation.getBlock();
              if (!target.getBlockData().equals(d)) {
                return;
              }

              target.setBlockData(B.getAir());

              for (int i = 0; i < bonus; i++) {
                Block bx = target.getWorld().getBlockAt(target.getX(), target.getY() + 1 + i, target.getZ());
                if (!bx.getBlockData().equals(d)) {
                  return;
                }

                bx.setBlockData(B.getAir(), false);
              }

              fallEffect(blockLocation, d);
              for (int i = target.getY(); i > target.getWorld().getMinHeight() + 1; i--) {
                Block bb = target.getWorld().getBlockAt(target.getX(), i, target.getZ());

                if (bb.getRelative(BlockFace.DOWN).getType().isSolid()) {
                  for (int j = 0; j < bonus; j++) {
                    bb.getWorld().getBlockAt(bb.getX(), bb.getY() + 1 + j, bb.getZ()).setBlockData(d, false);
                  }

                  bb.setBlockData(d, false);
                  landEffect(bb.getLocation(), d);
                  break;
                }

                if (!bb.isEmpty() && bb.isPassable() && !B.isFluid(bb.getBlockData())) {
                  if (d.getMaterial().isItem()) {
                    bb.getWorld().dropItemNaturally(bb.getLocation(), new ItemStack(d.getMaterial(), 1 + bonus));
                  }
                  break;
                }
              }
            } finally {
              synchronized (queued) {
                queued.remove(b);
              }
            }
          }, 0);
        });
      }
    }
  }

  @Override
  public void onDeactivate() {
    if (ticker != 0) {
      J.csr(ticker);
      ticker = 0;
    }

    if (jobs != null) {
      synchronized (jobs) {
        jobs.clear();
      }
    }

    if (queued != null) {
      synchronized (queued) {
        queued.clear();
      }
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
