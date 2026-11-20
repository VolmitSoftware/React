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
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.MobStackHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.format.Form;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Mob Stacking feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureMobStacking extends ReactFeature implements FeatureIntegrity, MobStackHost.Events {
  static boolean exceedsStackLimit(int intoCount, int sourceCount, int maxStackSize) {
    return intoCount + sourceCount > maxStackSize;
  }

  static boolean withinHealthLimit(double sourceMaxHealth, double intoMaxHealth, double maxHealth) {
    return sourceMaxHealth + intoMaxHealth <= maxHealth;
  }

  static int theoreticalMaxStackCount(double maxHealth, double entityMaxHealth, int maxStackSize) {
    return Math.min((int) Math.ceil(Math.floor(maxHealth / entityMaxHealth)), maxStackSize);
  }

  static long packChunkKey(int chunkX, int chunkZ) {
    return (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
  }

  static int chunkKeyX(long key) {
    return (int) (key >> 32);
  }

  static int chunkKeyZ(long key) {
    return (int) key;
  }

  static boolean withinMergeRadius(double ax, double ay, double az, double bx, double by, double bz, double radius) {
    return Math.abs(ax - bx) <= radius && Math.abs(ay - by) <= radius && Math.abs(az - bz) <= radius;
  }

  static String baseStackName(String type) {
    return Form.capitalizeWords(type.toLowerCase().replace('_', ' '));
  }

  static String nativeStackName(String baseName, int count, String uniqueLabel) {
    if (count > 1) {
      return BOLD + count + "x " + RESET + GRAY + baseName;
    }
    return GOLD + count + "x " + uniqueLabel + RESET + GRAY + baseName;
  }

  static boolean sameFormattedName(String name, String expected) {
    if (expected == null) {
      return false;
    }
    if (name.equals(expected)) {
      return true;
    }
    LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
    return serializer.serialize(serializer.deserialize(name))
        .equals(serializer.serialize(serializer.deserialize(expected)));
  }

  public static final String ID = "mob-stacking";
  private static final String BOLD = "§l";
  private static final String RESET = "§r";
  private static final String GRAY = "§7";
  private static final String GOLD = "§6";
  private static final long PRESENTATION_REFRESH_MS = 5_000L;
  private static final long QUIET_CHUNK_COOLDOWN_MS = 15_000L;
  private static final int MAX_CHUNK_SUBMISSIONS_PER_TICK = 64;
  static final int MAX_CHUNK_INSPECTIONS_PER_TICK = 128;
  private static final int MAX_INDEXED_ENTITIES = 65_536;
  private static final int MAX_NEIGHBOR_CHUNKS_PER_WORK = 64;
  private static final int MAX_NEIGHBOR_ENTITIES_PER_WORK = 256;
  static final int MAX_ENTITIES_PER_CHUNK_CALLBACK = 256;
  static final int MAX_COMPARISONS_PER_CHUNK_CALLBACK = 4096;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum stack size allowed by mob stacking.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private int maxStackSize = 10;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum health allowed by mob stacking.", impact = "Higher values allow more throughput before intervention; lower values make mitigation more aggressive.")
  private double maxHealth = 100;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Filter definition for stackable types used by mob stacking.", impact = "Narrow this list to target fewer cases, or broaden it to include more matching entries.")
  private Set<String> stackableTypes;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Shows native stack names when Gloss entity overlays are unavailable.", impact = "Gloss receives stack counts regardless of this setting and owns the combined entity overlay when enabled.")
  private boolean customNames = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Search radius used by mob stacking (blocks).", impact = "Higher values widen the search area and cost more work; lower values narrow scope and run cheaper.")
  private double searchRadius = 6;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether mob stacking applies vacuum effect.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean vacuumEffect = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Skips skip custom mobs when mob stacking evaluates targets.", impact = "Enable this to exclude matching cases; disable it to include them in enforcement.")
  private boolean skipCustomMobs = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Controls whether mob stacking applies only spawner mobs.", impact = "Enable to apply this behavior; disable to keep this path inactive.")
  private boolean onlySpawnerMobs = false;
  @art.arcane.react.util.project.config.ConfigDoc(value = "How often queued chunks are processed for stacking, in milliseconds.", impact = "Lower values stack freshly spawned mobs sooner at more scheduling cost; higher values batch more merge work per pass.")
  private int batchIntervalMs = 250;

  private transient final Map<String, String> formattedBaseNames = new ConcurrentHashMap<>();
  private transient final Map<UUID, Long> presentationRefreshes = new ConcurrentHashMap<>();
  private transient final Map<UUID, Set<Long>> dirtyChunks = new ConcurrentHashMap<>();
  private transient final Map<UUID, Set<Long>> inFlightChunks = new ConcurrentHashMap<>();
  private transient final Map<ChunkWorkKey, ChunkWork> chunkWork = new ConcurrentHashMap<>();
  private transient final Map<UUID, IndexedStackEntity> indexedEntities = new ConcurrentHashMap<>();
  private transient final Map<ChunkWorkKey, Map<UUID, IndexedStackEntity>> indexedChunks = new ConcurrentHashMap<>();
  private transient final Map<ChunkWorkKey, Long> quietChunks = new ConcurrentHashMap<>();
  private transient final Consumer<GameEntity> entityTickListener = this::onTick;
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient final MobStackRecovery recovery = new MobStackRecovery(this);
  private transient volatile MobStackHost stacks;
  private transient volatile EntityHost entities;
  private transient volatile WorldHost worlds;
  private transient volatile Subscription ticks = Subscription.NONE;
  private transient volatile Subscription events = Subscription.NONE;
  private transient volatile boolean active;
  private transient volatile StackableIndex stackableIndex;
  private transient volatile long nextQuietSweepMs;

  public FeatureMobStacking() {
    super(ID);
    stackableTypes = defaultStackableTypes();
  }

  FeatureMobStacking(MobStackHost stacks) {
    super(ID);
    this.stacks = Objects.requireNonNull(stacks);
    stackableTypes = new HashSet<>(stacks.defaultStackableTypes());
  }

  public static Set<String> defaultStackableTypes() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    Optional<MobStackHost> host = platform == null ? Optional.empty() : platform.mobStackHost();
    return host.isPresent() ? new HashSet<>(host.get().defaultStackableTypes()) : new HashSet<>();
  }

  MobStackHost stacks() {
    MobStackHost host = stacks;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.mobStackHost().orElse(null);
      stacks = host;
    }
    return host;
  }

  private EntityHost entities() {
    EntityHost host = entities;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.entityHost().orElse(null);
      entities = host;
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

  @Override
  public void onAttach() {
    MobStackHost host = stacks();
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      if (platform != null) {
        platform.warn("Mob Stacking needs mob access that " + platform.platformName() + " does not provide; it stays idle.");
      }
      return;
    }
    synchronized (this) {
      events.cancel();
      events = host.listen(this);
    }
  }

  @Override
  public void onDetach() {
    synchronized (this) {
      events.cancel();
      events = Subscription.NONE;
    }
  }

  @Override
  public void onActivate() {
    lifecycleGeneration.incrementAndGet();
    active = true;
    dirtyChunks.clear();
    inFlightChunks.clear();
    chunkWork.clear();
    indexedEntities.clear();
    indexedChunks.clear();
    quietChunks.clear();
    presentationRefreshes.clear();
    StackableIndex index = rebuildStackableIndex();
    EntityHost host = entities();
    if (host == null || stacks() == null || index.types().isEmpty()) {
      return;
    }
    synchronized (this) {
      ticks.cancel();
      ticks = host.onTick(index.types(), entityTickListener);
    }
  }

  public boolean isStackableType(String type) {
    if (type == null) {
      return false;
    }

    StackableIndex index = stackableIndex;
    if (index == null || index.source() != stackableTypes) {
      index = rebuildStackableIndex();
    }

    return index.types().contains(type);
  }

  private synchronized StackableIndex rebuildStackableIndex() {
    Set<String> configured = stackableTypes;
    Set<String> types = new HashSet<>();
    if (configured != null) {
      for (String type : configured) {
        if (type != null && !type.isBlank()) {
          types.add(type.trim());
        }
      }
    }

    StackableIndex index = new StackableIndex(configured, Set.copyOf(types));
    stackableIndex = index;
    return index;
  }

  @Override
  public void onTame(GameEntity entity, UUID ownerId) {
    if (getStackCount(entity) <= 1) {
      return;
    }

    J.runEntity(entity.handle(), () -> completeTamedStackSplit(entity, ownerId), 1);
  }

  @Override
  public void onSneakInteract(GameEntity clicked) {
    MobStackHost host = stacks();
    if (host == null || !isStackableType(clicked.type()) || getStackCount(clicked) <= 1) {
      return;
    }

    int newStackCount = getStackCount(clicked) / 2;
    int remainingStackCount = getStackCount(clicked) - newStackCount;
    GameEntity split;
    try {
      split = host.spawnCopy(clicked, 0.5D, hasUserCustomName(clicked));
    } catch (RuntimeException failure) {
      ReactPlatforms.get().warn("Could not split " + clicked.type() + " stack; the original stack remains unchanged.", failure);
      return;
    }
    if (split == null) {
      ReactPlatforms.get().warn("Could not split " + clicked.type() + " stack; the replacement spawn was rejected.");
      return;
    }
    host.setHealth(split, host.health(clicked));
    setStackCount(split, newStackCount);
    host.setFlag(split, MobStackHost.DO_NOT_STACK, true);
    host.setFlag(split, MobStackHost.UNIQUE_STACK, true);
    updateEntityCustomName(split);

    setStackCount(clicked, remainingStackCount);
    host.setFlag(clicked, MobStackHost.DO_NOT_STACK, true);
    host.setFlag(clicked, MobStackHost.UNIQUE_STACK, true);
    updateEntityCustomName(clicked);
  }

  public void updateEntityCustomName(GameEntity entity) {
    refreshStackPresentation(entity, getStackCount(entity));
  }

  @Override
  public void onDeath(GameEntity entity) {
    MobStackHost host = stacks();
    if (host == null) {
      return;
    }
    int currentStack = getStackCount(entity);

    if (currentStack > 1) {
      spawnReplacement(entity, currentStack - 1);
    }

    if (currentStack > 1 || host.hasFlag(entity, MobStackHost.UNIQUE_STACK)) {
      clearStackName(entity, currentStack);
    }
  }

  @Override
  public boolean beforeStateChange(GameEntity source) {
    int count = getStackCount(source);
    if (count <= 1) {
      return true;
    }
    try {
      GameEntity replacement = createReplacement(source);
      if (replacement == null) {
        ReactPlatforms.get().warn("Could not split " + source.type() + " stack before its state changed; the change was cancelled.");
        return false;
      }
      setStackCount(replacement, count - 1);
      setStackCount(source, 1);
      return true;
    } catch (RuntimeException failure) {
      ReactPlatforms.get().warn("Could not split " + source.type() + " stack before its state changed; the change was cancelled.", failure);
      return false;
    }
  }

  private boolean spawnReplacement(GameEntity source, int nextStackCount) {
    if (nextStackCount <= 0) {
      return false;
    }

    GameEntity replacement = createReplacement(source);
    if (replacement == null) {
      return false;
    }
    setStackCount(replacement, nextStackCount);
    return true;
  }

  private GameEntity createReplacement(GameEntity source) {
    MobStackHost host = stacks();
    GameEntity replacement = host.spawnCopy(source, 0D, hasUserCustomName(source));
    if (replacement == null) {
      return null;
    }
    host.untame(replacement);
    return replacement;
  }

  private void completeTamedStackSplit(GameEntity entity, UUID ownerId) {
    MobStackHost host = stacks();
    if (!host.isAlive(entity) || !host.isTamedPet(entity)) {
      return;
    }

    if (ownerId == null || !ownerId.equals(host.ownerId(entity))) {
      return;
    }

    splitTamedStack(entity);
  }

  private boolean splitTamedStack(GameEntity entity) {
    int currentStack = getStackCount(entity);
    if (currentStack <= 1 || !spawnReplacement(entity, currentStack - 1)) {
      return false;
    }

    setStackCount(entity, 1);
    return true;
  }

  public boolean merge(GameEntity a, GameEntity into) {
    if (!active || !isEnabled()) {
      return false;
    }
    EntityHost protection = entities();
    if (protection != null && (protection.isProtected(a, ReactOperation.STACK)
        || protection.isProtected(into, ReactOperation.STACK))) {
      return false;
    }

    if (canMerge(a, into)) {
      setStackCount(into, getStackCount(into) + getStackCount(a));
      MobStackHost host = stacks();
      if (vacuumEffect) {
        host.sendCollect(a, into);
      }
      host.discard(a);
      return true;
    }

    return false;
  }

  public boolean canMerge(GameEntity a, GameEntity into) {
    MobStackHost host = stacks();
    if (host == null || host.isSameEntity(a, into)) {
      return false;
    }

    if (!Objects.equals(a.type(), into.type())) {
      return false;
    }

    if (host.isTamedPet(a) || host.isTamedPet(into)) {
      return false;
    }

    if (!isStackableType(a.type())) {
      return false;
    }

    if (!host.isLiving(a) || !host.isLiving(into)) {
      return false;
    }

    if (a.isPlayer() || into.isPlayer()) {
      return false;
    }

    if (host.isDead(a) || host.isDead(into)) {
      return false;
    }

    if (!Objects.equals(host.mergeBucket(a), host.mergeBucket(into))) {
      return false;
    }

    if (host.hasFlag(a, MobStackHost.DO_NOT_STACK) || host.hasFlag(into, MobStackHost.DO_NOT_STACK)) {
      return false;
    }

    if (onlySpawnerMobs && (!host.hasFlag(a, MobStackHost.SPAWNED_BY_SPAWNER)
        || !host.hasFlag(into, MobStackHost.SPAWNED_BY_SPAWNER))) {
      return false;
    }

    if (exceedsStackLimit(getStackCount(into), getStackCount(a), maxStackSize)) {
      return false;
    }

    if (!host.hasSafeMergeState(a, into)) {
      return false;
    }
    if (hasUserCustomName(a) || hasUserCustomName(into)) {
      return false;
    }
    if (!host.hasSafeHealthState(a, into)) {
      return false;
    }

    EntityHost custom = entities();
    if (skipCustomMobs && custom != null && (custom.isCustomMob(a) || custom.isCustomMob(into))) {
      return false;
    }

    return withinHealthLimit(host.maxHealth(a), host.maxHealth(into), maxHealth);
  }

  private boolean hasUserCustomName(GameEntity entity) {
    String name = stacks().customName(entity);
    return name != null && !name.isBlank() && !ownsStackName(entity, name, getStackCount(entity));
  }

  public int getTheoreticalMaxStackCount(GameEntity entityAsType) {
    MobStackHost host = stacks();
    if (host != null && host.isLiving(entityAsType)) {
      return theoreticalMaxStackCount(maxHealth, host.maxHealth(entityAsType), maxStackSize);
    }

    return maxStackSize;
  }

  public void setStackCount(GameEntity entity, int count) {
    clearStackName(entity, getStackCount(entity));
    ReactEntity.setStackCount(entity, count);
    refreshStackPresentation(entity, count);
  }

  void refreshStackPresentation(GameEntity entity, int count) {
    MobStackHost host = stacks();
    boolean handled = host.isLiving(entity) && host.showOverlay(entity, count);
    String currentName = host.customName(entity);
    if (handled || !customNames || (count <= 1 && !host.hasFlag(entity, MobStackHost.UNIQUE_STACK))) {
      clearStackName(entity, count);
    } else if (currentName == null || currentName.isBlank() || ownsStackName(entity, currentName, count)) {
      String stackName = nativeStackName(entity, count);
      if (!stackName.equals(currentName)) {
        host.setCustomName(entity, stackName);
      }
      String appliedName = host.customName(entity);
      if (appliedName != null) {
        host.setStackLabel(entity, appliedName);
      }
    }
    UUID entityId = entity.id();
    if (entityId != null) {
      presentationRefreshes.put(entityId, System.currentTimeMillis());
    }
  }

  private void clearStackName(GameEntity entity, int count) {
    MobStackHost host = stacks();
    String name = host.customName(entity);
    if (name == null) {
      return;
    }
    if (ownsStackName(entity, name, count)) {
      host.setCustomName(entity, null);
    }
    host.clearStackLabel(entity);
  }

  private boolean ownsStackName(GameEntity entity, String name, int count) {
    MobStackHost host = stacks();
    String storedName = host.stackLabel(entity);
    return sameFormattedName(name, storedName)
        || ((count > 1 || host.hasFlag(entity, MobStackHost.UNIQUE_STACK))
        && sameFormattedName(name, nativeStackName(entity, count)));
  }

  private String nativeStackName(GameEntity entity, int count) {
    String baseName = formattedBaseNames.computeIfAbsent(entity.type(), FeatureMobStacking::baseStackName);
    return nativeStackName(baseName, count, count > 1 ? "" : ReactLanguage.plain(RuntimeMessages.MOB_STACKING_UNIQUE));
  }

  public int getStackCount(GameEntity entity) {
    return ReactEntity.getStackCount(entity);
  }

  @Override
  public void onIntegrityTick() {
    recovery.tick(lifecycleGeneration.get());
  }

  boolean shouldRecoverStacks(long generation) {
    return !isEnabled() && generation == lifecycleGeneration.get() && ReactComponents.isRunning();
  }

  int restoreStack(GameEntity source, int maximum, long generation) {
    MobStackHost host = stacks();
    int restored = 0;
    while (restored < maximum && shouldRecoverStacks(generation) && host.isAlive(source)) {
      int count = getStackCount(source);
      if (count <= 1) {
        if (host.hasFlag(source, MobStackHost.UNIQUE_STACK) || host.stackLabel(source) != null) {
          finishStackRecovery(source, count);
        }
        break;
      }

      GameEntity replacement = createReplacement(source);
      if (replacement == null) {
        recovery.warnRejectedSpawn(source);
        break;
      }
      if (!shouldRecoverStacks(generation) || !host.isAlive(source) || getStackCount(source) != count) {
        host.discard(replacement);
        break;
      }
      try {
        host.restoreHealth(replacement, host.health(source));
        host.setFlag(replacement, MobStackHost.UNIQUE_STACK, false);
        host.setFlag(replacement, MobStackHost.DO_NOT_STACK, false);
        ReactEntity.setStackCount(replacement, 1);
        ReactEntity.setStackCount(source, count - 1);
      } catch (RuntimeException failure) {
        host.discard(replacement);
        throw failure;
      }
      restored++;
      clearStackName(source, count);
      if (count == 2) {
        finishStackRecovery(source, 1);
      } else {
        refreshStackPresentation(source, count - 1);
      }
      finishStackRecovery(replacement, 1);
    }
    return restored;
  }

  private void finishStackRecovery(GameEntity entity, int previousCount) {
    MobStackHost host = stacks();
    clearStackName(entity, previousCount);
    host.setFlag(entity, MobStackHost.UNIQUE_STACK, false);
    host.setFlag(entity, MobStackHost.DO_NOT_STACK, false);
    refreshStackPresentation(entity, 1);
    UUID entityId = entity.id();
    if (entityId != null) {
      presentationRefreshes.remove(entityId);
    }
  }

  @Override
  public void onEntitiesLoad(List<GameEntity> loaded) {
    if (isEnabled() || !recovery.needsRearm()) {
      return;
    }
    MobStackHost host = stacks();
    for (GameEntity entity : loaded) {
      if (host.isLiving(entity) && getStackCount(entity) > 1) {
        recovery.rearm();
        return;
      }
    }
  }

  @Override
  public void onSpawn(GameEntity entity) {
    if (active && isStackableType(entity.type())) {
      markDirty(entity, stacks().isLiving(entity) ? getStackCount(entity) : 1, true);
    }
  }

  @Override
  public void onSpawnerSpawn(GameEntity entity) {
    if (active) {
      stacks().setFlag(entity, MobStackHost.SPAWNED_BY_SPAWNER, true);
    }
  }

  public void onTick(GameEntity entity) {
    if (entity == null) {
      return;
    }
    MobStackHost host = stacks();
    if (host.isDead(entity)) {
      removeIndexed(entity);
      return;
    }

    if (host.isLiving(entity) && host.isTamedPet(entity)) {
      removeIndexed(entity);
      splitTamedStack(entity);
      return;
    }

    if (!active) {
      return;
    }

    boolean living = host.isLiving(entity);
    int stackCount = living ? getStackCount(entity) : 1;
    if (living && (stackCount > 1 || host.hasFlag(entity, MobStackHost.UNIQUE_STACK))) {
      Long lastRefresh = presentationRefreshes.get(entity.id());
      if (lastRefresh == null || System.currentTimeMillis() - lastRefresh >= PRESENTATION_REFRESH_MS) {
        updateEntityCustomName(entity);
      }
    }
    markDirty(entity, stackCount, false);
  }

  private void markDirty(GameEntity entity, int stackCount, boolean force) {
    GameLocation location = entity.location();
    if (location == null) {
      return;
    }

    UUID worldId = location.worldId();
    long chunkKey = packChunkKey(location.chunkX(), location.chunkZ());
    ChunkWorkKey workKey = new ChunkWorkKey(worldId, chunkKey);
    boolean changed = indexEntity(entity, workKey, stackCount);
    if (!force && !changed && isQuiet(workKey)) {
      return;
    }
    Set<Long> pending = dirtyChunks.computeIfAbsent(worldId, ignored -> ConcurrentHashMap.newKeySet());
    pending.add(chunkKey);
    addCanonicalNeighborAnchors(worldId, chunkKey, pending);
  }

  private boolean isQuiet(ChunkWorkKey workKey) {
    Long quietUntil = quietChunks.get(workKey);
    if (quietUntil == null) {
      return false;
    }
    if (quietUntil > System.currentTimeMillis()) {
      return true;
    }
    quietChunks.remove(workKey, quietUntil);
    return false;
  }

  private void recordPassOutcome(ChunkWorkKey workKey, int merges) {
    if (merges > 0) {
      quietChunks.remove(workKey);
      return;
    }
    quietChunks.put(workKey, System.currentTimeMillis() + QUIET_CHUNK_COOLDOWN_MS);
  }

  private void pruneQuietChunks(long now) {
    if (now < nextQuietSweepMs || quietChunks.isEmpty()) {
      return;
    }
    nextQuietSweepMs = now + QUIET_CHUNK_COOLDOWN_MS;
    quietChunks.values().removeIf(quietUntil -> quietUntil <= now);
  }

  @Override
  public void onRemove(GameEntity entity, boolean exploded) {
    try {
      if (exploded) {
        int count = getStackCount(entity);
        if (count > 1) {
          GameEntity replacement = createReplacement(entity);
          if (replacement != null) {
            setStackCount(replacement, count - 1);
          } else {
            ReactPlatforms.get().warn("Could not restore the remaining " + (count - 1) + " creepers after a stack exploded.");
          }
        }
      }
    } catch (RuntimeException failure) {
      ReactPlatforms.get().warn("Could not restore creeper stack after explosion at " + entity.location(), failure);
    } finally {
      removeIndexed(entity);
    }
  }

  private boolean indexEntity(GameEntity entity, ChunkWorkKey workKey, int stackCount) {
    UUID entityId = entity.id();
    Object handle = entity.handle();
    if (isIndexedAt(indexedEntities.get(entityId), handle, workKey, stackCount)) {
      return false;
    }

    synchronized (this) {
      IndexedStackEntity previous = indexedEntities.get(entityId);
      if (isIndexedAt(previous, handle, workKey, stackCount)) {
        return false;
      }
      if (previous == null && indexedEntities.size() >= MAX_INDEXED_ENTITIES) {
        return true;
      }

      IndexedStackEntity indexed = new IndexedStackEntity(entityId, workKey, new WeakReference<>(handle), stackCount);
      indexedEntities.put(entityId, indexed);
      if (previous != null && !previous.chunkKey().equals(workKey)) {
        removeIndexedFromChunk(previous);
      }
      indexedChunks.computeIfAbsent(workKey, ignored -> new ConcurrentHashMap<>()).put(entityId, indexed);
    }
    return true;
  }

  private static boolean isIndexedAt(IndexedStackEntity indexed, Object handle, ChunkWorkKey workKey, int stackCount) {
    return indexed != null
        && indexed.stackCount() == stackCount
        && indexed.chunkKey().equals(workKey)
        && indexed.reference().get() == handle;
  }

  private void removeIndexed(GameEntity entity) {
    UUID entityId = entity.id();
    if (entityId == null) {
      return;
    }
    presentationRefreshes.remove(entityId);
    if (!indexedEntities.containsKey(entityId)) {
      return;
    }
    synchronized (this) {
      IndexedStackEntity indexed = indexedEntities.remove(entityId);
      if (indexed != null) {
        removeIndexedFromChunk(indexed);
      }
    }
  }

  private void removeIndexedFromChunk(IndexedStackEntity indexed) {
    Map<UUID, IndexedStackEntity> chunk = indexedChunks.get(indexed.chunkKey());
    if (chunk == null) {
      return;
    }
    chunk.remove(indexed.entityId(), indexed);
    if (chunk.isEmpty()) {
      indexedChunks.remove(indexed.chunkKey(), chunk);
    }
  }

  private void addCanonicalNeighborAnchors(UUID worldId, long chunkKey, Set<Long> pending) {
    int chunkRadius = neighborChunkRadius();
    if (chunkRadius == 0) {
      return;
    }

    int chunkX = chunkKeyX(chunkKey);
    int chunkZ = chunkKeyZ(chunkKey);
    int inspected = 0;
    for (int distance = 1; distance <= chunkRadius && inspected < MAX_NEIGHBOR_CHUNKS_PER_WORK; distance++) {
      for (int offsetX = -distance; offsetX <= distance && inspected < MAX_NEIGHBOR_CHUNKS_PER_WORK; offsetX++) {
        for (int offsetZ = -distance; offsetZ <= distance && inspected < MAX_NEIGHBOR_CHUNKS_PER_WORK; offsetZ++) {
          if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != distance) {
            continue;
          }
          inspected++;
          long neighborKey = packChunkKey(chunkX + offsetX, chunkZ + offsetZ);
          if (Long.compare(neighborKey, chunkKey) >= 0) {
            continue;
          }
          Map<UUID, IndexedStackEntity> neighbor = indexedChunks.get(new ChunkWorkKey(worldId, neighborKey));
          if (neighbor != null && !neighbor.isEmpty()) {
            pending.add(neighborKey);
          }
        }
      }
    }
  }

  @Override
  public void onTick() {
    long generation = lifecycleGeneration.get();
    if (!isActive(generation)) {
      dirtyChunks.clear();
      return;
    }

    pruneQuietChunks(System.currentTimeMillis());
    if (dirtyChunks.isEmpty()) {
      return;
    }

    int availableSubmissions = MAX_CHUNK_SUBMISSIONS_PER_TICK - countInFlightChunks();
    if (availableSubmissions <= 0) {
      return;
    }

    WorldHost worldHost = worlds();
    if (worldHost == null) {
      return;
    }
    boolean folia = J.isFoliaThreading();
    List<ChunkClaim> claims = new ArrayList<>(availableSubmissions);
    int submitted = 0;
    int inspected = 0;
    for (Map.Entry<UUID, Set<Long>> entry : dirtyChunks.entrySet()) {
      if (!isActive(generation)
          || submitted >= availableSubmissions
          || inspected >= MAX_CHUNK_INSPECTIONS_PER_TICK) {
        break;
      }

      UUID worldId = entry.getKey();
      Set<Long> pending = entry.getValue();
      if (pending == null || pending.isEmpty()) {
        continue;
      }

      ReactWorld world = worldHost.world(worldId).orElse(null);
      if (world == null) {
        pending.clear();
        chunkWork.keySet().removeIf(workKey -> workKey.worldId().equals(worldId));
        continue;
      }

      Iterator<Long> pendingIterator = pending.iterator();
      while (isActive(generation)
          && submitted < availableSubmissions
          && inspected < MAX_CHUNK_INSPECTIONS_PER_TICK
          && pendingIterator.hasNext()) {
        Long boxedKey = pendingIterator.next();
        inspected++;
        if (boxedKey == null) {
          continue;
        }

        long key = boxedKey;
        if (!claimChunk(worldId, pending, key)) {
          continue;
        }

        claims.add(new ChunkClaim(worldId, world, key, generation));
        submitted++;
      }
    }

    if (claims.isEmpty()) {
      return;
    }
    for (ChunkClaim claim : claims) {
      if (folia) {
        submitFoliaChunkClaim(claim);
      } else {
        submitPaperChunkClaim(claim);
      }
    }
  }

  private void submitPaperChunkClaim(ChunkClaim claim) {
    try {
      J.s(() -> runChunkClaim(claim));
    } catch (Throwable throwable) {
      ReactPlatforms.get().reportError(throwable);
      completeChunkClaim(claim, false);
    }
  }

  private int countInFlightChunks() {
    int count = 0;
    for (Set<Long> inFlight : inFlightChunks.values()) {
      count += inFlight.size();
      if (count >= MAX_CHUNK_SUBMISSIONS_PER_TICK) {
        return MAX_CHUNK_SUBMISSIONS_PER_TICK;
      }
    }
    return count;
  }

  private boolean claimChunk(UUID worldId, Set<Long> pending, long key) {
    Set<Long> existing = inFlightChunks.get(worldId);
    if (existing != null && existing.contains(key)) {
      return false;
    }
    if (!pending.remove(key)) {
      return false;
    }

    Set<Long> inFlight = inFlightChunks.computeIfAbsent(worldId, ignored -> ConcurrentHashMap.newKeySet());
    if (inFlight.add(key)) {
      return true;
    }

    pending.add(key);
    return false;
  }

  private void submitFoliaChunkClaim(ChunkClaim claim) {
    boolean scheduled = false;
    try {
      scheduled = J.runChunk(
          claim.world().handle(),
          chunkKeyX(claim.key()),
          chunkKeyZ(claim.key()),
          () -> runChunkClaim(claim)
      );
    } catch (Throwable throwable) {
      ReactPlatforms.get().reportError(throwable);
    } finally {
      if (!scheduled) {
        completeChunkClaim(claim, false);
      }
    }
  }

  private void runChunkClaim(ChunkClaim claim) {
    boolean completed = false;
    try {
      if (!isActive(claim.generation())) {
        return;
      }
      completed = stackChunk(claim.world(), chunkKeyX(claim.key()), chunkKeyZ(claim.key()));
    } catch (Throwable throwable) {
      chunkWork.remove(new ChunkWorkKey(claim.worldId(), claim.key()));
      ReactPlatforms.get().reportError(throwable);
    } finally {
      completeChunkClaim(claim, completed);
    }
  }

  private void completeChunkClaim(ChunkClaim claim, boolean completed) {
    if (claim.generation() != lifecycleGeneration.get()) {
      return;
    }

    Set<Long> inFlight = inFlightChunks.get(claim.worldId());
    if (inFlight != null) {
      inFlight.remove(claim.key());
    }
    if (!completed && isActive(claim.generation())) {
      dirtyChunks.computeIfAbsent(claim.worldId(), ignored -> ConcurrentHashMap.newKeySet())
          .add(claim.key());
    }
  }

  private boolean stackChunk(ReactWorld world, int chunkX, int chunkZ) {
    ChunkWorkKey workKey = new ChunkWorkKey(world.id(), packChunkKey(chunkX, chunkZ));
    MobStackHost host = stacks();
    EntityHost entityHost = entities();
    if (!active || host == null || entityHost == null || !host.isChunkLoaded(world, chunkX, chunkZ)) {
      chunkWork.remove(workKey);
      return true;
    }

    ChunkWork work = chunkWork.get(workKey);
    if (work == null) {
      List<GameEntity> chunkEntities = entityHost.chunkEntities(world, chunkX, chunkZ);
      GameEntity[] candidates = collectChunkCandidates(workKey, chunkX, chunkZ, chunkEntities);
      if (candidates.length < 2) {
        recordPassOutcome(workKey, 0);
        return true;
      }

      ChunkWork created = new ChunkWork(candidates);
      ChunkWork existing = chunkWork.putIfAbsent(workKey, created);
      work = existing == null ? created : existing;
    }

    boolean completed = advanceChunkWork(work, J.isFoliaThreading());
    if (completed) {
      chunkWork.remove(workKey, work);
      recordPassOutcome(workKey, work.merges);
    }
    return completed;
  }

  private GameEntity[] collectChunkCandidates(
      ChunkWorkKey anchorKey,
      int chunkX,
      int chunkZ,
      List<GameEntity> exactChunkEntities
  ) {
    int chunkRadius = neighborChunkRadius();
    if (chunkRadius == 0) {
      return exactChunkEntities.toArray(new GameEntity[0]);
    }

    List<GameEntity> candidates = new ArrayList<>(exactChunkEntities.size() + MAX_NEIGHBOR_ENTITIES_PER_WORK);
    Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (GameEntity entity : exactChunkEntities) {
      if (seen.add(entity.handle())) {
        candidates.add(entity);
      }
    }

    EntityHost entityHost = entities();
    int inspectedChunks = 0;
    int addedEntities = 0;
    for (int distance = 1;
         distance <= chunkRadius
             && inspectedChunks < MAX_NEIGHBOR_CHUNKS_PER_WORK
             && addedEntities < MAX_NEIGHBOR_ENTITIES_PER_WORK;
         distance++) {
      for (int offsetX = -distance;
           offsetX <= distance
               && inspectedChunks < MAX_NEIGHBOR_CHUNKS_PER_WORK
               && addedEntities < MAX_NEIGHBOR_ENTITIES_PER_WORK;
           offsetX++) {
        for (int offsetZ = -distance;
             offsetZ <= distance
                 && inspectedChunks < MAX_NEIGHBOR_CHUNKS_PER_WORK
                 && addedEntities < MAX_NEIGHBOR_ENTITIES_PER_WORK;
             offsetZ++) {
          if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != distance) {
            continue;
          }
          inspectedChunks++;
          long neighbor = packChunkKey(chunkX + offsetX, chunkZ + offsetZ);
          if (Long.compare(neighbor, anchorKey.key()) <= 0) {
            continue;
          }

          ChunkWorkKey neighborKey = new ChunkWorkKey(anchorKey.worldId(), neighbor);
          Map<UUID, IndexedStackEntity> indexed = indexedChunks.get(neighborKey);
          if (indexed == null) {
            continue;
          }
          for (IndexedStackEntity candidate : indexed.values()) {
            if (addedEntities >= MAX_NEIGHBOR_ENTITIES_PER_WORK) {
              break;
            }
            if (!candidate.chunkKey().equals(neighborKey)) {
              continue;
            }
            Object handle = candidate.reference().get();
            if (handle == null) {
              indexed.remove(candidate.entityId(), candidate);
              indexedEntities.remove(candidate.entityId(), candidate);
              continue;
            }
            if (seen.add(handle)) {
              candidates.add(entityHost.wrap(handle));
              addedEntities++;
            }
          }
          if (indexed.isEmpty()) {
            indexedChunks.remove(neighborKey, indexed);
          }
        }
      }
    }
    return candidates.toArray(new GameEntity[0]);
  }

  private int neighborChunkRadius() {
    return Math.max(0, (int) Math.ceil(Math.max(0D, searchRadius) / 16D));
  }

  private boolean advanceChunkWork(ChunkWork work, boolean folia) {
    MobStackHost host = stacks();
    EntityHost protection = entities();
    int entitiesRemaining = MAX_ENTITIES_PER_CHUNK_CALLBACK;
    while (work.scanCursor < work.chunkEntities.length && entitiesRemaining > 0) {
      GameEntity entity = work.chunkEntities[work.scanCursor++];
      entitiesRemaining--;
      if (folia && !J.isOwnedByCurrentRegion(entity.handle())) {
        continue;
      }
      if (!isStackCandidate(host, entity)) {
        continue;
      }

      work.buildingBuckets.computeIfAbsent(host.mergeBucket(entity), ignored -> new ArrayList<>()).add(entity);
    }
    if (work.scanCursor < work.chunkEntities.length) {
      return false;
    }

    if (work.buckets == null) {
      work.buckets = new ArrayList<>(work.buildingBuckets.values());
      work.buildingBuckets.clear();
      work.startCollapse();
    }

    int comparisonsRemaining = MAX_COMPARISONS_PER_CHUNK_CALLBACK;
    while (work.bucketCursor < work.buckets.size()) {
      List<GameEntity> bucket = work.buckets.get(work.bucketCursor);
      if (bucket.size() < 2) {
        work.nextBucket();
        continue;
      }

      if (work.entityCursor >= bucket.size()) {
        work.nextBucket();
        continue;
      }

      if (work.currentEntity == null) {
        if (entitiesRemaining <= 0) {
          return false;
        }
        entitiesRemaining--;
        GameEntity entity = bucket.get(work.entityCursor);
        if (folia && !J.isOwnedByCurrentRegion(entity.handle())) {
          work.finishCurrent(false);
          continue;
        }
        if (host.isDead(entity)) {
          work.finishCurrent(false);
          continue;
        }

        boolean protectedEntity = protection != null && protection.isProtected(entity, ReactOperation.STACK);
        GameLocation location = entity.location();
        work.beginCurrent(entity, location, protectedEntity);
        if (protectedEntity || location == null) {
          work.finishCurrent(location != null);
          continue;
        }
      }

      if (folia && !J.isOwnedByCurrentRegion(work.currentEntity.handle())) {
        work.finishCurrent(false);
        continue;
      }
      if (host.isDead(work.currentEntity)) {
        work.finishCurrent(false);
        continue;
      }

      boolean merged = false;
      while (work.survivorCursor < work.survivors.size()) {
        if (comparisonsRemaining <= 0) {
          return false;
        }
        comparisonsRemaining--;

        int survivorIndex = work.survivorCursor++;
        if (work.survivorProtection[survivorIndex]) {
          continue;
        }

        GameEntity survivor = work.survivors.get(survivorIndex);
        if (folia && !J.isOwnedByCurrentRegion(survivor.handle())) {
          continue;
        }
        if (host.isDead(survivor)) {
          continue;
        }

        GameLocation to = work.survivorLocations.get(survivorIndex);
        if (!withinMergeRadius(
            work.currentLocation.x(),
            work.currentLocation.y(),
            work.currentLocation.z(),
            to.x(),
            to.y(),
            to.z(),
            searchRadius
        )) {
          continue;
        }

        if (merge(work.currentEntity, survivor)) {
          merged = true;
          work.merges++;
          break;
        }
      }

      work.finishCurrent(!merged);
    }

    return true;
  }

  private boolean isStackCandidate(MobStackHost host, GameEntity entity) {
    return host.isLiving(entity)
        && !entity.isPlayer()
        && isStackableType(entity.type())
        && !host.isDead(entity)
        && !host.isTamedPet(entity);
  }

  private boolean isActive(long generation) {
    return active && generation == lifecycleGeneration.get();
  }

  private record ChunkClaim(UUID worldId, ReactWorld world, long key, long generation) {
  }

  private record ChunkWorkKey(UUID worldId, long key) {
  }

  private record IndexedStackEntity(
      UUID entityId,
      ChunkWorkKey chunkKey,
      WeakReference<Object> reference,
      int stackCount
  ) {
  }

  private static final class ChunkWork {
    private final GameEntity[] chunkEntities;
    private final Map<Object, List<GameEntity>> buildingBuckets;
    private List<List<GameEntity>> buckets;
    private List<GameEntity> survivors;
    private List<GameLocation> survivorLocations;
    private boolean[] survivorProtection;
    private int scanCursor;
    private int merges;
    private int bucketCursor;
    private int entityCursor;
    private int survivorCursor;
    private GameEntity currentEntity;
    private GameLocation currentLocation;
    private boolean currentProtection;

    private ChunkWork(GameEntity[] chunkEntities) {
      this.chunkEntities = chunkEntities;
      this.buildingBuckets = new HashMap<>();
      this.survivors = new ArrayList<>();
      this.survivorLocations = new ArrayList<>();
      this.survivorProtection = new boolean[0];
    }

    private void beginCurrent(GameEntity entity, GameLocation location, boolean protectedEntity) {
      currentEntity = entity;
      currentLocation = location;
      currentProtection = protectedEntity;
      survivorCursor = 0;
    }

    private void startCollapse() {
      survivorProtection = buckets.isEmpty() ? new boolean[0] : new boolean[buckets.get(0).size()];
    }

    private void finishCurrent(boolean keep) {
      if (keep && currentEntity != null) {
        int survivorIndex = survivors.size();
        survivorProtection[survivorIndex] = currentProtection;
        survivors.add(currentEntity);
        survivorLocations.add(currentLocation);
      }
      currentEntity = null;
      currentLocation = null;
      currentProtection = false;
      survivorCursor = 0;
      entityCursor++;
    }

    private void nextBucket() {
      bucketCursor++;
      entityCursor = 0;
      survivorCursor = 0;
      currentEntity = null;
      currentLocation = null;
      currentProtection = false;
      survivors = new ArrayList<>();
      survivorLocations = new ArrayList<>();
      survivorProtection = bucketCursor < buckets.size()
          ? new boolean[buckets.get(bucketCursor).size()]
          : new boolean[0];
    }
  }

  private record StackableIndex(Set<String> source, Set<String> types) {
  }

  @Override
  public void onDeactivate() {
    active = false;
    lifecycleGeneration.incrementAndGet();
    synchronized (this) {
      ticks.cancel();
      ticks = Subscription.NONE;
    }
    dirtyChunks.clear();
    inFlightChunks.clear();
    chunkWork.clear();
    indexedEntities.clear();
    indexedChunks.clear();
    quietChunks.clear();
    presentationRefreshes.clear();
  }

  @Override
  public int getTickInterval() {
    return Math.max(50, batchIntervalMs);
  }
}
