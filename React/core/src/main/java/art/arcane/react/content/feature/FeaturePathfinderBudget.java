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
import art.arcane.react.content.feature.perworld.PerWorldPressure;
import art.arcane.react.content.feature.perworld.ReactScopedPressure;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.platform.EntityActivityHost;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
public class FeaturePathfinderBudget extends ReactFeature implements FeatureIntegrity {
  private static final int MAX_INDEXED_MOBS = 65_536;
  public static final String ID = "pathfinder-budget";
  private static final int CLAIM_CLEANUP_BATCH_SIZE = 256;
  private static final long CLAIM_CLEANUP_TIMEOUT_MS = 30_000L;

  private transient final AtomicBoolean pathfinderScanQueued = new AtomicBoolean(false);
  private transient final AtomicBoolean loadEngaged = new AtomicBoolean(false);
  private transient final AtomicBoolean runtimeFailure = new AtomicBoolean(false);
  private transient final AtomicInteger nextPaperWorld = new AtomicInteger(0);
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient final Map<UUID, IndexedMob> indexedMobs = new ConcurrentHashMap<>();
  private transient final Queue<UUID> indexedMobOrder = new ConcurrentLinkedQueue<>();
    private transient final ConcurrentHashMap<UUID, BudgetClaim> budgetedMobs = new ConcurrentHashMap<>();
  private transient final ReferenceQueue<Object> staleMobReferences = new ReferenceQueue<>();
  private transient final Queue<ClaimReleaseSweep> claimReleaseSweeps = new ConcurrentLinkedQueue<>();
  private transient final AtomicBoolean claimCleanupDraining = new AtomicBoolean(false);
  private transient final AtomicInteger claimCleanupOperations = new AtomicInteger(0);
  private transient final AtomicLong claimCleanupFailures = new AtomicLong(0L);
  private transient final AtomicLong claimCleanupEpoch = new AtomicLong(0L);
  private transient final Object claimCleanupMonitor = new Object();
  private transient volatile AtomicInteger ownerScanOperations = new AtomicInteger(0);
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
  private transient volatile EntityActivityHost activity;
  private transient volatile WorldHost worlds;
  private transient volatile boolean bridgesResolved;
  private transient volatile Subscription entityTicks = Subscription.NONE;
  private transient volatile Subscription chunkEntities = Subscription.NONE;
  private transient volatile boolean bridgesAvailable;
  private transient volatile boolean active;
  private transient volatile double lastTickMs;

  public FeaturePathfinderBudget() {
    super(ID);
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
    bridgesAvailable = resolveBridges();
    if (!bridgesAvailable) {
      setEnabled(false);
      warn("Pathfinder Budget disabled: NMS navigation bridges unavailable on this server software.");
      return;
    }

    active = true;
    subscribeChunkEntities();
    if (J.isFoliaThreading()) {
      EntityHost entities = entities();
      if (entities != null) {
        entityTicks = entities.onTick(List.of(), this::indexMob);
      }
    }
  }

  @Override
  public void onIntegrityTick() {
    subscribeChunkEntities();
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

    Subscription ticks = entityTicks;
    entityTicks = Subscription.NONE;
    ticks.cancel();
    indexedMobs.clear();
    indexedMobOrder.clear();
    if (J.isServerStopping()) {
      abandonOwnerScans();
      abandonClaimCleanup();
      releaseClaimsOnStoppingThread();
      return;
    }

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

    JobBatch batch = new JobBatch(JobBatch.scanLimits(), () -> finishScan(generation));
    J.s(() -> {
      try {
        queueScan(generation, batch);
      } finally {
        batch.seal();
      }
    });
  }

  /**
   * Opens the chunk entity load and unload hooks once for the whole run, so budgets and stale marks are reconciled
   * even while the feature is inactive.
   */
  private synchronized void subscribeChunkEntities() {
    if (chunkEntities != Subscription.NONE) {
      return;
    }
    EntityActivityHost host = activity();
    if (host == null) {
      return;
    }
    Subscription loads = host.onEntitiesLoad(this::onEntitiesLoad);
    Subscription unloads = host.onEntitiesUnload(this::onEntitiesUnload);
    chunkEntities = () -> {
      loads.cancel();
      unloads.cancel();
    };
  }

