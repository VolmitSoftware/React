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
import art.arcane.react.React;
import art.arcane.react.api.feature.FeatureIntegrityListener;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.core.integration.GlossDropNameIntegration;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.BundleUtils;
import art.arcane.volmlib.util.math.RNG;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Stacks items into bundles
 */
@art.arcane.react.util.project.config.ConfigDescription("Configuration for Item Super Stacker feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureItemSuperStacker extends ReactFeature implements FeatureIntegrityListener {
  public static final String ID = "item-super-stacker";
  private static final long GLOSS_REPUBLISH_INTERVAL_MS = 30_000L;
  private static final long GLOSS_CACHE_SWEEP_INTERVAL_MS = 60_000L;
  private static final int MAX_INDEXED_ITEMS = 65_536;
  private static final int MAX_QUEUED_BUCKETS = 4096;
  private static final int MAX_BUCKET_SUBMISSIONS_PER_TICK = 64;
  private static final int MAX_CANDIDATES_PER_BUCKET_PASS = 65;
  private static final double MERGE_SOUND_RADIUS = 32D;
  private static final int MAX_MERGE_SOUND_RECIPIENTS = 64;
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
  private transient final Consumer<Entity> entityTickListener = this::onItemTick;
  private transient final GlossDropNameIntegration glossDropNames;
  private transient final Map<UUID, Long> glossRefreshes;
  private transient final Map<UUID, IndexedItem> indexedItems = new ConcurrentHashMap<>();
  private transient final Map<ItemBucketKey, Map<UUID, IndexedItem>> itemBuckets = new ConcurrentHashMap<>();
  private transient final Map<ItemBucketKey, Long> queuedBuckets = new ConcurrentHashMap<>();
  private transient final Queue<ItemBucketKey> bucketQueue = new ConcurrentLinkedQueue<>();
  private transient final Map<ItemBucketKey, BucketFlight> bucketFlights = new ConcurrentHashMap<>();
  private transient final AtomicLong lifecycleGeneration = new AtomicLong();
  private transient final Object itemIndexLock = new Object();
  private transient final Object queueLock = new Object();
  private transient final Object lifecycleMutationLock = new Object();
  private transient volatile long lastGlossCacheSweepMs;
  private transient volatile boolean active;

  public FeatureItemSuperStacker() {
    this(new GlossDropNameIntegration());
  }

  FeatureItemSuperStacker(GlossDropNameIntegration glossDropNames) {
    super(ID);
    this.glossDropNames = Objects.requireNonNull(glossDropNames);
    this.glossRefreshes = new ConcurrentHashMap<>();
  }

  public boolean isSuperStack(Item item) {
    return BundleUtils.isBundle(item.getItemStack()) && BundleUtils.isFlagged(item.getItemStack());
  }

  public List<ItemStack> explode(Item item) {
    ItemStack m = item.getItemStack();
    List<ItemStack> contents = BundleUtils.isFlagged(m) ? BundleUtils.explode(m) : List.of(m);
    removeTrackedItem(item);
    return contents;
  }

  public void effectMerge(Item item, Item into) {
    Location source = item.getLocation();
    Location buf = source.clone();
    item.getWorld().spawnParticle(Particle.ITEM, source, 7, 0.1, 0.1, 0.1, 0.1, item.getItemStack());

    Location target = into.getLocation();
    double distance = target.distance(source);
    int steps = Math.max(1, (int) Math.ceil(effectiveSearchRadius() * 2.0D));
    if (distance > 1.0E-6D) {
      Vector step = target.clone().subtract(source).toVector().multiply(1.0D / steps);
      for (int index = 0; index < steps; index++) {
        buf.add(step);
        item.getWorld().spawnParticle(Particle.ITEM, buf, 3, 0, 0, 0, 0, item.getItemStack());
      }
    }

    if (cl.flip()) {
      playMergeSound(source);
    }
  }

  void playMergeSound(Location source) {
    World world = source.getWorld();
    if (world == null) {
      return;
    }

    UUID worldId = world.getUID();
    double x = source.getX();
    double y = source.getY();
    double z = source.getZ();
    Sound sound = Sound.sound(
        Key.key("minecraft:item.bundle.insert"),
        Sound.Source.NEUTRAL,
        0.5f,
        1.2f + RNG.r.f(-0.1f, 0.1f)
    );
    NearbyPlayerIndexController controller = React.controller(NearbyPlayerIndexController.class);
    if (controller == null) {
      return;
    }

    List<NearbyPlayerIndexController.PlayerViewSnapshot> recipients = controller.playerSnapshotsInColumn(
        world,
        x,
        z,
        MERGE_SOUND_RADIUS,
        MAX_MERGE_SOUND_RECIPIENTS
    );
    for (NearbyPlayerIndexController.PlayerViewSnapshot recipient : recipients) {
      Player player = Bukkit.getPlayer(recipient.playerId());
      if (player == null) {
        continue;
      }

      try {
        J.runEntity(
            player,
            () -> playMergeSoundOwned(player, worldId, x, y, z, sound),
            0,
            null
        );
      } catch (Throwable throwable) {
        React.reportError(throwable);
      }
    }
  }

  private void playMergeSoundOwned(Player player, UUID worldId, double x, double y, double z, Sound sound) {
    if (!J.isOwnedByCurrentRegion(player)) {
      return;
    }

    playMergeSound(player, worldId, x, y, z, sound);
  }

  private void playMergeSound(Player player, UUID worldId, double x, double y, double z, Sound sound) {
    if (!player.isOnline()) {
      return;
    }

    World playerWorld = player.getWorld();
    if (playerWorld == null || !worldId.equals(playerWorld.getUID())) {
      return;
    }

    React.audiences().player(player).playSound(sound, x, y, z);
  }

  public void mergeWithNearbyItems(Item item) {
    if (!active) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(item)) {
      J.runEntity(item, () -> mergeWithNearbyItems(item), 0, null);
      return;
    }

    synchronized (lifecycleMutationLock) {
      if (!active || item.isDead() || !item.isValid()) {
        return;
      }
      ItemBucketKey bucketKey = indexItem(item);
      if (bucketKey != null) {
        mergeBucketOwned(item, bucketKey);
      }
    }
  }

  @Override
  public void onTick() {
    long generation = lifecycleGeneration.get();
    if (!isActive(generation)) {
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
      Item anchor = findAnchor(bucketKey);
      if (anchor == null) {
        completeBucketFlight(flight, false);
        continue;
      }
      scheduleBucketFlight(anchor, flight);
    }
  }

  private void scheduleBucketFlight(Item anchor, BucketFlight flight) {
    Runnable retired = () -> completeBucketFlight(flight, true);
    boolean scheduled;
    try {
      scheduled = J.runEntity(
          anchor,
          () -> executeBucketFlight(anchor, flight),
          0,
          retired
      );
    } catch (RuntimeException | Error failure) {
      React.reportError(failure);
      retired.run();
      return;
    }
    if (!scheduled) {
      retired.run();
    }
  }

  private void executeBucketFlight(Item anchor, BucketFlight flight) {
    boolean continueWork = false;
    try {
      synchronized (lifecycleMutationLock) {
        if (!isActive(flight.generation)
            || (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(anchor))
            || anchor.isDead()
            || !anchor.isValid()) {
          removeIndexedItem(anchor);
          continueWork = true;
        } else {
          ItemBucketKey currentKey = indexItem(anchor);
          if (currentKey != null && !currentKey.equals(flight.bucketKey)) {
            queueBucket(currentKey, 0);
          } else if (currentKey != null) {
            continueWork = mergeBucketOwned(anchor, currentKey);
          }
        }
      }
    } catch (Throwable failure) {
      React.reportError(failure);
      continueWork = true;
    } finally {
      completeBucketFlight(flight, continueWork);
    }
  }

  private boolean mergeBucketOwned(Item anchor, ItemBucketKey bucketKey) {
    boolean folia = J.isFoliaThreading();
    List<Item> candidates = collectCandidates(
        bucketKey,
        anchor,
        Math.min(MAX_CANDIDATES_PER_BUCKET_PASS, effectiveMaxMergesPerPass() + 1)
    );
    Item collector = anchor;
    int merged = 0;
    boolean effectPlayed = false;
    if (collector.isDead() || !collector.isValid()) {
      removeIndexedItem(collector);
      return false;
    }
    for (Item target : candidates) {
      if (merged >= effectiveMaxMergesPerPass()) {
        break;
      }
      if (target == collector) {
        continue;
      }
      if (folia && !J.isOwnedByCurrentRegion(target)) {
        continue;
      }
      if (target.isDead() || !target.isValid()) {
        removeIndexedItem(target);
        continue;
      }
      if (!withinMergeRadius(collector, target)) {
        continue;
      }

      Item survivor = mergePair(collector, target, !effectPlayed);
      if (survivor != null) {
        collector = survivor;
        effectPlayed = true;
        merged++;
      }
    }

    if (merged >= effectiveMaxMergesPerPass() && !collector.isDead() && collector.isValid()) {
      ItemBucketKey continuationKey = indexItem(collector);
      if (continuationKey != null) {
        queueBucket(continuationKey, 0);
      }
    }
    return false;
  }

  private boolean withinMergeRadius(Item source, Item target) {
    Location sourceLocation = source.getLocation();
    Location targetLocation = target.getLocation();
    World sourceWorld = sourceLocation.getWorld();
    World targetWorld = targetLocation.getWorld();
    if (sourceWorld == null || targetWorld == null || sourceWorld != targetWorld) {
      return false;
    }

    double radius = effectiveSearchRadius();
    return Math.abs(sourceLocation.getX() - targetLocation.getX()) <= radius
        && Math.abs(sourceLocation.getY() - targetLocation.getY()) <= radius
        && Math.abs(sourceLocation.getZ() - targetLocation.getZ()) <= radius;
  }

  private Item mergePair(Item source, Item target, boolean playEffect) {
    ItemStack sourceStack = source.getItemStack();
    ItemStack targetStack = target.getItemStack();
    ItemStack bundled = BundleUtils.merge(sourceStack, targetStack, effectiveMaxItemsPerBundle());
    if (bundled != null) {
      if (playEffect) {
        effectMerge(source, target);
      }
      removeTrackedItem(source);
      target.setItemStack(bundled);
      refreshGloss(target);
      return target;
    }
    if (!mergeMatchingStacks || !sourceStack.isSimilar(targetStack)) {
      return null;
    }

    int capacity = Math.max(0, targetStack.getMaxStackSize() - targetStack.getAmount());
    int transferred = Math.min(sourceStack.getAmount(), capacity);
    if (transferred <= 0) {
      return null;
    }
    if (playEffect) {
      effectMerge(source, target);
    }

    ItemStack updatedTarget = targetStack.clone();
    updatedTarget.setAmount(targetStack.getAmount() + transferred);
    target.setItemStack(updatedTarget);
    refreshGloss(target);
    if (transferred == sourceStack.getAmount()) {
      removeTrackedItem(source);
      return target;
    }

    ItemStack remainder = sourceStack.clone();
    remainder.setAmount(sourceStack.getAmount() - transferred);
    source.setItemStack(remainder);
    refreshGloss(source);
    return source;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityPickupItemEvent e) {
    if (e.getEntity() instanceof Player p) {
      if (isSuperStack(e.getItem())) {
        e.setCancelled(true);
        for (ItemStack i : explode(e.getItem())) {
          p.getInventory().addItem(i).values().forEach((g) -> p.getWorld().dropItem(p.getLocation(), g));
        }

        if (cl.flip()) {
          React.audiences().player(p).playSound(Sound.sound(
              Key.key("minecraft:item.bundle.drop_contents"),
              Sound.Source.PLAYER,
              1f,
              0.85f + RNG.r.f(-0.1f, 0.1f)
          ), e.getItem().getLocation().getX(), e.getItem().getLocation().getY(), e.getItem().getLocation().getZ());
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(InventoryClickEvent e) {
    if (e.getWhoClicked() instanceof Player p) {
      if (e.getCurrentItem() != null && BundleUtils.isFlagged(e.getCurrentItem())) {
        ItemStack i = e.getCurrentItem();
        List<ItemStack> items = BundleUtils.explode(i);
        e.setCancelled(true);
        e.setCurrentItem(null);
        for (ItemStack j : items) {
          p.getInventory().addItem(j).values().forEach((g) -> p.getWorld().dropItem(p.getLocation(), g));
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(InventoryPickupItemEvent event) {
    Item item = event.getItem();
    if (!isSuperStack(item)) {
      return;
    }

    event.setCancelled(true);
    List<ItemStack> leftovers = insertContents(event.getInventory(), BundleUtils.explode(item.getItemStack()));
    if (leftovers.isEmpty()) {
      removeTrackedItem(item);
      return;
    }

    ItemStack residualBundle = BundleUtils.createBundle(leftovers);
    if (residualBundle != null) {
      item.setItemStack(residualBundle);
      refreshGloss(item);
      return;
    }

    Location location = item.getLocation();
    World world = item.getWorld();
    removeTrackedItem(item);
    for (ItemStack leftover : leftovers) {
      world.dropItemNaturally(location, leftover);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ItemSpawnEvent e) {
    if (active) {
      queueItem(e.getEntity(), effectiveSpawnMergeDelayTicks());
    }
  }

  @EventHandler
  public void on(EntityRemoveEvent event) {
    if (event.getEntity() instanceof Item item) {
      removeIndexedItem(item);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesLoadEvent event) {
    if (!active) {
      return;
    }
    for (Entity entity : event.getEntities()) {
      if (entity instanceof Item item) {
        queueItem(item, 0);
        if (isSuperStack(item)) {
          refreshGloss(item);
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Item item)) {
      return;
    }

    if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
      return;
    }

    if (isSuperStack(item)) {
      e.setCancelled(true);
    }
  }

  @Override
  public void onActivate() {
    synchronized (lifecycleMutationLock) {
      lifecycleGeneration.incrementAndGet();
      active = true;
      clearMergeIndex();
    }
    glossRefreshes.clear();
    EntityController controller = React.controller(EntityController.class);
    if (controller != null) {
      controller.registerEntityTickListener(EntityType.ITEM, entityTickListener);
    }
  }

  @Override
  public void onDeactivate() {
    synchronized (lifecycleMutationLock) {
      active = false;
      lifecycleGeneration.incrementAndGet();
      clearMergeIndex();
    }
    glossRefreshes.clear();
    EntityController controller = React.controller(EntityController.class);
    if (controller != null) {
      controller.unregisterEntityTickListener(entityTickListener);
    }
  }

  private void onItemTick(Entity entity) {
    if (!active || !(entity instanceof Item item) || item.isDead() || !item.isValid()) {
      return;
    }
    queueItem(item, 0);
    if (!item.isDead() && item.isValid() && isSuperStack(item)) {
      refreshGlossIfDue(item);
    }
  }

  private void refreshGlossIfDue(Item item) {
    long now = System.currentTimeMillis();
    Long refreshedAt = glossRefreshes.get(item.getUniqueId());
    if (refreshedAt != null && now - refreshedAt < GLOSS_REPUBLISH_INTERVAL_MS) {
      return;
    }
    refreshGloss(item, now);
    sweepGlossRefreshes(now);
  }

  private void refreshGloss(Item item) {
    refreshGloss(item, System.currentTimeMillis());
  }

  private void refreshGloss(Item item, long now) {
    if (glossDropNames.refresh(item, glossBundleHeaderFormat, glossBundleEntryFormat,
        glossBundleMoreFormat, glossBundleEntryLimit)) {
      glossRefreshes.put(item.getUniqueId(), now);
    }
  }

  private void sweepGlossRefreshes(long now) {
    if (now - lastGlossCacheSweepMs < GLOSS_CACHE_SWEEP_INTERVAL_MS) {
      return;
    }
    lastGlossCacheSweepMs = now;
    glossRefreshes.entrySet().removeIf(entry -> now - entry.getValue() >= GLOSS_CACHE_SWEEP_INTERVAL_MS);
  }

  private void queueItem(Item item, int delayTicks) {
    synchronized (lifecycleMutationLock) {
      if (!active) {
        return;
      }
      ItemBucketKey bucketKey = indexItem(item);
      if (bucketKey != null) {
        queueBucket(bucketKey, delayTicks);
      }
    }
  }

  private ItemBucketKey indexItem(Item item) {
    UUID itemId = item.getUniqueId();
    Location location = item.getLocation();
    World world = location.getWorld();
    if (itemId == null || world == null) {
      return null;
    }

    ItemBucketKey bucketKey = new ItemBucketKey(
        world.getUID(),
        packChunk(location.getBlockX() >> 4, location.getBlockZ() >> 4)
    );
    synchronized (itemIndexLock) {
      IndexedItem previous = indexedItems.get(itemId);
      if (previous == null && indexedItems.size() >= MAX_INDEXED_ITEMS) {
        return null;
      }

      IndexedItem indexed = new IndexedItem(itemId, bucketKey, new WeakReference<>(item));
      indexedItems.put(itemId, indexed);
      if (previous != null && !previous.bucketKey.equals(bucketKey)) {
        removeFromBucket(previous);
      }
      itemBuckets.computeIfAbsent(bucketKey, ignored -> new ConcurrentHashMap<>()).put(itemId, indexed);
    }
    return bucketKey;
  }

  private void removeIndexedItem(Item item) {
    UUID itemId = item.getUniqueId();
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

  private Item findAnchor(ItemBucketKey bucketKey) {
    Map<UUID, IndexedItem> bucket = itemBuckets.get(bucketKey);
    if (bucket == null) {
      return null;
    }
    for (IndexedItem indexed : bucket.values()) {
      Item item = indexed.reference.get();
      if (item != null) {
        return item;
      }
      bucket.remove(indexed.itemId, indexed);
      indexedItems.remove(indexed.itemId, indexed);
    }
    if (bucket.isEmpty()) {
      itemBuckets.remove(bucketKey, bucket);
    }
    return null;
  }

  private List<Item> collectCandidates(ItemBucketKey anchorKey, Item anchor, int maximum) {
    List<Item> candidates = new ArrayList<>(maximum);
    Set<Item> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    seen.add(anchor);
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
        for (IndexedItem indexed : bucket.values()) {
          if (candidates.size() >= maximum) {
            break;
          }
          Item item = indexed.reference.get();
          if (item == null) {
            bucket.remove(indexed.itemId, indexed);
            indexedItems.remove(indexed.itemId, indexed);
            continue;
          }
          if (seen.add(item)) {
            candidates.add(item);
          }
        }
        if (bucket.isEmpty()) {
          itemBuckets.remove(bucketKey, bucket);
        }
      }
    }
    return candidates;
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

  private List<ItemStack> insertContents(Inventory inventory, List<ItemStack> contents) {
    List<ItemStack> leftovers = new ArrayList<>();
    for (ItemStack content : contents) {
      Map<Integer, ItemStack> overflow = inventory.addItem(content.clone());
      leftovers.addAll(overflow.values());
    }
    return leftovers;
  }

  private void removeTrackedItem(Item item) {
    removeIndexedItem(item);
    glossDropNames.remove(item);
    glossRefreshes.remove(item.getUniqueId());
    if (React.instance != null) {
      FeatureHopperItemIndex hopperItemIndex = React.feature(FeatureHopperItemIndex.class);
      if (hopperItemIndex != null && hopperItemIndex.getItemIndex() != null) {
        hopperItemIndex.getItemIndex().removeItem(item.getUniqueId());
      }

    }
    item.remove();
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
    private final WeakReference<Item> reference;

    private IndexedItem(UUID itemId, ItemBucketKey bucketKey, WeakReference<Item> reference) {
      this.itemId = itemId;
      this.bucketKey = bucketKey;
      this.reference = reference;
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
