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
import art.arcane.react.content.feature.perworld.PerWorldPressure;
import art.arcane.react.content.sampler.SamplerEntities;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.platform.EntityEventHost;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ItemHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;

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
  private static final String ITEM_TYPE = "ITEM";

  public static final String ID = "item-backpressure";
  private transient final AtomicBoolean itemScanQueued = new AtomicBoolean(false);
  private transient final AtomicInteger nextPaperWorld = new AtomicInteger(0);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient final Map<UUID, IndexedItem> indexedItems = new ConcurrentHashMap<>();
  private transient final Queue<UUID> indexedItemOrder = new ConcurrentLinkedQueue<>();
  private transient final Consumer<GameEntity> entityTickListener = this::indexItem;
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
  private Set<String> valuables = Set.of(
      "NETHERITE_INGOT",
      "NETHERITE_SCRAP",
      "NETHER_STAR",
      "DIAMOND",
      "ELYTRA",
      "TOTEM_OF_UNDYING"
  );
  private transient Subscription ticks = Subscription.NONE;
  private transient volatile ItemHost items;
  private transient volatile EntityEventHost events;
  private transient volatile WorldHost worlds;
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
      ReactPlatform platform = ReactPlatforms.getOrNull();
      EntityHost host = platform == null ? null : platform.entityHost().orElse(null);
      if (host != null) {
        ticks = host.onTick(List.of(ITEM_TYPE), entityTickListener);
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

    ticks.cancel();
    ticks = Subscription.NONE;
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

    WorldHost worldHost = worlds();
    ItemHost host = items();
    if (worldHost == null || host == null) {
      return;
    }
    List<ReactWorld> worlds = worldHost.worlds();
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

      ReactWorld world = worlds.get((worldStart + worldOffset) % worlds.size());
      if (!includeAllWorlds && !PerWorldPressure.get(world.id()).isPressure()) {
        continue;
      }

      List<GameEntity> entities = host.scan(world, remainingScans);
      if (entities.isEmpty()) {
        continue;
      }

      for (GameEntity entity : entities) {
        if (!isCurrent(generation)) {
          return;
        }
        remainingScans--;
        if (!host.isItem(entity) || !canRemove(entity)) {
          continue;
        }

        if (!removeOwnedItem(entity, generation)) {
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

    ItemHost host = items();
    if (host == null) {
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

      Object handle = candidate.reference.get();
      if (handle == null) {
        indexedItems.remove(entityId, candidate);
        continue;
      }

      GameEntity item = host.entity(handle);
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
            handle,
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
        ReactPlatform platform = ReactPlatforms.getOrNull();
        if (platform != null) {
          platform.reportError(failure);
        }
      }
    }
    finishFoliaCandidate(pending, generation);
  }

  private void removeIndexedItem(
      IndexedItem candidate,
      GameEntity item,
      boolean includeAllWorlds,
      AtomicInteger remainingRemovals,
      long generation
  ) {
    if (!isCurrent(generation)
        || !J.isOwnedByCurrentRegion(item.handle())) {
      return;
    }

    GameLocation location = item.location();
    if (!includeAllWorlds && (location == null || !PerWorldPressure.get(location.worldId()).isPressure())) {
      return;
    }

    if (!canRemove(item) || !claimBudget(remainingRemovals)) {
      return;
    }

    if (removeOwnedItem(item, generation)) {
      indexedItems.remove(candidate.entityId, candidate);
    }
  }

  private boolean removeOwnedItem(GameEntity item, long generation) {
    synchronized (lifecycleMutationLock) {
      if (!isCurrent(generation)) {
        return false;
      }

      ItemHost host = items();
      if (host != null) {
        host.discard(item);
      }
      return true;
    }
  }

  private void indexItem(GameEntity item) {
    long generation = lifecycleGeneration.get();
    if (!isCurrent(generation)) {
      return;
    }

    UUID entityId = item.id();
    Object handle = item.handle();
    IndexedItem replacement = new IndexedItem(entityId, generation, new WeakReference<>(handle));
    IndexedItem existing = indexedItems.putIfAbsent(entityId, replacement);
    if (existing == null) {
      if (indexedItems.size() > MAX_INDEXED_ITEMS) {
        indexedItems.remove(entityId, replacement);
        return;
      }
      indexedItemOrder.offer(entityId);
      return;
    }

    if (existing.generation != generation || existing.reference.get() != handle) {
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

  private boolean canRemove(GameEntity item) {
    ItemHost host = items();
    EntityEventHost checks = events();
    if (item == null || host == null || checks == null) {
      return false;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(item.handle())) {
      return false;
    }

    if (!item.isValid() || item.ticksLived() < minimumItemAgeTicks) {
      return false;
    }

    String customName = host.customName(item);
    if (protectNamedItems && customName != null && !customName.isBlank()) {
      return false;
    }

    if (protectValuables && valuables.contains(host.material(item))) {
      return false;
    }

    return !checks.nearPlayer(item, noPlayerRadius);
  }

  private ItemHost items() {
    ItemHost host = items;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.itemHost().orElse(null);
      items = host;
    }
    return host;
  }

  private EntityEventHost events() {
    EntityEventHost host = events;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.entityEventHost().orElse(null);
      events = host;
    }
    return host;
  }

  private WorldHost worlds() {
    WorldHost host = worlds;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.worldHost().orElse(null);
      worlds = host;
    }
    return host;
  }

  private static final class IndexedItem {
    private final UUID entityId;
    private final long generation;
    private final WeakReference<Object> reference;

    private IndexedItem(UUID entityId, long generation, WeakReference<Object> reference) {
      this.entityId = entityId;
      this.generation = generation;
      this.reference = reference;
    }
  }
}