  void onEntitiesLoad(List<GameEntity> loaded) {
    long generation = lifecycleGeneration.get();
    for (GameEntity entity : loaded) {
      if (entity.isMob()) {
        reconcileLoadedMob(entity, generation);
      }
    }
  }

  void onEntitiesUnload(List<GameEntity> unloading) {
    for (GameEntity entity : unloading) {
      if (entity.isMob()) {
        releaseUnloadingMob(entity);
      }
    }
  }

  private void queueScan(long generation, JobBatch batch) {
    if (!isCurrent(generation)) {
      return;
    }

    WorldHost worldHost = worlds();
    EntityActivityHost host = activity();
    List<ReactWorld> worlds = worldHost == null ? List.of() : worldHost.worlds();
    if (worlds.isEmpty() || host == null) {
      return;
    }

    int remaining = Math.max(1, maxEntitiesSampledPerCycle);
    int worldStart = Math.floorMod(nextPaperWorld.getAndIncrement(), worlds.size());
    for (int worldOffset = 0; worldOffset < worlds.size(); worldOffset++) {
      if (!isCurrent(generation) || remaining <= 0) {
        return;
      }

      ReactWorld world = worlds.get((worldStart + worldOffset) % worlds.size());
      List<GameEntity> entities = host.nextEntities(world, remaining);
      if (entities.isEmpty()) {
        continue;
      }

      for (GameEntity entity : entities) {
        if (!isCurrent(generation)) {
          return;
        }
        remaining--;
        batch.submit(() -> manageEntity(entity, generation));
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
    AtomicInteger ownerScans = ownerScanOperations;
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

      GameEntity mob = entity(candidate.reference);
      if (mob == null) {
        indexedMobs.remove(entityId, candidate);
        continue;
      }

      indexedMobOrder.offer(entityId);
      pending.incrementAndGet();
      ownerScans.incrementAndGet();
      AtomicBoolean completionClaimed = new AtomicBoolean(false);
      Runnable completed = () -> {
        if (completionClaimed.compareAndSet(false, true)) {
          completeOwnerScan(ownerScans);
          finishFoliaCandidate(pending, generation);
        }
      };
      Runnable retired = () -> {
        indexedMobs.remove(entityId, candidate);
        completed.run();
      };
      try {
        boolean scheduled = J.runEntity(
            mob.handle(),
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
        reportError(failure);
      }
    }
    finishFoliaCandidate(pending, generation);
  }

  private void manageEntity(GameEntity entity, long generation) {
    if (!isCurrent(generation) || entity == null) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity.handle())) {
      return;
    }

    if (activity() == null || !entity.isMob() || !entity.isValid()) {
      return;
    }

    GameEntity mob = entity;
    try {
      BudgetClaim claim = reconcileClaimState(mob, generation);
      boolean engagedLoad = lastTickMs >= engageTickTimeMs || worldPressure(mob).isPressure();
      if (!engagedLoad || nearPlayer(mob, fullBudgetWithinDistance)) {
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

  private BudgetClaim reconcileClaimState(GameEntity mob, long generation) throws Throwable {
    UUID entityId = mob.id();
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

  private void applyBudget(GameEntity mob, long generation) throws Throwable {
    synchronized (lifecycleMutationLock) {
      if (!isCurrent(generation)) {
        return;
      }

      UUID entityId = mob.id();
      BudgetedMobReference reference = new BudgetedMobReference(mob, staleMobReferences);
      BudgetClaim claim = new BudgetClaim(generation, reference);
      if (budgetedMobs.putIfAbsent(entityId, claim) != null) {
        return;
      }

      try {
        float multiplier = (float) Math.max(0.05D, Math.min(1D, budgetMultiplier));
        if (!activity.setNavigationBudget(mob, multiplier)) {
          budgetedMobs.remove(entityId, claim);
          return;
        }
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

  private void restoreBudget(GameEntity mob, long generation) throws Throwable {
    UUID entityId = mob.id();
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

  private void resetBudgetState(GameEntity mob) {
    activity.resetNavigationBudget(mob);
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

  /**
   * Forgets claim releases already handed to entity schedulers. Batches from before the call
   * carry the old epoch, so a late completion cannot touch the reset counters.
   */
  private void abandonClaimCleanup() {
    synchronized (claimCleanupMonitor) {
      claimCleanupEpoch.incrementAndGet();
      claimReleaseSweeps.clear();
      claimCleanupDraining.set(false);
      claimCleanupOperations.set(0);
      claimCleanupFailures.set(0L);
      claimCleanupMonitor.notifyAll();
    }
  }

  /**
   * Restores every budget this thread may touch and forgets the rest. The thread that stops the
   * server owns all regions on Folia and is the main thread elsewhere. A mob left budgeted keeps
   * its marker, which resets the budget when the mob loads again.
   */
  private void releaseClaimsOnStoppingThread() {
    drainStaleClaims();
    boolean folia = J.isFoliaThreading();
    for (BudgetClaim claim : budgetedMobs.values()) {
      GameEntity mob = entity(claim.mobReference);
      if (mob != null && (folia ? J.isOwnedByCurrentRegion(mob.handle()) : J.isPrimaryThread())) {
        releaseClaim(claim, mob);
      }
      budgetedMobs.remove(claim.mobReference.entityId, claim);
    }
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
    GameEntity mob = entity(claim.mobReference);
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
    if ((folia && J.isOwnedByCurrentRegion(mob.handle())) || (!folia && J.isPrimaryThread())) {
      release.run();
      return;
    }

    if (!J.runEntity(mob.handle(), release, 0, retired)) {
      exactCompletion.accept(false);
    }
  }

  private void completeClaimRelease(BudgetClaim claim, GameEntity mob, Consumer<Boolean> completion) {
    boolean succeeded = false;
    try {
      releaseClaimOrThrow(claim, mob);
      succeeded = true;
    } catch (Throwable failure) {
      reportError(failure);
    } finally {
      completion.accept(succeeded);
    }
  }

  private void releaseClaimOrThrow(BudgetClaim claim, GameEntity mob) throws Throwable {
    if (budgetedMobs.get(claim.mobReference.entityId) != claim) {
      return;
    }
    resetBudgetState(mob);
    budgetedMobs.remove(claim.mobReference.entityId, claim);
  }

  private void releaseClaim(BudgetClaim claim, GameEntity mob) {
    try {
      releaseClaimOrThrow(claim, mob);
    } catch (Throwable failure) {
      reportError(failure);
    }
  }

  private void signalClaimCleanup() {
    synchronized (claimCleanupMonitor) {
      claimCleanupMonitor.notifyAll();
    }
  }

  private void reconcileLoadedMob(GameEntity mob, long generation) {
    if (mob == null || generation != lifecycleGeneration.get()) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(mob.handle())) {
      J.runEntity(mob.handle(), () -> reconcileLoadedMob(mob, generation));
      return;
    }

    if (!resolveBridges()) {
      return;
    }

    try {
      BudgetClaim claim = budgetedMobs.get(mob.id());
      if (active && isCurrentClaim(claim, mob, generation)) {
        return;
      }
      if (claim != null) {
        releaseClaim(claim, mob);
      } else if (hasMarker(mob) && !budgetedMobs.containsKey(mob.id())) {
        resetBudgetState(mob);
      }
    } catch (Throwable failure) {
      if (active) {
        failRuntime(failure);
      } else {
        reportError(failure);
      }
    }
  }

  private void releaseUnloadingMob(GameEntity mob) {
    if (mob == null) {
      return;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(mob.handle())) {
      UUID entityId = mob.id();
      BudgetClaim claim = budgetedMobs.get(entityId);
      Runnable retired = () -> {
        if (claim != null) {
          budgetedMobs.remove(entityId, claim);
        }
      };
      if (!J.runEntity(mob.handle(), () -> releaseUnloadingMob(mob), 0, retired)) {
        retired.run();
      }
      return;
    }

    if (!resolveBridges()) {
      return;
    }

    try {
      BudgetClaim claim = budgetedMobs.get(mob.id());
      if (claim != null) {
        releaseClaim(claim, mob);
      } else if (hasMarker(mob)) {
        resetBudgetState(mob);
      }
    } catch (Throwable failure) {
      reportError(failure);
    }
  }

  private boolean resolveBridges() {
    EntityActivityHost host = activity;
    if (host != null && host.supportsNavigationBudget()) {
      return true;
    }
    if (bridgesResolved) {
      return false;
    }
    return resolveBridgesOnce();
  }

  private synchronized boolean resolveBridgesOnce() {
    if (!bridgesResolved) {
      activity();
      bridgesResolved = true;
    }
    EntityActivityHost host = activity;
    return host != null && host.supportsNavigationBudget();
  }

  private boolean hasMarker(GameEntity mob) {
    return activity.hasNavigationBudget(mob);
  }

  private boolean isCurrentClaim(BudgetClaim claim, GameEntity mob, long generation) {
    return claim != null && claim.generation == generation && claim.mobReference.get() == mob.handle();
  }

  private void indexMob(GameEntity entity) {
    if (!entity.isMob()) {
      return;
    }

    long generation = lifecycleGeneration.get();
    if (!isCurrent(generation)) {
      return;
    }

    UUID entityId = entity.id();
    IndexedMob replacement = new IndexedMob(generation, new WeakReference<>(entity.handle()));
    IndexedMob existing = indexedMobs.putIfAbsent(entityId, replacement);
    if (existing == null) {
      if (indexedMobs.size() > MAX_INDEXED_MOBS) {
        indexedMobs.remove(entityId, replacement);
        return;
      }
      indexedMobOrder.offer(entityId);
      return;
    }

    if (existing.generation != generation || existing.reference.get() != entity.handle()) {
      indexedMobs.replace(entityId, existing, replacement);
    }
  }

  private void completeOwnerScan(AtomicInteger ownerScans) {
    ownerScans.decrementAndGet();
    synchronized (ownerScanMonitor) {
      ownerScanMonitor.notifyAll();
    }
  }

  /**
   * Drops the count of scans queued on entity schedulers. A stopping server never runs or retires
   * them, and a scan that does finish later settles against the counter it started with.
   */
  private void abandonOwnerScans() {
    synchronized (ownerScanMonitor) {
      ownerScanOperations = new AtomicInteger(0);
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
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.reportError("Pathfinder Budget disabled due to runtime incompatibility: "
          + failure.getClass().getSimpleName() + ": " + failure.getMessage(), failure);
    }
  }

  private double sampleTickMs() {
    return sample(SamplerTickTime.ID);
  }

  private GameEntity entity(WeakReference<Object> reference) {
    Object handle = reference.get();
    EntityHost host = handle == null ? null : entities();
    return host == null ? null : host.wrap(handle);
  }

  private PerWorldPressure worldPressure(GameEntity mob) {
    GameLocation location = mob.location();
    return location == null ? PerWorldPressure.NORMAL : PerWorldPressure.get(location.worldId());
  }

  private boolean nearPlayer(GameEntity mob, double distance) {
    WorldHost host = worlds();
    return host != null && host.anyPlayerNear(mob.location(), distance);
  }

  private EntityActivityHost activity() {
    EntityActivityHost host = activity;
    if (host == null) {
      ReactPlatform platform = ReactPlatforms.getOrNull();
      host = platform == null ? null : platform.entityActivityHost().orElse(null);
      activity = host;
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

  private static EntityHost entities() {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    return platform == null ? null : platform.entityHost().orElse(null);
  }

  private static void warn(String message) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.warn(message);
    }
  }

  private static void reportError(Throwable failure) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.reportError(failure);
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
    private final long epoch = claimCleanupEpoch.get();
    private volatile boolean sealed;

    private void submit(BudgetClaim claim) {
      synchronized (claimCleanupMonitor) {
        if (epoch != claimCleanupEpoch.get()) {
          return;
        }
        claimCleanupOperations.incrementAndGet();
      }
      pending.incrementAndGet();
      scheduleClaimRelease(claim, this::complete);
    }

    private void complete(boolean succeeded) {
      synchronized (claimCleanupMonitor) {
        if (epoch != claimCleanupEpoch.get()) {
          return;
        }
        if (!succeeded) {
          claimCleanupFailures.incrementAndGet();
        }
        claimCleanupOperations.decrementAndGet();
        claimCleanupMonitor.notifyAll();
      }
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
    private final WeakReference<Object> reference;

    private IndexedMob(long generation, WeakReference<Object> reference) {
      this.generation = generation;
      this.reference = reference;
    }
  }

  private static final class BudgetedMobReference extends WeakReference<Object> {
    private final UUID entityId;

    private BudgetedMobReference(GameEntity mob, ReferenceQueue<Object> referenceQueue) {
      super(mob.handle(), referenceQueue);
      entityId = mob.id();
    }
  }
}
