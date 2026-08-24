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

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.feature.perworld.PerWorldPressure;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Item Backpressure feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureItemBackpressure extends ReactFeature {
  private static final int MAX_INDEXED_ITEMS = 65_536;

  public static final String ID = "item-backpressure";
  private transient final AtomicBoolean itemScanQueued = new AtomicBoolean(false);
  private transient final AtomicInteger nextPaperWorld = new AtomicInteger(0);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient final Map<UUID, IndexedItem> indexedItems = new ConcurrentHashMap<>();
  private transient final Queue<UUID> indexedItemOrder = new ConcurrentLinkedQueue<>();
  private transient final Consumer<Entity> entityTickListener = this::indexItem;
  private transient final Object lifecycleMutationLock = new Object();
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for item backpressure in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 1000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Tick-time threshold for trigger time in item backpressure (milliseconds).", impact = "Higher values delay activation or exit; lower values make this threshold easier to cross.")
  private double triggerTickTimeMS = 60;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Trigger threshold for trigger entity count in item backpressure.", impact = "Higher values trigger mitigation later; lower values trigger earlier and more aggressively.")
  private int triggerEntityCount = 5000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum entity candidates scanned per cycle by item backpressure.", impact = "Higher values inspect drops faster at greater per-cycle cost; lower values impose a tighter fixed work bound.")
  private int maxItemsScannedPerWorld = 220;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum items removed allowed per cycle in item backpressure.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxItemsRemovedPerCycle = 90;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Minimum item age ticks required by item backpressure.", impact = "Higher values require stronger signals before action; lower values make this condition easier to satisfy.")
  private int minimumItemAgeTicks = 200;
  @art.arcane.react.util.project.config.ConfigDoc(value = "No player radius used by item backpressure (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double noPlayerRadius = 40;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Protects named items from item backpressure enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
  private boolean protectNamedItems = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Protects valuables from item backpressure enforcement.", impact = "Enable this to keep matching targets safe; disable it to make them eligible for handling.")
  private boolean protectValuables = true;
  private Set<Material> valuables = Set.of(
      Material.NETHERITE_INGOT,
      Material.NETHERITE_SCRAP,
      Material.NETHER_STAR,
      Material.DIAMOND,
      Material.ELYTRA,
      Material.TOTEM_OF_UNDYING
  );
  private transient volatile EntityController registeredController;
  private transient volatile boolean active;

  public FeatureItemBackpressure() {
    super(ID);
  }

  static boolean shouldThrottle(double tickTimeMs, double triggerTickTimeMs, double entityCount, double triggerEntityCount) {
    return tickTimeMs >= triggerTickTimeMs || entityCount >= triggerEntityCount;
  }

  @Override
  public void onActivate() {
    synchronized (lifecycleMutationLock) {
      lifecycleGeneration.incrementAndGet();
      itemScanQueued.set(false);
      nextPaperWorld.set(0);
      indexedItems.clear();
      indexedItemOrder.clear();
      active = true;
    }

    if (J.isFoliaThreading()) {
      EntityController controller = React.controller(EntityController.class);
      if (controller != null) {
        controller.registerEntityTickListener(EntityType.ITEM, entityTickListener);
        registeredController = controller;
      }
    }
  }

  @Override
  public void onDeactivate() {
    synchronized (lifecycleMutationLock) {
      active = false;
      lifecycleGeneration.incrementAndGet();
      itemScanQueued.set(false);
    }

    EntityController controller = registeredController;
    registeredController = null;
    if (controller != null) {
      controller.unregisterEntityTickListener(entityTickListener);
    }
    indexedItems.clear();
    indexedItemOrder.clear();
  }

  @Override
  public int getTickInterval() {
    return Math.max(50, tickIntervalMS);
  }

  @Override
  public void onTick() {
    if (!active) {
      return;
    }

    boolean globalTrigger = shouldApplyBackpressure();
    long generation = lifecycleGeneration.get();
    if (!itemScanQueued.compareAndSet(false, true)) {
      return;
    }

    if (J.isFoliaThreading()) {
      scheduleFoliaScan(globalTrigger, generation);
      return;
    }

    J.s(() -> {
      try {
        removeRemoteItems(globalTrigger, generation);
      } finally {
        finishScan(generation);
      }
    });
  }

  private boolean shouldApplyBackpressure() {
    double tickTime = sample(SamplerTickTime.ID, 0D);
    if (tickTime >= triggerTickTimeMS) {
      return true;
    }

    double entityCount = sample(SamplerEntities.ID, 0D);
    return shouldThrottle(tickTime, triggerTickTimeMS, entityCount, triggerEntityCount);
  }

  private void removeRemoteItems(boolean includeAllWorlds, long generation) {
    if (!isCurrent(generation)) {
      return;
    }

    List<World> worlds = Bukkit.getWorlds();
    if (worlds.isEmpty()) {
      return;
    }

    int remainingScans = Math.max(1, maxItemsScannedPerWorld);
    int remainingRemovals = Math.max(1, maxItemsRemovedPerCycle);
    int worldStart = Math.floorMod(nextPaperWorld.getAndIncrement(), worlds.size());
    for (int worldOffset = 0; worldOffset < worlds.size(); worldOffset++) {
      if (!isCurrent(generation) || remainingScans <= 0 || remainingRemovals <= 0) {
        return;
      }

      World world = worlds.get((worldStart + worldOffset) % worlds.size());
      if (!includeAllWorlds && !PerWorldPressure.get(world).isPressure()) {
        continue;
      }

      List<Entity> entities = WorldEntitySnapshots.next(world, remainingScans);
      if (entities.isEmpty()) {
        continue;
      }

      for (Entity entity : entities) {
        if (!isCurrent(generation)) {
          return;
        }
        remainingScans--;
        if (!(entity instanceof Item item) || !canRemove(item)) {
          continue;
        }

        if (!removeOwnedItem(item, generation)) {
          return;
        }
        remainingRemovals--;
        if (remainingRemovals <= 0) {
          return;
        }
      }
    }
  }

  private void scheduleFoliaScan(boolean includeAllWorlds, long generation) {
    if (!isCurrent(generation)) {
      finishScan(generation);
      return;
    }

    int scanBudget = Math.max(1, maxItemsScannedPerWorld);
    AtomicInteger pending = new AtomicInteger(1);
    AtomicInteger remainingRemovals = new AtomicInteger(Math.max(1, maxItemsRemovedPerCycle));
    for (int i = 0; i < scanBudget; i++) {
      UUID entityId = indexedItemOrder.poll();
      if (entityId == null) {
        break;
      }

      IndexedItem candidate = indexedItems.get(entityId);
      if (candidate == null || candidate.generation != generation) {
        if (candidate != null) {
          indexedItems.remove(entityId, candidate);
        }
        continue;
      }

      Item item = candidate.reference.get();
      if (item == null) {
        indexedItems.remove(entityId, candidate);
        continue;
      }

      indexedItemOrder.offer(entityId);
      pending.incrementAndGet();
      AtomicBoolean completionClaimed = new AtomicBoolean(false);
      Runnable completed = () -> {
        if (completionClaimed.compareAndSet(false, true)) {
          finishFoliaCandidate(pending, generation);
        }
      };
      Runnable retired = () -> {
        indexedItems.remove(entityId, candidate);
        completed.run();
      };
      try {
        boolean scheduled = J.runEntity(
            item,
            () -> {
              try {
                removeIndexedItem(candidate, item, includeAllWorlds, remainingRemovals, generation);
              } finally {
                completed.run();
              }
            },
            0,
            retired
        );
        if (!scheduled) {
          retired.run();
        }
      } catch (Throwable failure) {
        retired.run();
        React.reportError(failure);
      }
    }
    finishFoliaCandidate(pending, generation);
  }

  private void removeIndexedItem(
      IndexedItem candidate,
      Item item,
      boolean includeAllWorlds,
      AtomicInteger remainingRemovals,
      long generation
  ) {
    if (!isCurrent(generation)
        || !J.isOwnedByCurrentRegion(item)) {
      return;
    }

    if (!includeAllWorlds && !PerWorldPressure.get(item.getWorld()).isPressure()) {
      return;
    }

    if (!canRemove(item) || !claimBudget(remainingRemovals)) {
      return;
    }

    if (removeOwnedItem(item, generation)) {
      indexedItems.remove(candidate.entityId, candidate);
    }
  }

  private boolean removeOwnedItem(Item item, long generation) {
    synchronized (lifecycleMutationLock) {
      if (!isCurrent(generation)) {
        return false;
      }

      item.remove();
      return true;
    }
  }

  private void indexItem(Entity entity) {
    if (!(entity instanceof Item item)) {
      return;
    }

    long generation = lifecycleGeneration.get();
    if (!isCurrent(generation)) {
      return;
    }

    UUID entityId = item.getUniqueId();
    IndexedItem replacement = new IndexedItem(entityId, generation, new WeakReference<>(item));
    IndexedItem existing = indexedItems.putIfAbsent(entityId, replacement);
    if (existing == null) {
      if (indexedItems.size() > MAX_INDEXED_ITEMS) {
        indexedItems.remove(entityId, replacement);
        return;
      }
      indexedItemOrder.offer(entityId);
      return;
    }

    if (existing.generation != generation || existing.reference.get() != item) {
      indexedItems.replace(entityId, existing, replacement);
    }
  }

  private boolean claimBudget(AtomicInteger budget) {
    int current = budget.get();
    while (current > 0) {
      if (budget.compareAndSet(current, current - 1)) {
        return true;
      }
      current = budget.get();
    }
    return false;
  }

  private void finishFoliaCandidate(AtomicInteger pending, long generation) {
    if (pending.decrementAndGet() == 0) {
      finishScan(generation);
    }
  }

  private void finishScan(long generation) {
    if (generation == lifecycleGeneration.get()) {
      itemScanQueued.set(false);
    }
  }

  private boolean isCurrent(long generation) {
    return active && generation == lifecycleGeneration.get();
  }

  private boolean canRemove(Item item) {
    if (item == null) {
      return false;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(item)) {
      return false;
    }

    if (item.isDead() || item.getTicksLived() < minimumItemAgeTicks) {
      return false;
    }

    if (protectNamedItems && item.getCustomName() != null && !item.getCustomName().isBlank()) {
      return false;
    }

    if (protectValuables && valuables.contains(item.getItemStack().getType())) {
      return false;
    }

    return !React.hasNearbyPlayer(item.getLocation(), noPlayerRadius);
  }

  private static final class IndexedItem {
    private final UUID entityId;
    private final long generation;
    private final WeakReference<Item> reference;

    private IndexedItem(UUID entityId, long generation, WeakReference<Item> reference) {
      this.entityId = entityId;
      this.generation = generation;
      this.reference = reference;
    }
  }
}
