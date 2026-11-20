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

import art.arcane.react.api.feature.FeatureIntegrity;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ItemHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.ChronoLatch;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
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

/**
 * Stacks items into bundles
 */
@art.arcane.react.util.project.config.ConfigDescription("Configuration for Item Super Stacker feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureItemSuperStacker extends ReactFeature implements FeatureIntegrity {
  public static final String ID = "item-super-stacker";
  private static final long GLOSS_REPUBLISH_INTERVAL_MS = 30_000L;
  private static final long GLOSS_CACHE_SWEEP_INTERVAL_MS = 60_000L;
  private static final int MAX_INDEXED_ITEMS = 65_536;
  private static final int MAX_QUEUED_BUCKETS = 4096;
  private static final int MAX_BUCKET_SUBMISSIONS_PER_TICK = 64;
  private static final int MAX_CANDIDATES_PER_BUCKET_PASS = 65;
  private static final List<String> ITEM_TYPES = List.of("ITEM");
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum items allowed per bundle in item super stacker.", impact = "Higher values permit larger bursts before control engages; lower values clamp spikes sooner.")
  private int maxItemsPerBundle = 64;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Search radius used by item super stacker (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double searchRadius = 3;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Merges ordinary matching item stacks immediately instead of waiting for Minecraft's merge timer.", impact = "This is especially effective for dense mining drops such as cobblestone and preserves the normal maximum stack size.")
  private boolean mergeMatchingStacks = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum nearby item entities consolidated by one item-super-stacker pass.", impact = "Higher values collapse dense drop clusters sooner; runtime use is bounded from 1 to 64 to cap work per pass.")
  private int maxMergesPerPass = 16;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Ticks after an item spawns before React runs its first cluster merge.", impact = "One tick lets Bukkit finish adding the item entity; runtime use is bounded from 1 to 20 ticks.")
  private int spawnMergeDelayTicks = 1;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Gloss vertical-label header for React bundles.", impact = "Gloss replaces the total token and renders ampersand color codes.")
  private String glossBundleHeaderFormat = "&eBundle &8(&e{total} items&8)";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Gloss vertical-label line for each material in React bundles.", impact = "Gloss replaces the count and type tokens and renders ampersand color codes.")
  private String glossBundleEntryFormat = "&7- &f{count}x {type}";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Gloss vertical-label suffix for hidden React bundle materials.", impact = "Gloss replaces the remaining-material token and renders ampersand color codes.")
  private String glossBundleMoreFormat = "&8+{remaining} more";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum material entries shown in a React bundle's Gloss label.", impact = "Additional material types collapse into the configured remainder line. Values are clamped from 1 to 10.")
  private int glossBundleEntryLimit = 3;
  private transient ChronoLatch cl = new ChronoLatch(10);
  private transient final Consumer<GameEntity> entityTickListener = this::onItemTick;
  private transient final Map<UUID, Long> glossRefreshes = new ConcurrentHashMap<>();
  private transient final Map<UUID, IndexedItem> indexedItems = new ConcurrentHashMap<>();
  private transient final Map<ItemBucketKey, Map<UUID, IndexedItem>> itemBuckets = new ConcurrentHashMap<>();
  private transient final Map<ItemBucketKey, Long> queuedBuckets = new ConcurrentHashMap<>();
  private transient final Queue<ItemBucketKey> bucketQueue = new ConcurrentLinkedQueue<>();
  private transient final Map<ItemBucketKey, BucketFlight> bucketFlights = new ConcurrentHashMap<>();
  private transient final AtomicLong lifecycleGeneration = new AtomicLong();
  private transient final Object itemIndexLock = new Object();
  private transient final Object queueLock = new Object();
  private transient final Object subscriptionLock = new Object();
  private transient final AtomicInteger anchorRotation = new AtomicInteger();
  private transient final AtomicInteger candidateRotation = new AtomicInteger();
  private transient final List<Subscription> integrityHooks = new ArrayList<>();
  private transient final List<Subscription> mergeHooks = new ArrayList<>();
  private transient volatile ItemHost items;
  private transient volatile long lastGlossCacheSweepMs;
  private transient volatile boolean active;

  public FeatureItemSuperStacker() {
    super(ID);
  }

  FeatureItemSuperStacker(ItemHost items) {
    super(ID);
    this.items = items;
  }

  public boolean isSuperStack(GameEntity item) {
    ItemHost host = items();
    return host != null && host.isSuperStack(item);
  }

  public void effectMerge(GameEntity item, GameEntity into) {
    ItemHost host = items();
    if (host != null) {
      host.mergeEffect(item, into, effectiveSearchRadius(), cl.flip());
    }
  }

  public void mergeWithNearbyItems(GameEntity item) {
    if (!active) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(item.handle())) {
      J.runEntity(item.handle(), () -> mergeWithNearbyItems(item), 0, null);
      return;
    }

    ItemHost host = items();
    long generation = lifecycleGeneration.get();
    if (host == null || !isActive(generation) || !host.isLive(item)) {
      return;
    }
    ItemBucketKey bucketKey = indexItem(host, item);
    if (bucketKey != null) {
      mergeBucketOwned(host, item, bucketKey, generation);
    }
  }

  @Override
  public void onTick() {
    long generation = lifecycleGeneration.get();
    ItemHost host = items();
    if (host == null || !isActive(generation)) {
      return;
    }

    int available = Math.min(MAX_BUCKET_SUBMISSIONS_PER_TICK, bucketQueue.size());
    long now = System.currentTimeMillis();
    for (int processed = 0; processed < available; processed++) {
      ItemBucketKey bucketKey = bucketQueue.poll();
      if (bucketKey == null) {
        return;
      }

      Long dueAt = queuedBuckets.get(bucketKey);
      if (dueAt == null) {
        continue;
      }
      if (dueAt > now) {
        bucketQueue.offer(bucketKey);
        continue;
      }
      if (!queuedBuckets.remove(bucketKey, dueAt)) {
        continue;
      }

      BucketFlight flight = new BucketFlight(bucketKey, generation);
      if (bucketFlights.putIfAbsent(bucketKey, flight) != null) {
        queueBucket(bucketKey, 0);
        continue;
      }
      GameEntity anchor = findAnchor(host, bucketKey);
      if (anchor == null) {
        completeBucketFlight(flight, false);
        continue;
      }
      scheduleBucketFlight(host, anchor, flight);
    }
  }

  private void scheduleBucketFlight(ItemHost host, GameEntity anchor, BucketFlight flight) {
    Runnable retired = () -> completeBucketFlight(flight, true);
    boolean scheduled;
    try {
      scheduled = J.runEntity(
          anchor.handle(),
          () -> executeBucketFlight(host, anchor, flight),
          0,
          retired
      );
    } catch (RuntimeException | Error failure) {
      reportError(failure);
      retired.run();
      return;
    }
    if (!scheduled) {
      retired.run();
    }
  }

  private void executeBucketFlight(ItemHost host, GameEntity anchor, BucketFlight flight) {
    boolean continueWork = false;
    try {
      if (!isActive(flight.generation)
          || (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(anchor.handle()))
          || !host.isLive(anchor)) {
        removeIndexedItem(anchor);
        continueWork = true;
      } else {
        ItemBucketKey currentKey = indexItem(host, anchor);
        if (currentKey != null && !currentKey.equals(flight.bucketKey)) {
          queueBucket(currentKey, 0);
        } else if (currentKey != null) {
          continueWork = mergeBucketOwned(host, anchor, currentKey, flight.generation);
        }
      }
    } catch (Throwable failure) {
      reportError(failure);
      continueWork = true;
    } finally {
      completeBucketFlight(flight, continueWork);
    }
  }

  private boolean mergeBucketOwned(ItemHost host, GameEntity anchor, ItemBucketKey bucketKey, long generation) {
    boolean folia = J.isFoliaThreading();
    List<GameEntity> candidates = collectCandidates(
        host,
        bucketKey,
        anchor,
        Math.min(MAX_CANDIDATES_PER_BUCKET_PASS, effectiveMaxMergesPerPass() + 1)
    );
    GameEntity collector = anchor;
    int merged = 0;
    boolean effectPlayed = false;
    if (!host.isLive(collector)) {
      removeIndexedItem(collector);
      return false;
    }
    int maxBundle = effectiveMaxItemsPerBundle();
    for (GameEntity target : candidates) {
      if (merged >= effectiveMaxMergesPerPass() || !isActive(generation)) {
        break;
      }
      if (target.handle() == collector.handle()) {
        continue;
      }
      if (folia && !J.isOwnedByCurrentRegion(target.handle())) {
        continue;
      }
      if (!host.isLive(target)) {
        removeIndexedItem(target);
        continue;
      }
      if (!withinMergeRadius(collector, target)) {
        continue;
      }
      if (exceedsBundleCapacity(host, collector, target, maxBundle)) {
        continue;
      }

      GameEntity survivor = mergePair(host, collector, target, !effectPlayed);
      if (survivor != null) {
        collector = survivor;
        effectPlayed = true;
        merged++;
      }
    }

    if (merged >= effectiveMaxMergesPerPass() && host.isLive(collector)) {
      ItemBucketKey continuationKey = indexItem(host, collector);
      if (continuationKey != null) {
        queueBucket(continuationKey, 0);
      }
    }
    return false;
  }

  private boolean withinMergeRadius(GameEntity source, GameEntity target) {
    GameLocation sourceLocation = source.location();
    GameLocation targetLocation = target.location();
    if (sourceLocation == null || targetLocation == null || !sourceLocation.worldId().equals(targetLocation.worldId())) {
      return false;
    }

    double radius = effectiveSearchRadius();
    return Math.abs(sourceLocation.x() - targetLocation.x()) <= radius
        && Math.abs(sourceLocation.y() - targetLocation.y()) <= radius
        && Math.abs(sourceLocation.z() - targetLocation.z()) <= radius;
  }

  private boolean exceedsBundleCapacity(ItemHost host, GameEntity collector, GameEntity target, int maxBundle) {
    int collectorBundle = cachedBundleCount(collector);
    int targetBundle = cachedBundleCount(target);
    if (collectorBundle <= 0 && targetBundle <= 0) {
      return false;
    }
    int collectorCount = collectorBundle > 0 ? collectorBundle : host.amount(collector);
    int targetCount = targetBundle > 0 ? targetBundle : host.amount(target);
    return collectorCount + targetCount > maxBundle;
  }

  private int cachedBundleCount(GameEntity item) {
    IndexedItem indexed = indexedFor(item);
    return indexed == null ? -1 : indexed.bundleCount;
  }

  private void recordBundleCount(GameEntity item, int bundleCount) {
    IndexedItem indexed = indexedFor(item);
    if (indexed != null) {
      indexed.bundleCount = bundleCount;
    }
  }

  private IndexedItem indexedFor(GameEntity item) {
    UUID itemId = item.id();
    if (itemId == null) {
      return null;
    }
    IndexedItem indexed = indexedItems.get(itemId);
    return indexed == null || indexed.reference.get() != item.handle() ? null : indexed;
  }

  private GameEntity mergePair(ItemHost host, GameEntity source, GameEntity target, boolean playEffect) {
    int sourceBundle = cachedBundleCount(source);
    int targetBundle = cachedBundleCount(target);
    int sourceAmount = host.amount(source);
    int targetAmount = host.amount(target);
    if (host.bundle(source, target, effectiveMaxItemsPerBundle())) {
      int bundledCount = (sourceBundle > 0 ? sourceBundle : sourceAmount) + (targetBundle > 0 ? targetBundle : targetAmount);
      if (playEffect) {
        effectMerge(source, target);
      }
      removeTrackedItem(source);
      recordBundleCount(target, bundledCount);
      refreshGloss(target);
      return target;
    }
    if (!mergeMatchingStacks || !host.isSimilar(source, target)) {
      return null;
    }

    int capacity = Math.max(0, host.maxStackSize(target) - targetAmount);
    int transferred = Math.min(sourceAmount, capacity);
    if (transferred <= 0) {
      return null;
    }
    if (playEffect) {
      effectMerge(source, target);
    }

    host.setAmount(target, targetAmount + transferred);
    refreshGloss(target);
    if (transferred == sourceAmount) {
      removeTrackedItem(source);
      return target;
    }

    host.setAmount(source, sourceAmount - transferred);
    refreshGloss(source);
    return source;
  }

  void onPlayerPickup(ItemHost.PlayerPickup pickup) {
    GameEntity item = pickup.item();
    if (!isSuperStack(item)) {
      return;
    }

    pickup.cancel();
    pickup.giveContents();
    removeTrackedItem(item);
    if (cl.flip()) {
      pickup.playUnpackSound();
    }
  }

  void onSlotClick(ItemHost.SlotClick click) {
    if (click.isSuperStack()) {
      click.unpack();
    }
  }

  void onContainerPickup(ItemHost.ContainerPickup pickup) {
    GameEntity item = pickup.item();
    if (!isSuperStack(item)) {
      return;
    }

    pickup.cancel();
    if (pickup.insertContents() == ItemHost.Insertion.KEPT) {
      recordBundleCount(item, pickup.keptCount());
      refreshGloss(item);
      return;
    }
    removeTrackedItem(item);
  }

  void onSpawn(GameEntity item) {
    if (active) {
      queueItem(item, effectiveSpawnMergeDelayTicks());
    }
  }

  void onRemove(GameEntity item) {
    removeIndexedItem(item);
  }

  void onLoad(GameEntity item) {
    if (!active) {
      return;
    }
    queueItem(item, 0);
    if (isSuperStack(item)) {
      refreshGloss(item);
    }
  }

  void onDamage(ItemHost.Damage damage) {
    if (!damage.isVoid() && isSuperStack(damage.item())) {
      damage.cancel();
    }
  }

  @Override
  public void onAttach() {
    ItemHost host = items();
    if (host == null) {
      return;
    }
    synchronized (subscriptionLock) {
      if (!integrityHooks.isEmpty()) {
        return;
      }
      integrityHooks.add(host.onPlayerPickup(this::onPlayerPickup));
      integrityHooks.add(host.onSlotClick(this::onSlotClick));
      integrityHooks.add(host.onContainerPickup(this::onContainerPickup));
      integrityHooks.add(host.onDamage(this::onDamage));
      integrityHooks.add(host.onRemove(this::onRemove));
    }
  }

  @Override
  public void onDetach() {
    cancel(integrityHooks);
  }

  @Override
  public void onActivate() {
    lifecycleGeneration.incrementAndGet();
    clearMergeIndex();
    active = true;
    glossRefreshes.clear();
    ItemHost host = items();
    ReactPlatform platform = ReactPlatforms.getOrNull();
    EntityHost entities = platform == null ? null : platform.entityHost().orElse(null);
    synchronized (subscriptionLock) {
      if (entities != null) {
        mergeHooks.add(entities.onTick(ITEM_TYPES, entityTickListener));
      }
      if (host != null) {
        mergeHooks.add(host.onSpawn(this::onSpawn));
        mergeHooks.add(host.onLoad(this::onLoad));
      }
    }
  }

  @Override
  public void onDeactivate() {
    active = false;
    lifecycleGeneration.incrementAndGet();
    clearMergeIndex();
    glossRefreshes.clear();
    cancel(mergeHooks);
  }

  private void cancel(List<Subscription> subscriptions) {
    List<Subscription> open;
    synchronized (subscriptionLock) {
      open = new ArrayList<>(subscriptions);
      subscriptions.clear();
    }
    for (Subscription subscription : open) {
      subscription.cancel();
    }
  }

  private void onItemTick(GameEntity item) {
    ItemHost host = active ? items() : null;
    if (host == null || !host.isLive(item)) {
      return;
    }
    queueItem(item, 0);
    if (host.isLive(item) && host.isSuperStack(item)) {
      refreshGlossIfDue(item);
    }
  }

  private void refreshGlossIfDue(GameEntity item) {
    long now = System.currentTimeMillis();
    Long refreshedAt = glossRefreshes.get(item.id());
    if (refreshedAt != null && now - refreshedAt < GLOSS_REPUBLISH_INTERVAL_MS) {
      return;
    }
    refreshGloss(item, now);
    sweepGlossRefreshes(now);
  }

  private void refreshGloss(GameEntity item) {
    refreshGloss(item, System.currentTimeMillis());
  }

  private void refreshGloss(GameEntity item, long now) {
    ItemHost host = items();
    if (host != null && host.refreshLabel(item, glossBundleHeaderFormat, glossBundleEntryFormat,
        glossBundleMoreFormat, glossBundleEntryLimit)) {
      glossRefreshes.put(item.id(), now);
    }
  }

  private void sweepGlossRefreshes(long now) {
    if (now - lastGlossCacheSweepMs < GLOSS_CACHE_SWEEP_INTERVAL_MS) {
      return;
    }
    lastGlossCacheSweepMs = now;
    glossRefreshes.entrySet().removeIf(entry -> now - entry.getValue() >= GLOSS_CACHE_SWEEP_INTERVAL_MS);
  }

  private void queueItem(GameEntity item, int delayTicks) {
    ItemHost host = active ? items() : null;
    if (host == null) {
      return;
    }
    ItemBucketKey bucketKey = indexItem(host, item);
    if (bucketKey != null) {
      queueBucket(bucketKey, delayTicks);
    }
  }

  private ItemBucketKey indexItem(ItemHost host, GameEntity item) {
    UUID itemId = item.id();
    GameLocation location = item.location();
    if (itemId == null || location == null) {
      return null;
    }

    UUID worldId = location.worldId();
    Object handle = item.handle();
    long chunkKey = packChunk(location.chunkX(), location.chunkZ());
    IndexedItem current = indexedItems.get(itemId);
    boolean sameItem = current != null && current.reference.get() == handle;
    if (sameItem && current.bucketKey.chunkKey == chunkKey && current.bucketKey.worldId.equals(worldId)) {
      return current.bucketKey;
    }

    ItemBucketKey bucketKey = new ItemBucketKey(worldId, chunkKey);
    int bundleCount = sameItem ? current.bundleCount : host.superStackCount(item);
    synchronized (itemIndexLock) {
      IndexedItem previous = indexedItems.get(itemId);
      if (previous == null && indexedItems.size() >= MAX_INDEXED_ITEMS) {
        return null;
      }

      IndexedItem indexed = new IndexedItem(itemId, bucketKey, new WeakReference<>(handle), bundleCount);
      indexedItems.put(itemId, indexed);
      if (previous != null && !previous.bucketKey.equals(bucketKey)) {
        removeFromBucket(previous);
      }
      itemBuckets.computeIfAbsent(bucketKey, ignored -> new ConcurrentHashMap<>()).put(itemId, indexed);
    }
    return bucketKey;
  }

  private void removeIndexedItem(GameEntity item) {
    UUID itemId = item.id();
    if (itemId == null) {
      return;
    }

    synchronized (itemIndexLock) {
      IndexedItem indexed = indexedItems.remove(itemId);
      if (indexed != null) {
        removeFromBucket(indexed);
      }
    }
  }

  private void removeFromBucket(IndexedItem indexed) {
    Map<UUID, IndexedItem> bucket = itemBuckets.get(indexed.bucketKey);
    if (bucket == null) {
      return;
    }
    bucket.remove(indexed.itemId, indexed);
    if (bucket.isEmpty()) {
      itemBuckets.remove(indexed.bucketKey, bucket);
    }
  }

  private void queueBucket(ItemBucketKey bucketKey, int delayTicks) {
    long dueAt = System.currentTimeMillis() + (Math.max(0, delayTicks) * 50L);
    synchronized (queueLock) {
      Long existing = queuedBuckets.get(bucketKey);
      if (existing != null) {
        if (dueAt < existing) {
          queuedBuckets.put(bucketKey, dueAt);
        }
        return;
      }
      if (queuedBuckets.size() >= MAX_QUEUED_BUCKETS) {
        return;
      }
      queuedBuckets.put(bucketKey, dueAt);
      bucketQueue.offer(bucketKey);
    }
  }

  private GameEntity findAnchor(ItemHost host, ItemBucketKey bucketKey) {
    Map<UUID, IndexedItem> bucket = itemBuckets.get(bucketKey);
    if (bucket == null) {
      return null;
    }
    int maxBundle = effectiveMaxItemsPerBundle();
    int skip = rotationOffset(anchorRotation, bucket.size());
    Object wrapped = null;
    int index = 0;
    for (IndexedItem indexed : bucket.values()) {
      Object item = indexed.reference.get();
      if (item == null) {
        bucket.remove(indexed.itemId, indexed);
        indexedItems.remove(indexed.itemId, indexed);
        continue;
      }
      if (indexed.bundleCount >= maxBundle) {
        continue;
      }
      if (index++ >= skip) {
        return host.entity(item);
      }
      if (wrapped == null) {
        wrapped = item;
      }
    }
    if (bucket.isEmpty()) {
      itemBuckets.remove(bucketKey, bucket);
    }
    return wrapped == null ? null : host.entity(wrapped);
  }

  private List<GameEntity> collectCandidates(ItemHost host, ItemBucketKey anchorKey, GameEntity anchor, int maximum) {
    List<GameEntity> candidates = new ArrayList<>(maximum);
    Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    seen.add(anchor.handle());
    candidates.add(anchor);
    int anchorX = chunkX(anchorKey.chunkKey);
    int anchorZ = chunkZ(anchorKey.chunkKey);
    int chunkRadius = Math.max(0, (int) Math.ceil(effectiveSearchRadius() / 16D));
    for (int offsetX = -chunkRadius; offsetX <= chunkRadius && candidates.size() < maximum; offsetX++) {
      for (int offsetZ = -chunkRadius; offsetZ <= chunkRadius && candidates.size() < maximum; offsetZ++) {
        ItemBucketKey bucketKey = new ItemBucketKey(
            anchorKey.worldId,
            packChunk(anchorX + offsetX, anchorZ + offsetZ)
        );
        Map<UUID, IndexedItem> bucket = itemBuckets.get(bucketKey);
        if (bucket == null) {
          continue;
        }
        int skip = rotationOffset(candidateRotation, bucket.size());
        appendCandidates(host, bucket, skip, Integer.MAX_VALUE, maximum, candidates, seen);
        appendCandidates(host, bucket, 0, skip, maximum, candidates, seen);
        if (bucket.isEmpty()) {
          itemBuckets.remove(bucketKey, bucket);
        }
      }
    }
    return candidates;
  }

  private void appendCandidates(
      ItemHost host,
      Map<UUID, IndexedItem> bucket,
      int from,
      int to,
      int maximum,
      List<GameEntity> candidates,
      Set<Object> seen
  ) {
    int index = 0;
    for (IndexedItem indexed : bucket.values()) {
      if (candidates.size() >= maximum || index >= to) {
        return;
      }
      int position = index++;
      if (position < from) {
        continue;
      }
      Object item = indexed.reference.get();
      if (item == null) {
        bucket.remove(indexed.itemId, indexed);
        indexedItems.remove(indexed.itemId, indexed);
        continue;
      }
      if (seen.add(item)) {
        candidates.add(host.entity(item));
      }
    }
  }

  private static int rotationOffset(AtomicInteger rotation, int size) {
    int window = Math.min(size, MAX_CANDIDATES_PER_BUCKET_PASS);
    return window <= 1 ? 0 : Math.floorMod(rotation.getAndIncrement(), window);
  }

  private void completeBucketFlight(BucketFlight flight, boolean retry) {
    if (!flight.terminal.compareAndSet(false, true)) {
      return;
    }
    bucketFlights.remove(flight.bucketKey, flight);
    if (retry && isActive(flight.generation)) {
      queueBucket(flight.bucketKey, 0);
    }
  }

  private void clearMergeIndex() {
    synchronized (itemIndexLock) {
      indexedItems.clear();
      itemBuckets.clear();
    }
    synchronized (queueLock) {
      queuedBuckets.clear();
      bucketQueue.clear();
    }
    bucketFlights.clear();
  }

  private boolean isActive(long generation) {
    return active && generation == lifecycleGeneration.get();
  }

  private static long packChunk(int chunkX, int chunkZ) {
    return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
  }

  private static int chunkX(long chunkKey) {
    return (int) (chunkKey >> 32);
  }

  private static int chunkZ(long chunkKey) {
    return (int) chunkKey;
  }

  void removeTrackedItem(GameEntity item) {
    removeIndexedItem(item);
    ItemHost host = items();
    if (host == null) {
      return;
    }
    host.clearLabel(item);
    glossRefreshes.remove(item.id());
    host.discardMerged(item);
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

  private static void reportError(Throwable failure) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.reportError(failure);
    }
  }

  private int effectiveMaxItemsPerBundle() {
    return Math.max(2, Math.min(maxItemsPerBundle, 64));
  }

  private double effectiveSearchRadius() {
    return Math.max(0.5D, Math.min(searchRadius, 16.0D));
  }

  private int effectiveMaxMergesPerPass() {
    return Math.max(1, Math.min(maxMergesPerPass, 64));
  }

  private int effectiveSpawnMergeDelayTicks() {
    return Math.max(1, Math.min(spawnMergeDelayTicks, 20));
  }

  @Override
  public int getTickInterval() {
    return 50;
  }

  private record ItemBucketKey(UUID worldId, long chunkKey) {
  }

  private static final class IndexedItem {
    private final UUID itemId;
    private final ItemBucketKey bucketKey;
    private final WeakReference<Object> reference;
    private volatile int bundleCount;

    private IndexedItem(UUID itemId, ItemBucketKey bucketKey, WeakReference<Object> reference, int bundleCount) {
      this.itemId = itemId;
      this.bucketKey = bucketKey;
      this.reference = reference;
      this.bundleCount = bundleCount;
    }
  }

  private static final class BucketFlight {
    private final ItemBucketKey bucketKey;
    private final long generation;
    private final AtomicBoolean terminal;

    private BucketFlight(ItemBucketKey bucketKey, long generation) {
      this.bucketKey = bucketKey;
      this.generation = generation;
      this.terminal = new AtomicBoolean(false);
    }
  }
}
