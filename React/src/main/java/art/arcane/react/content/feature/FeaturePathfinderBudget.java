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
import art.arcane.react.api.feature.FeatureIntegrityListener;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.feature.perworld.PerWorldPressure;
import art.arcane.react.content.feature.perworld.ReactScopedPressure;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.bridge.BridgeKind;
import art.arcane.react.core.bridge.NmsBridgeDescriptor;
import art.arcane.react.core.bridge.NmsBridgeHandle;
import art.arcane.react.core.bridge.NmsBridgeRegistry;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Pathfinder Budget feature. Shrinks the A* node budget of mobs away from players while the server is under load, making every path search cheaper without changing where mobs try to go. Budgets are restored when mobs come near players, when the server recovers, on entity unload, or on deactivation. Fails closed to vanilla pathfinding if the NMS navigation bridges are unavailable.")
public class FeaturePathfinderBudget extends ReactFeature implements FeatureIntegrityListener {
  private static final int MAX_INDEXED_MOBS = 65_536;
  public static final String ID = "pathfinder-budget";
  public static final String BRIDGE_GET_NAVIGATION = "Mob.getNavigation";
  public static final String BRIDGE_SET_MAX_VISITED = "PathNavigation.setMaxVisitedNodesMultiplier";
  public static final String BRIDGE_RESET_MAX_VISITED = "PathNavigation.resetMaxVisitedNodesMultiplier";
  private static final NamespacedKey NS_BUDGETED = new NamespacedKey(React.instance, "react-path-budget");
  private static final int CLAIM_CLEANUP_BATCH_SIZE = 256;
  private static final long CLAIM_CLEANUP_TIMEOUT_MS = 30_000L;

  private transient final AtomicBoolean pathfinderScanQueued = new AtomicBoolean(false);
  private transient final AtomicBoolean loadEngaged = new AtomicBoolean(false);
  private transient final AtomicBoolean runtimeFailure = new AtomicBoolean(false);
  private transient final AtomicInteger nextPaperWorld = new AtomicInteger(0);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient final Map<UUID, IndexedMob> indexedMobs = new ConcurrentHashMap<>();
  private transient final Queue<UUID> indexedMobOrder = new ConcurrentLinkedQueue<>();
  private transient final Consumer<Entity> entityTickListener = this::indexMob;
  private transient final ConcurrentHashMap<UUID, BudgetClaim> budgetedMobs = new ConcurrentHashMap<>();
  private transient final ReferenceQueue<Mob> staleMobReferences = new ReferenceQueue<>();
  private transient final Queue<ClaimReleaseSweep> claimReleaseSweeps = new ConcurrentLinkedQueue<>();
  private transient final AtomicBoolean claimCleanupDraining = new AtomicBoolean(false);
  private transient final AtomicInteger claimCleanupOperations = new AtomicInteger(0);
  private transient final AtomicLong claimCleanupFailures = new AtomicLong(0L);
  private transient final Object claimCleanupMonitor = new Object();
  private transient final AtomicInteger ownerScanOperations = new AtomicInteger(0);
  private transient final Object ownerScanMonitor = new Object();
  private transient final Object lifecycleMutationLock = new Object();
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for pathfinder budget in milliseconds.", impact = "Lower values react faster but consume more CPU; higher values reduce overhead but react later.")
  private int tickIntervalMS = 1000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum mobs sampled per cycle.", impact = "Higher values converge on the mob population faster at more per-cycle cost.")
  private int maxEntitiesSampledPerCycle = 240;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds required before pathfinding budgets shrink.", impact = "Lower values shed pathfinding cost earlier; higher values reserve it for heavier load.")
  private double engageTickTimeMs = 48;
  @art.arcane.react.util.project.config.ConfigDoc(value = "A* visited-node budget multiplier applied to distant mobs while engaged (vanilla is 1.0).", impact = "Lower values make path searches cheaper but produce worse long-distance paths for affected mobs.")
  private double budgetMultiplier = 0.4;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Mobs within this distance of a player always keep their full pathfinding budget (blocks).", impact = "Higher values protect more mobs from budgeting; lower values shed more pathfinding cost near players.")
  private double fullBudgetWithinDistance = 16;
  private transient volatile NmsBridgeHandle bridgeGetNavigation;
  private transient volatile NmsBridgeHandle bridgeSetMaxVisited;
  private transient volatile NmsBridgeHandle bridgeResetMaxVisited;
  private transient volatile EntityController registeredController;
  private transient volatile boolean bridgesAvailable;
  private transient volatile boolean active;
  private transient volatile double lastTickMs;

