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

import art.arcane.chrono.ChronoLatch;
import art.arcane.chrono.PrecisionStopwatch;
import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.FastWorld;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Fast Leaf Decay feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureFastLeafDecay extends ReactFeature implements Listener {
  public static final String ID = "fast-leaf-decay";
  private final transient Set<Block> search = ConcurrentHashMap.newKeySet();
  private transient Cache<IChunk, ChunkSnapshot> snapshot;
  private transient ChronoLatch cooldownLatch;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Leaf decay radius used by fast leaf decay (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private int leafDecayDistance = 6;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Leaf decay radius used by fast leaf decay (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private int leafDecayRadius = 5;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum async ms allowed by fast leaf decay.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private double maxAsyncMS = 10;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum sync spike ms allowed by fast leaf decay.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private double maxSyncSpikeMS = 10;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for fast leaf decay in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 250;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Cooldown for decay trigger cooldown in fast leaf decay (milliseconds).", impact = "Higher values reduce repeat frequency; lower values allow reactions more often.")
  private int decayTriggerCooldownMS = 250;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Per-tick spread used to stagger leaf decay work in fast leaf decay.", impact = "Higher values spread work across more ticks; lower values process decay in tighter bursts.")
  private int decayTickSpread = 20;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Probability setting for sound chance in fast leaf decay.", impact = "Higher values make this effect occur more often; lower values make it rarer.")
  private double soundChance = 0.25;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Audio tuning value for sound volume in fast leaf decay.", impact = "Adjust this to balance audibility and tone for player feedback events.")
  private double soundVolume = 0.26;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Audio tuning value for sound pitch in fast leaf decay.", impact = "Adjust this to balance audibility and tone for player feedback events.")
  private double soundPitch = 0.2;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast leaf decay applies force decay persistent.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean forceDecayPersistent = false;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast leaf decay applies play sounds.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean playSounds = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether fast leaf decay applies fast block changes.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean fastBlockChanges = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Text value for decay sound used by fast leaf decay.", impact = "Adjust this to match your naming, routing, or permission conventions.")
  private String decaySound = "minecraft:block.azalea_leaves.fall";

  public FeatureFastLeafDecay() {
    super(ID);
  }

  @Override
  public void onActivate() {
    search.clear();
    if (J.isFoliaThreading()) {
      snapshot = Caffeine.newBuilder()
          .expireAfterAccess(10, TimeUnit.SECONDS)
          .build((k) -> k.chunk().getChunkSnapshot(true, false, false));
    } else {
      snapshot = Caffeine.newBuilder()
          .expireAfterAccess(10, TimeUnit.SECONDS)
          .refreshAfterWrite(1, TimeUnit.SECONDS)
          .build((k) -> k.chunk().getChunkSnapshot(true, false, false));
    }
    cooldownLatch = new ChronoLatch(decayTriggerCooldownMS);
  }

  @Override
  public void onDeactivate() {
    search.clear();
  }

  @Override
  public int getTickInterval() {
    return tickIntervalMS;
  }

  public int getLeafDecayDistance() {
    return leafDecayDistance;
  }

  public int getLeafDecayRadius() {
    return leafDecayRadius;
  }

  public boolean isLeaf(BlockData block) {
    return block instanceof Leaves;
  }

  public boolean shouldDecay(BlockData block) {
    return isLeaf(block)
        && (forceDecayPersistent || !((Leaves) block).isPersistent())
        && ((Leaves) block).getDistance() >= getLeafDecayDistance();
  }

  public void addBlockForDecay(IBlock block, BlockData data) {
    Block b = block.block();

    if (shouldDecay(b.getBlockData())) {
      if (playSounds && Math.random() < soundChance) {
        // audience delivery: spigot Player has no playSound(net.kyori Sound)
        b.getWorld().getPlayers().forEach(player ->
            React.audiences().player(player).playSound(Sound.sound(
                Key.key(decaySound),
                Sound.Source.BLOCK,
                (float) soundVolume,
                (float) soundPitch
            ), b.getLocation().getX(), b.getLocation().getY(), b.getLocation().getZ())
        );
      }

      if (fastBlockChanges) {
        FastWorld.breakNaturally(b);
      } else {
        b.breakNaturally();
      }
    }
  }

  public ChunkSnapshot snap(Chunk c) {
    return snapshot.get(new IChunk(c.getWorld(), c.getX(), c.getZ()), (k) -> k.chunk().getChunkSnapshot(true, false, false));
  }

  public BlockData data(World world, int x, int y, int z) {
    return snap(world.getChunkAt(x >> 4, z >> 4)).getBlockData(x & 15, y, z & 15);
  }

  public void decay(Block block) {
    search.add(block);
  }

  public void checkDecay(Block block) {
    if (isLeaf(block.getBlockData())) {
      decay(block);
    }
  }

  /**
   * Check for leaf decay on leaf decay event
   */
  @EventHandler(priority = EventPriority.MONITOR)
  public void on(LeavesDecayEvent e) {
    if (cooldownLatch.flip()) {
      checkDecay(e.getBlock());
    }
  }

  /**
   * Check for leaf decay on block break
   */
  @EventHandler(priority = EventPriority.MONITOR)
  public void onBlockBreak(BlockBreakEvent e) {
    Block block = e.getBlock();
    BlockData blockData = block.getBlockData();
    if (blockData instanceof Leaves leaves) {
      if (leaves.isPersistent()) {
        checkDecay(e.getBlock());
      }
    }
  }

  @Override
  public void onTick() {
    PrecisionStopwatch p = PrecisionStopwatch.start();

    try {
      AtomicInteger i = new AtomicInteger();
      AtomicInteger j = new AtomicInteger();
      AtomicInteger k = new AtomicInteger();
      for (Block block : search) {
        if (p.getMilliseconds() > maxAsyncMS) {
          break;
        }

        var location = block.getLocation().clone();
        J.s(location, () -> {
          Block root = location.getBlock();
          PrecisionStopwatch px = PrecisionStopwatch.start();
          for (i.set(root.getX() - getLeafDecayRadius()); i.get() < root.getX() + getLeafDecayRadius(); i.getAndIncrement()) {
            for (j.set(root.getY() - getLeafDecayRadius()); j.get() < root.getY() + getLeafDecayRadius(); j.getAndIncrement()) {
              for (k.set(root.getZ() - getLeafDecayRadius()); k.get() < root.getZ() + getLeafDecayRadius(); k.getAndIncrement()) {
                if (px.getMilliseconds() > maxSyncSpikeMS) {
                  return;
                }

                BlockData d = data(root.getWorld(), i.get(), j.get(), k.get());
                if (shouldDecay(d)) {
                  addBlockForDecay(new IBlock(root.getWorld(), i.get(), j.get(), k.get()), d);
                }
              }

            }
          }
        }, 0);
      }

      search.clear();
    } catch (Throwable ex) {
      search.clear();
      React.warn("Fast leaf decay scan failed: " + ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      React.reportError(ex);
    }
  }

  @EqualsAndHashCode
  @AllArgsConstructor
  @Data
  public static class IBlock {
    @art.arcane.react.util.project.config.ConfigDoc(value = "Runtime reference field for world used by fast leaf decay.", impact = "This value is typically populated from live game objects and not intended for manual editing.")
    private final World world;
    @art.arcane.react.util.project.config.ConfigDoc(value = "X-axis coordinate used by fast leaf decay internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int x;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Y-axis coordinate used by fast leaf decay internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int y;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Z-axis coordinate used by fast leaf decay internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int z;

    public Block block() {
      return world.getBlockAt(x, y, z);
    }

    public IChunk chunk() {
      return new IChunk(world, x >> 4, z >> 4);
    }
  }

  @EqualsAndHashCode
  @AllArgsConstructor
  @Data
  public static class IChunk {
    @art.arcane.react.util.project.config.ConfigDoc(value = "Runtime reference field for world used by fast leaf decay.", impact = "This value is typically populated from live game objects and not intended for manual editing.")
    private final World world;
    @art.arcane.react.util.project.config.ConfigDoc(value = "X-axis coordinate used by fast leaf decay internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int x;
    @art.arcane.react.util.project.config.ConfigDoc(value = "Z-axis coordinate used by fast leaf decay internal tracking.", impact = "This is internal state data and should not normally be changed manually.")
    private final int z;

    public Chunk chunk() {
      return world.getChunkAt(x, z);
    }
  }
}