  public FeaturePathfinderBudget() {
    super(ID);
  }

  public static List<NmsBridgeDescriptor> pathfinderBridgeDescriptors() {
    List<String> mobClasses = List.of(
        "net.minecraft.world.entity.Mob",
        "net.minecraft.world.entity.EntityInsentient");
    List<String> navigationClasses = List.of(
        "net.minecraft.world.entity.ai.navigation.PathNavigation",
        "net.minecraft.world.entity.ai.navigation.NavigationAbstract");
    return List.of(
        new NmsBridgeDescriptor(
            BRIDGE_GET_NAVIGATION, BridgeKind.METHOD, mobClasses, "getNavigation",
            List.of(List.of()),
            navigationClasses.get(0),
            Optional.of("Mob.getNavigation")),
        new NmsBridgeDescriptor(
            BRIDGE_SET_MAX_VISITED, BridgeKind.METHOD, navigationClasses, "setMaxVisitedNodesMultiplier",
            List.of(List.of("float")),
            "void",
            Optional.of("PathNavigation.setMaxVisitedNodesMultiplier")),
        new NmsBridgeDescriptor(
            BRIDGE_RESET_MAX_VISITED, BridgeKind.METHOD, navigationClasses, "resetMaxVisitedNodesMultiplier",
            List.of(List.of()),
            "void",
            Optional.of("PathNavigation.resetMaxVisitedNodesMultiplier"))
    );
  }

  @Override
  public void onActivate() {
    lifecycleGeneration.incrementAndGet();
    active = false;
    pathfinderScanQueued.set(false);
    loadEngaged.set(false);
    runtimeFailure.set(false);
    nextPaperWorld.set(0);
    indexedMobs.clear();
    indexedMobOrder.clear();
    lastTickMs = 0D;
    if (!resolveBridges()) {
      setEnabled(false);
      React.warn("Pathfinder Budget disabled: NMS navigation bridges unavailable on this server software.");
      return;
    }

    active = true;
    if (J.isFoliaThreading()) {
      EntityController controller = React.controller(EntityController.class);
      if (controller != null) {
        controller.registerEntityTickListener(entityTickListener);
        registeredController = controller;
      }
    }
  }

  @Override
  public void onDeactivate() {
    long deactivatedGeneration;
    synchronized (lifecycleMutationLock) {
      active = false;
      loadEngaged.set(false);
      deactivatedGeneration = lifecycleGeneration.getAndIncrement();
      pathfinderScanQueued.set(false);
    }

    EntityController controller = registeredController;
    registeredController = null;
    if (controller != null) {
      controller.unregisterEntityTickListener(entityTickListener);
    }
    indexedMobs.clear();
    indexedMobOrder.clear();
    if (!awaitOwnerScans(CLAIM_CLEANUP_TIMEOUT_MS)) {
      throw new IllegalStateException(
          "Pathfinder owner scans did not drain within " + CLAIM_CLEANUP_TIMEOUT_MS + "ms"
      );
    }
    releaseGeneration(deactivatedGeneration);
    if (!awaitClaimCleanup(CLAIM_CLEANUP_TIMEOUT_MS)) {
      throw new IllegalStateException(
          "Pathfinder budget cleanup did not drain within " + CLAIM_CLEANUP_TIMEOUT_MS + "ms"
      );
    }
  }

  @Override
  public int getTickInterval() {
    return Math.max(250, tickIntervalMS);
  }

  @Override
  public void onTick() {
    if (!active || !bridgesAvailable) {
      return;
    }

    drainStaleClaims();
    lastTickMs = sampleTickMs();
    long generation = lifecycleGeneration.get();
    boolean engaged = lastTickMs >= engageTickTimeMs || ReactScopedPressure.isAnyPressure();
    boolean previouslyEngaged = loadEngaged.getAndSet(engaged);
    if (previouslyEngaged && !engaged) {
      releaseGeneration(generation);
    }
    if (!pathfinderScanQueued.compareAndSet(false, true)) {
      return;
    }

    if (J.isFoliaThreading()) {
      scheduleFoliaScan(generation);
      return;
    }

    J.s(() -> {
      try {
        applyScan(generation);
      } finally {
        finishScan(generation);
      }
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesLoadEvent event) {
    long generation = lifecycleGeneration.get();
    for (Entity entity : event.getEntities()) {
      if (entity instanceof Mob mob) {
        reconcileLoadedMob(mob, generation);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesUnloadEvent event) {
    for (Entity entity : event.getEntities()) {
      if (entity instanceof Mob mob) {
        releaseUnloadingMob(mob);
      }
    }
  }

  private void applyScan(long generation) {
    if (!isCurrent(generation)) {
      return;
    }

    List<World> worlds = Bukkit.getWorlds();
    if (worlds.isEmpty()) {
      return;
    }

    int remaining = Math.max(1, maxEntitiesSampledPerCycle);
    int worldStart = Math.floorMod(nextPaperWorld.getAndIncrement(), worlds.size());
    for (int worldOffset = 0; worldOffset < worlds.size(); worldOffset++) {
      if (!isCurrent(generation) || remaining <= 0) {
        return;
      }

      World world = worlds.get((worldStart + worldOffset) % worlds.size());
      List<Entity> entities = WorldEntitySnapshots.next(world, remaining);
      if (entities.isEmpty()) {
        continue;
      }

      for (Entity entity : entities) {
        if (!isCurrent(generation)) {
          return;
        }
        remaining--;
        manageEntity(entity, generation);
      }
    }
  }

  private void scheduleFoliaScan(long generation) {
    if (!isCurrent(generation)) {
      finishScan(generation);
      return;
    }

    int budget = Math.max(1, maxEntitiesSampledPerCycle);
    AtomicInteger pending = new AtomicInteger(1);
    for (int i = 0; i < budget; i++) {
      UUID entityId = indexedMobOrder.poll();
      if (entityId == null) {
        break;
      }

      IndexedMob candidate = indexedMobs.get(entityId);
      if (candidate == null || candidate.generation != generation) {
        if (candidate != null) {
          indexedMobs.remove(entityId, candidate);
        }
        continue;
      }

      Mob mob = candidate.reference.get();
      if (mob == null) {
        indexedMobs.remove(entityId, candidate);
        continue;
      }

      indexedMobOrder.offer(entityId);
      pending.incrementAndGet();
      ownerScanOperations.incrementAndGet();
      AtomicBoolean completionClaimed = new AtomicBoolean(false);
      Runnable completed = () -> {
        if (completionClaimed.compareAndSet(false, true)) {
          completeOwnerScan();
          finishFoliaCandidate(pending, generation);
        }
      };
      Runnable retired = () -> {
        indexedMobs.remove(entityId, candidate);
        completed.run();
      };
      try {
        boolean scheduled = J.runEntity(
            mob,
            () -> {
              try {
                manageEntity(mob, generation);
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

  private void manageEntity(Entity entity, long generation) {
    if (!isCurrent(generation) || entity == null) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity)) {
      return;
    }

    if (!(entity instanceof Mob mob) || entity.isDead()) {
      return;
    }

    try {
      BudgetClaim claim = reconcileClaimState(mob, generation);
      boolean engagedLoad = lastTickMs >= engageTickTimeMs || PerWorldPressure.get(mob.getWorld()).isPressure();
      if (!engagedLoad || React.hasNearbyPlayer(mob.getLocation(), fullBudgetWithinDistance)) {
        if (claim != null || hasMarker(mob)) {
          restoreBudget(mob, generation);
        }
        return;
      }

      if (claim == null) {
        applyBudget(mob, generation);
      }
    } catch (Throwable failure) {
      failRuntime(failure);
    }
  }

  private BudgetClaim reconcileClaimState(Mob mob, long generation) throws Throwable {
    UUID entityId = mob.getUniqueId();
    BudgetClaim claim = budgetedMobs.get(entityId);
    if (isCurrentClaim(claim, mob, generation)) {
      return claim;
    }

    if (claim != null && budgetedMobs.remove(entityId, claim)) {
      resetBudgetState(mob);
    } else if (claim == null && hasMarker(mob) && !budgetedMobs.containsKey(entityId)) {
      resetBudgetState(mob);
    }
    return null;
  }

  private void applyBudget(Mob mob, long generation) throws Throwable {
    synchronized (lifecycleMutationLock) {
      if (!isCurrent(generation)) {
        return;
      }

      UUID entityId = mob.getUniqueId();
      BudgetedMobReference reference = new BudgetedMobReference(mob, staleMobReferences);
      BudgetClaim claim = new BudgetClaim(generation, reference);
      if (budgetedMobs.putIfAbsent(entityId, claim) != null) {
        return;
      }

      try {
        Object navigation = navigationOf(mob);
        if (navigation == null) {
          budgetedMobs.remove(entityId, claim);
          return;
        }

        float multiplier = (float) Math.max(0.05D, Math.min(1D, budgetMultiplier));
        bridgeSetMaxVisited.methodHandle().invokeWithArguments(navigation, multiplier);
        mob.getPersistentDataContainer().set(NS_BUDGETED, PersistentDataType.BYTE, (byte) 1);
      } catch (Throwable failure) {
        if (budgetedMobs.remove(entityId, claim)) {
          try {
            resetBudgetState(mob);
          } catch (Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
          }
        }
        throw failure;
      }
    }
  }

  private void restoreBudget(Mob mob, long generation) throws Throwable {
    UUID entityId = mob.getUniqueId();
    BudgetClaim claim = budgetedMobs.get(entityId);
    if (isCurrentClaim(claim, mob, generation)) {
      if (budgetedMobs.remove(entityId, claim)) {
        resetBudgetState(mob);
      }
      return;
    }

    if (claim != null && budgetedMobs.remove(entityId, claim)) {
      resetBudgetState(mob);
      return;
    }

    if (hasMarker(mob) && !budgetedMobs.containsKey(entityId)) {
      resetBudgetState(mob);
    }
  }

  private void resetBudgetState(Mob mob) throws Throwable {
    Throwable failure = null;
    try {
      Object navigation = navigationOf(mob);
      if (navigation != null) {
        bridgeResetMaxVisited.methodHandle().invokeWithArguments(navigation);
      }
    } catch (Throwable throwable) {
      failure = throwable;
    } finally {
      mob.getPersistentDataContainer().remove(NS_BUDGETED);
    }

    if (failure != null) {
      throw failure;
    }
  }

  private Object navigationOf(Mob mob) throws Throwable {
    Object handle = mob.getClass().getMethod("getHandle").invoke(mob);
    if (handle == null) {
      return null;
    }

    return bridgeGetNavigation.methodHandle().invokeWithArguments(handle);
  }

  private void releaseGeneration(long generation) {
    drainStaleClaims();
    synchronized (claimCleanupMonitor) {
      if (claimReleaseSweeps.isEmpty()
          && !claimCleanupDraining.get()
          && claimCleanupOperations.get() == 0) {
        claimCleanupFailures.set(0L);
      }
      claimReleaseSweeps.add(new ClaimReleaseSweep(budgetedMobs.entrySet().iterator(), generation));
    }
    startClaimCleanupDrain();
  }

  private boolean awaitClaimCleanup(long timeoutMillis) {
    startClaimCleanupDrain();
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
    synchronized (claimCleanupMonitor) {
      while (!claimReleaseSweeps.isEmpty()
          || claimCleanupDraining.get()
          || claimCleanupOperations.get() > 0) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) {
          return false;
        }

        try {
          TimeUnit.NANOSECONDS.timedWait(claimCleanupMonitor, remaining);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
    }
    return claimCleanupFailures.get() == 0L;
  }

  private void startClaimCleanupDrain() {
    if (claimReleaseSweeps.isEmpty() || !claimCleanupDraining.compareAndSet(false, true)) {
      return;
    }
    drainClaimCleanup();
  }

  private void drainClaimCleanup() {
    while (true) {
      int remaining = CLAIM_CLEANUP_BATCH_SIZE;
      ClaimCleanupBatch batch = new ClaimCleanupBatch();
      while (remaining > 0) {
        ClaimReleaseSweep sweep = claimReleaseSweeps.peek();
        if (sweep == null) {
          break;
        }

        remaining -= sweep.drain(remaining, batch);
        if (sweep.isComplete()) {
          claimReleaseSweeps.poll();
        }
      }

      if (!batch.seal()) {
        return;
      }
      if (claimReleaseSweeps.isEmpty()) {
        claimCleanupDraining.set(false);
        signalClaimCleanup();
        if (!claimReleaseSweeps.isEmpty()) {
          startClaimCleanupDrain();
        }
        return;
      }
    }
  }

  private void scheduleClaimRelease(BudgetClaim claim, Consumer<Boolean> completion) {
    AtomicBoolean completionClaimed = new AtomicBoolean(false);
    Consumer<Boolean> exactCompletion = succeeded -> {
      if (completionClaimed.compareAndSet(false, true)) {
        completion.accept(succeeded);
      }
    };
    Mob mob = claim.mobReference.get();
    if (mob == null) {
      budgetedMobs.remove(claim.mobReference.entityId, claim);
      exactCompletion.accept(true);
      return;
    }

    Runnable release = () -> completeClaimRelease(claim, mob, exactCompletion);
    Runnable retired = () -> {
      budgetedMobs.remove(claim.mobReference.entityId, claim);
      exactCompletion.accept(true);
    };
    boolean folia = J.isFoliaThreading();
    if ((folia && J.isOwnedByCurrentRegion(mob)) || (!folia && J.isPrimaryThread())) {
      release.run();
      return;
    }

    if (!J.runEntity(mob, release, 0, retired)) {
      exactCompletion.accept(false);
    }
  }

  private void completeClaimRelease(BudgetClaim claim, Mob mob, Consumer<Boolean> completion) {
    boolean succeeded = false;
    try {
      releaseClaimOrThrow(claim, mob);
      succeeded = true;
    } catch (Throwable failure) {
      React.reportError(failure);
    } finally {
      completion.accept(succeeded);
    }
  }

  private void releaseClaimOrThrow(BudgetClaim claim, Mob mob) throws Throwable {
    if (budgetedMobs.get(claim.mobReference.entityId) != claim) {
      return;
    }
    resetBudgetState(mob);
    budgetedMobs.remove(claim.mobReference.entityId, claim);
  }

  private void releaseClaim(BudgetClaim claim, Mob mob) {
    try {
      releaseClaimOrThrow(claim, mob);
    } catch (Throwable failure) {
      React.reportError(failure);
    }
  }

  private void signalClaimCleanup() {
    synchronized (claimCleanupMonitor) {
      claimCleanupMonitor.notifyAll();
    }
  }

  private void reconcileLoadedMob(Mob mob, long generation) {
    if (mob == null || generation != lifecycleGeneration.get()) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(mob)) {
      J.runEntity(mob, () -> reconcileLoadedMob(mob, generation));
      return;
    }

    if (!resolveBridges()) {
      return;
    }

    try {
      BudgetClaim claim = budgetedMobs.get(mob.getUniqueId());
      if (active && isCurrentClaim(claim, mob, generation)) {
        return;
      }
      if (claim != null) {
        releaseClaim(claim, mob);
      } else if (hasMarker(mob) && !budgetedMobs.containsKey(mob.getUniqueId())) {
        resetBudgetState(mob);
      }
    } catch (Throwable failure) {
      if (active) {
        failRuntime(failure);
      } else {
        React.reportError(failure);
      }
    }
  }

  private void releaseUnloadingMob(Mob mob) {
    if (mob == null) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(mob)) {
      UUID entityId = mob.getUniqueId();
      BudgetClaim claim = budgetedMobs.get(entityId);
      Runnable retired = () -> {
        if (claim != null) {
          budgetedMobs.remove(entityId, claim);
        }
      };
      if (!J.runEntity(mob, () -> releaseUnloadingMob(mob), 0, retired)) {
        retired.run();
      }
      return;
    }

    if (!resolveBridges()) {
      return;
    }

    try {
      BudgetClaim claim = budgetedMobs.get(mob.getUniqueId());
      if (claim != null) {
        releaseClaim(claim, mob);
      } else if (hasMarker(mob)) {
        resetBudgetState(mob);
      }
    } catch (Throwable failure) {
      React.reportError(failure);
    }
  }

  private synchronized boolean resolveBridges() {
    if (bridgeGetNavigation != null && bridgeSetMaxVisited != null && bridgeResetMaxVisited != null) {
      bridgesAvailable = bridgeGetNavigation.available()
          && bridgeSetMaxVisited.available()
          && bridgeResetMaxVisited.available();
      return bridgesAvailable;
    }

    NmsBridgeRegistry registry;
    try {
      registry = React.bridgeRegistry();
    } catch (Throwable failure) {
      bridgesAvailable = false;
      return false;
    }
    if (registry == null) {
      bridgesAvailable = false;
      return false;
    }

    List<NmsBridgeDescriptor> descriptors = pathfinderBridgeDescriptors();
    bridgeGetNavigation = registry.resolve(descriptors.get(0));
    bridgeSetMaxVisited = registry.resolve(descriptors.get(1));
    bridgeResetMaxVisited = registry.resolve(descriptors.get(2));
    bridgesAvailable = bridgeGetNavigation.available()
        && bridgeSetMaxVisited.available()
        && bridgeResetMaxVisited.available();
    return bridgesAvailable;
  }

  private boolean hasMarker(Mob mob) {
    return mob.getPersistentDataContainer().getOrDefault(
        NS_BUDGETED,
        PersistentDataType.BYTE,
        (byte) 0
    ) == 1;
  }

  private boolean isCurrentClaim(BudgetClaim claim, Mob mob, long generation) {
    return claim != null && claim.generation == generation && claim.mobReference.get() == mob;
  }

  private void indexMob(Entity entity) {
    if (!(entity instanceof Mob mob)) {
      return;
    }

    long generation = lifecycleGeneration.get();
    if (!isCurrent(generation)) {
      return;
    }

    UUID entityId = mob.getUniqueId();
    IndexedMob replacement = new IndexedMob(generation, new WeakReference<>(mob));
    IndexedMob existing = indexedMobs.putIfAbsent(entityId, replacement);
    if (existing == null) {
      if (indexedMobs.size() > MAX_INDEXED_MOBS) {
        indexedMobs.remove(entityId, replacement);
        return;
      }
      indexedMobOrder.offer(entityId);
      return;
    }

    if (existing.generation != generation || existing.reference.get() != mob) {
      indexedMobs.replace(entityId, existing, replacement);
    }
  }

  private void completeOwnerScan() {
    ownerScanOperations.decrementAndGet();
    synchronized (ownerScanMonitor) {
      ownerScanMonitor.notifyAll();
    }
  }

  private boolean awaitOwnerScans(long timeoutMillis) {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
    synchronized (ownerScanMonitor) {
      while (ownerScanOperations.get() > 0) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) {
          return false;
        }

        try {
          TimeUnit.NANOSECONDS.timedWait(ownerScanMonitor, remaining);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
    }
    return true;
  }

  private void finishFoliaCandidate(AtomicInteger pending, long generation) {
    if (pending.decrementAndGet() == 0) {
      finishScan(generation);
    }
  }

  private void finishScan(long generation) {
    if (generation == lifecycleGeneration.get()) {
      pathfinderScanQueued.set(false);
    }
  }

  private boolean isCurrent(long generation) {
    return active && bridgesAvailable && generation == lifecycleGeneration.get();
  }

  private void drainStaleClaims() {
    BudgetedMobReference reference = (BudgetedMobReference) staleMobReferences.poll();
    while (reference != null) {
      BudgetClaim claim = budgetedMobs.get(reference.entityId);
      if (claim != null && claim.mobReference == reference) {
        budgetedMobs.remove(reference.entityId, claim);
      }
      reference = (BudgetedMobReference) staleMobReferences.poll();
    }
  }

  private void failRuntime(Throwable failure) {
    if (!runtimeFailure.compareAndSet(false, true)) {
      return;
    }

    active = false;
    loadEngaged.set(false);
    bridgesAvailable = false;
    long failedGeneration = lifecycleGeneration.getAndIncrement();
    pathfinderScanQueued.set(false);
    setEnabled(false);
    releaseGeneration(failedGeneration);
    React.reportError("Pathfinder Budget disabled due to runtime incompatibility: "
        + failure.getClass().getSimpleName() + ": " + failure.getMessage(), failure);
  }

  private double sampleTickMs() {
    try {
      Sampler sampler = React.sampler(SamplerTickTime.ID);
      return sampler == null ? 0D : sampler.sample();
    } catch (Throwable ignored) {
      return 0D;
    }
  }

  private final class ClaimReleaseSweep {
    private final Iterator<Map.Entry<UUID, BudgetClaim>> claims;
    private final long generation;
    private boolean complete;

    private ClaimReleaseSweep(Iterator<Map.Entry<UUID, BudgetClaim>> claims, long generation) {
      this.claims = claims;
      this.generation = generation;
    }

    private int drain(int limit, ClaimCleanupBatch batch) {
      int examined = 0;
      while (examined < limit && claims.hasNext()) {
        BudgetClaim claim = claims.next().getValue();
        if (claim.generation == generation) {
          batch.submit(claim);
        }
        examined++;
      }
      if (!claims.hasNext()) {
        complete = true;
      }
      return examined;
    }

    private boolean isComplete() {
      return complete;
    }
  }

  private final class ClaimCleanupBatch {
    private final AtomicInteger pending = new AtomicInteger(1);
    private volatile boolean sealed;

    private void submit(BudgetClaim claim) {
      pending.incrementAndGet();
      claimCleanupOperations.incrementAndGet();
      scheduleClaimRelease(claim, this::complete);
    }

    private void complete(boolean succeeded) {
      if (!succeeded) {
        claimCleanupFailures.incrementAndGet();
      }
      claimCleanupOperations.decrementAndGet();
      signalClaimCleanup();
      if (pending.decrementAndGet() == 0 && sealed) {
        drainClaimCleanup();
      }
    }

    private boolean seal() {
      sealed = true;
      return pending.decrementAndGet() == 0;
    }
  }

  private static final class BudgetClaim {
    private final long generation;
    private final BudgetedMobReference mobReference;

    private BudgetClaim(long generation, BudgetedMobReference mobReference) {
      this.generation = generation;
      this.mobReference = mobReference;
    }
  }

  private static final class IndexedMob {
    private final long generation;
    private final WeakReference<Mob> reference;

    private IndexedMob(long generation, WeakReference<Mob> reference) {
      this.generation = generation;
      this.reference = reference;
    }
  }

  private static final class BudgetedMobReference extends WeakReference<Mob> {
    private final UUID entityId;

    private BudgetedMobReference(Mob mob, ReferenceQueue<Mob> referenceQueue) {
      super(mob, referenceQueue);
      entityId = mob.getUniqueId();
    }
  }
}
