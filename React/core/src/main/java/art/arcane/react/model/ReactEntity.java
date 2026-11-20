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

package art.arcane.react.model;

import art.arcane.react.api.entity.EntityPriority;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.util.common.scheduling.J;

import java.lang.ref.WeakReference;
import java.util.Iterator;
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

public class ReactEntity {
  private static final long maxTickInterval = 10000;
  private static final long SCRATCH_RETENTION_MS = 900_000L;
  public static final String STACK_COUNT_KEY = "react-stack-count";
  public static final String PAUSED_KEY = "react-paused";
  public static final String DOZING_KEY = "react-dozing";
  private static final int MANAGED_CLEANUP_BATCH_SIZE = 256;
  private static final Map<UUID, Scratch> scratchByEntity = new ConcurrentHashMap<>();
  private static final Map<UUID, PauseState> pauseByEntity = new ConcurrentHashMap<>();
  private static final Map<UUID, DozeState> dozeByEntity = new ConcurrentHashMap<>();
  private static final Queue<ManagedCleanupSweep> managedCleanupSweeps = new ConcurrentLinkedQueue<>();
  private static final AtomicBoolean managedCleanupDraining = new AtomicBoolean(false);
  private static final AtomicInteger managedCleanupOperations = new AtomicInteger(0);
  private static final AtomicLong managedCleanupFailures = new AtomicLong(0L);
  private static final AtomicLong managedCleanupEpoch = new AtomicLong(0L);
  private static final Object managedCleanupMonitor = new Object();
  private static final AtomicLong managedClaimSequence = new AtomicLong();

  public static long getStaleness(GameEntity entity) {
    return System.currentTimeMillis() - getLastTick(entity);
  }

  public static boolean tick(GameEntity entity, EntityPriority p) {
    if (entity == null) {
      return false;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity.handle())) {
      J.runEntity(entity.handle(), () -> tick(entity, p));
      return false;
    }

    if (!entity.isValid()) {
      return false;
    }

    if (getStaleness(entity) > maxTickInterval) {
      long now = System.currentTimeMillis();
      double rawPriority = p.getPriority(entity);
      setRawPriority(entity, rawPriority, now);
      p.updateCrowd(entity, rawPriority, now);
      p.updateDistanceToPlayer(entity);
      setPriority(entity, p.getPriorityWithCrowd(entity, rawPriority, getCrowding(entity)));
      setLastTick(entity, now);

      return true;
    }

    return false;
  }

  public static long getLastTick(GameEntity entity) {
    Scratch scratch = scratchByEntity.get(entity.id());
    return scratch == null ? 0 : scratch.lastTick;
  }

  public static void setLastTick(GameEntity entity, long lastTick) {
    Scratch scratch = scratch(entity);
    scratch.lastTick = lastTick;
    scratch.touchedMs = System.currentTimeMillis();
  }

  public static double getPriority(GameEntity entity) {
    Scratch scratch = scratchByEntity.get(entity.id());
    return scratch == null ? EntityPriority.BASELINE : scratch.priority;
  }

  public static void setPriority(GameEntity entity, double priority) {
    Scratch scratch = scratch(entity);
    scratch.priority = priority;
    scratch.touchedMs = System.currentTimeMillis();
  }

  public static double getRawPriority(GameEntity entity, long nowMs) {
    Scratch scratch = scratchByEntity.get(entity.id());
    if (scratch == null) {
      return Double.NaN;
    }

    long age = nowMs - scratch.rawPriorityMs;
    if (age < 0 || age > maxTickInterval) {
      return Double.NaN;
    }

    return scratch.rawPriority;
  }

  public static void setRawPriority(GameEntity entity, double rawPriority, long nowMs) {
    Scratch scratch = scratch(entity);
    scratch.rawPriority = rawPriority;
    scratch.rawPriorityMs = nowMs;
    scratch.touchedMs = System.currentTimeMillis();
  }

  public static int getStackCount(GameEntity entity) {
    return host().persistentInt(entity, STACK_COUNT_KEY, 1);
  }

  public static void setStackCount(GameEntity entity, int stackCount) {
    host().setPersistentInt(entity, STACK_COUNT_KEY, stackCount);
  }

  public static double getCrowding(GameEntity entity) {
    Scratch scratch = scratchByEntity.get(entity.id());
    return scratch == null ? 1 : scratch.crowding;
  }

  public static void setCrowding(GameEntity entity, double crowding) {
    Scratch scratch = scratch(entity);
    scratch.crowding = crowding;
    scratch.touchedMs = System.currentTimeMillis();
  }

  public static double getNearestPlayer(GameEntity entity) {
    Scratch scratch = scratchByEntity.get(entity.id());
    return scratch == null ? 1 : scratch.nearestPlayer;
  }

  public static void setNearestPlayer(GameEntity entity, double d) {
    Scratch scratch = scratch(entity);
    scratch.nearestPlayer = d;
    scratch.touchedMs = System.currentTimeMillis();
  }

  public static boolean isPaused(GameEntity entity) {
    return host().persistentFlag(entity, PAUSED_KEY);
  }

  public static boolean isPausedBy(GameEntity entity, PauseOwner owner) {
    if (entity == null || owner == null) {
      return false;
    }

    PauseState state = pauseByEntity.get(entity.id());
    return state != null && (state.owners & owner.mask) != 0;
  }

  public static void requestPause(GameEntity entity, PauseOwner owner) {
    if (entity == null || !entity.isLiving() || owner == null) {
      return;
    }

    GameEntity living = entity;

    UUID entityId = entity.id();
    long claim = managedClaimSequence.incrementAndGet();
    pauseByEntity.compute(entityId, (ignored, current) -> {
      int owners = addPauseOwner(current == null ? 0 : current.owners, owner);
      boolean aiOwned = current != null && current.aiOwned;
      if (!aiOwned) {
        aiOwned = claimAi(living);
      }
      long adaptiveClaim = current == null ? 0L : current.adaptiveClaim;
      long dynamicClaim = current == null ? 0L : current.dynamicClaim;
      if (owner == PauseOwner.ADAPTIVE_ENTITY_SLEEP) {
        adaptiveClaim = claim;
      } else {
        dynamicClaim = claim;
      }
      return new PauseState(new WeakReference<>(living.handle()), owners, aiOwned, adaptiveClaim, dynamicClaim);
    });
  }

  public static void releasePause(GameEntity entity, PauseOwner owner) {
    if (entity == null || !entity.isLiving() || owner == null) {
      return;
    }

    GameEntity living = entity;

    UUID entityId = entity.id();
    pauseByEntity.compute(entityId, (ignored, current) -> {
      if (current == null) {
        releaseOrphanAi(living);
        return null;
      }

      int owners = removePauseOwner(current.owners, owner);
      if (owners != 0) {
        return withoutPauseOwner(living, current, owner, owners);
      }

      if (current.aiOwned || isPaused(living)) {
        releaseAi(living);
      }
      return null;
    });
  }

  public static void releasePauseOwner(PauseOwner owner) {
    if (owner == null || pauseByEntity.isEmpty()) {
      return;
    }

    enqueueManagedCleanup(ManagedCleanupSweep.forPauseOwner(owner, managedClaimSequence.get()));
  }

  public static boolean isDozing(GameEntity mob) {
    if (mob == null) {
      return false;
    }

    return host().persistentFlag(mob, DOZING_KEY);
  }

  public static void requestDoze(GameEntity mob) {
    if (mob == null || !mob.isMob()) {
      return;
    }

    UUID entityId = mob.id();
    long claim = managedClaimSequence.incrementAndGet();
    dozeByEntity.compute(entityId, (ignored, current) -> {
      boolean awarenessOwned = current != null && current.awarenessOwned;
      if (!awarenessOwned && host().isAware(mob)) {
        host().setAware(mob, false);
        host().setPersistentFlag(mob, DOZING_KEY, true);
        awarenessOwned = true;
      }
      return new DozeState(new WeakReference<>(mob.handle()), awarenessOwned, claim);
    });
  }

  public static void releaseDoze(GameEntity mob) {
    if (mob == null || !mob.isMob()) {
      return;
    }

    DozeState state = dozeByEntity.remove(mob.id());
    if ((state != null && state.awarenessOwned) || isDozing(mob)) {
      host().setAware(mob, true);
      host().setPersistentFlag(mob, DOZING_KEY, false);
    }
  }

  public static void releaseAllDozes() {
    if (dozeByEntity.isEmpty()) {
      return;
    }

    enqueueManagedCleanup(ManagedCleanupSweep.forDozes(managedClaimSequence.get()));
  }

  public static void releaseAllManagedState() {
    if (J.isServerStopping()) {
      abandonManagedCleanup();
    }
    if (pauseByEntity.isEmpty() && dozeByEntity.isEmpty()) {
      return;
    }

    enqueueManagedCleanup(ManagedCleanupSweep.forAll(managedClaimSequence.get()));
  }

  public static void resumeManagedCleanup() {
    if (managedCleanupSweeps.isEmpty() && (!pauseByEntity.isEmpty() || !dozeByEntity.isEmpty())) {
      enqueueManagedCleanup(ManagedCleanupSweep.forAll(managedClaimSequence.get()));
      return;
    }
    startManagedCleanupDrain();
  }

  public static boolean awaitManagedCleanup(long timeoutMillis) {
    startManagedCleanupDrain();
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
    synchronized (managedCleanupMonitor) {
      while (!managedCleanupSweeps.isEmpty()
          || managedCleanupDraining.get()
          || managedCleanupOperations.get() > 0) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) {
          return false;
        }

        try {
          TimeUnit.NANOSECONDS.timedWait(managedCleanupMonitor, remaining);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
    }
    return managedCleanupFailures.get() == 0L;
  }

  public static void reconcileManagedState(GameEntity entity) {
    if (entity == null || !entity.isLiving()) {
      return;
    }

    GameEntity living = entity;

    UUID entityId = entity.id();
    PauseState pauseState = pauseByEntity.computeIfPresent(
        entityId,
        (ignored, current) -> new PauseState(
            new WeakReference<>(living.handle()),
            current.owners,
            current.aiOwned,
            current.adaptiveClaim,
            current.dynamicClaim
        )
    );
    if (pauseState == null) {
      releaseOrphanAi(living);
    }
    if (entity.isMob()) {
      GameEntity mob = entity;
      DozeState dozeState = dozeByEntity.computeIfPresent(
          entityId,
          (ignored, current) -> new DozeState(new WeakReference<>(mob.handle()), current.awarenessOwned, current.claim)
      );
      if (dozeState == null && isDozing(mob)) {
        host().setAware(mob, true);
        host().setPersistentFlag(mob, DOZING_KEY, false);
      }
    }
  }

  public static void releaseManagedState(GameEntity entity) {
    if (entity == null || !entity.isLiving()) {
      return;
    }

    GameEntity living = entity;

    UUID entityId = entity.id();
    pauseByEntity.compute(entityId, (ignored, current) -> {
      if ((current != null && current.aiOwned) || isPaused(living)) {
        releaseAi(living);
      }
      return null;
    });
    if (entity.isMob()) {
      releaseDoze(entity);
    }
    scratchByEntity.remove(entityId);
  }

  public static void sweepScratch() {
    if (!scratchByEntity.isEmpty()) {
      long cutoff = System.currentTimeMillis() - SCRATCH_RETENTION_MS;
      Iterator<Map.Entry<UUID, Scratch>> iterator = scratchByEntity.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<UUID, Scratch> entry = iterator.next();
        if (entry.getValue().touchedMs < cutoff) {
          iterator.remove();
        }
      }
    }

    pruneClearedManagedState();
  }

  public static void clearScratch() {
    scratchByEntity.clear();
  }

  static int addPauseOwner(int owners, PauseOwner owner) {
    return owners | owner.mask;
  }

  static int removePauseOwner(int owners, PauseOwner owner) {
    return owners & ~owner.mask;
  }

  private static EntityHost host() {
    return ReactPlatforms.get().entityHost().orElseThrow(() -> new IllegalStateException("No entity host is bound"));
  }

  private static GameEntity resolve(WeakReference<Object> reference) {
    Object handle = reference.get();
    return handle == null ? null : host().wrap(handle);
  }

  private static void reportError(Throwable failure) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    if (platform != null) {
      platform.reportError(failure);
    }
  }

  private static Scratch scratch(GameEntity entity) {
    return scratchByEntity.computeIfAbsent(entity.id(), ignored -> new Scratch());
  }

  private static boolean claimAi(GameEntity entity) {
    if (isPaused(entity)) {
      return true;
    }
    if (!host().hasAi(entity)) {
      return false;
    }

    host().setAi(entity, false);
    host().setPersistentFlag(entity, PAUSED_KEY, true);
    return true;
  }

  private static void releaseAi(GameEntity entity) {
    host().setAi(entity, true);
    host().setPersistentFlag(entity, PAUSED_KEY, false);
  }

  private static void releaseOrphanAi(GameEntity entity) {
    if (isPaused(entity)) {
      releaseAi(entity);
    }
  }

  private static PauseState withoutPauseOwner(
      GameEntity entity,
      PauseState current,
      PauseOwner owner,
      int owners
  ) {
    long adaptiveClaim = owner == PauseOwner.ADAPTIVE_ENTITY_SLEEP ? 0L : current.adaptiveClaim;
    long dynamicClaim = owner == PauseOwner.DYNAMIC_ACTIVATION_RANGE ? 0L : current.dynamicClaim;
    return new PauseState(new WeakReference<>(entity.handle()), owners, current.aiOwned, adaptiveClaim, dynamicClaim);
  }

  private static long pauseClaim(PauseState state, PauseOwner owner) {
    return owner == PauseOwner.ADAPTIVE_ENTITY_SLEEP ? state.adaptiveClaim : state.dynamicClaim;
  }

  private static void releaseCapturedPauseOwner(GameEntity entity, PauseOwner owner, long claim) {
    pauseByEntity.compute(entity.id(), (ignored, current) -> {
      if (current == null || pauseClaim(current, owner) != claim) {
        return current;
      }

      int owners = removePauseOwner(current.owners, owner);
      if (owners != 0) {
        return withoutPauseOwner(entity, current, owner, owners);
      }

      if (current.aiOwned || isPaused(entity)) {
        releaseAi(entity);
      }
      return null;
    });
  }

  private static void discardCapturedPauseOwner(UUID entityId, PauseOwner owner, long claim) {
    pauseByEntity.computeIfPresent(entityId, (ignored, current) -> {
      if (pauseClaim(current, owner) != claim) {
        return current;
      }

      int owners = removePauseOwner(current.owners, owner);
      return owners == 0 ? null : withoutPauseOwnerReference(current, owner, owners);
    });
  }

  private static PauseState withoutPauseOwnerReference(PauseState current, PauseOwner owner, int owners) {
    long adaptiveClaim = owner == PauseOwner.ADAPTIVE_ENTITY_SLEEP ? 0L : current.adaptiveClaim;
    long dynamicClaim = owner == PauseOwner.DYNAMIC_ACTIVATION_RANGE ? 0L : current.dynamicClaim;
    return new PauseState(current.entity, owners, current.aiOwned, adaptiveClaim, dynamicClaim);
  }

  private static void clearCapturedPause(GameEntity entity, PauseState captured) {
    pauseByEntity.compute(entity.id(), (ignored, current) -> {
      if (current == null) {
        return null;
      }

      PauseState remaining = removeCapturedPauseClaims(current, captured);
      if (remaining == current) {
        return current;
      }
      if (remaining != null) {
        return new PauseState(
            new WeakReference<>(entity.handle()),
            remaining.owners,
            remaining.aiOwned,
            remaining.adaptiveClaim,
            remaining.dynamicClaim
        );
      }

      if (current.aiOwned || isPaused(entity)) {
        releaseAi(entity);
      }
      return null;
    });
  }

  private static void discardCapturedPause(UUID entityId, PauseState captured) {
    pauseByEntity.computeIfPresent(
        entityId,
        (ignored, current) -> removeCapturedPauseClaims(current, captured)
    );
  }

  private static PauseState removeCapturedPauseClaims(PauseState current, PauseState captured) {
    int owners = current.owners;
    long adaptiveClaim = current.adaptiveClaim;
    long dynamicClaim = current.dynamicClaim;
    if ((captured.owners & PauseOwner.ADAPTIVE_ENTITY_SLEEP.mask) != 0
        && adaptiveClaim == captured.adaptiveClaim) {
      owners = removePauseOwner(owners, PauseOwner.ADAPTIVE_ENTITY_SLEEP);
      adaptiveClaim = 0L;
    }
    if ((captured.owners & PauseOwner.DYNAMIC_ACTIVATION_RANGE.mask) != 0
        && dynamicClaim == captured.dynamicClaim) {
      owners = removePauseOwner(owners, PauseOwner.DYNAMIC_ACTIVATION_RANGE);
      dynamicClaim = 0L;
    }

    if (owners == current.owners) {
      return current;
    }
    if (owners == 0) {
      return null;
    }
    return new PauseState(current.entity, owners, current.aiOwned, adaptiveClaim, dynamicClaim);
  }

  private static PauseState capturePauseClaims(PauseState state, long claimCutoff) {
    int owners = 0;
    long adaptiveClaim = 0L;
    long dynamicClaim = 0L;
    if (state.adaptiveClaim > 0L && state.adaptiveClaim <= claimCutoff) {
      owners = addPauseOwner(owners, PauseOwner.ADAPTIVE_ENTITY_SLEEP);
      adaptiveClaim = state.adaptiveClaim;
    }
    if (state.dynamicClaim > 0L && state.dynamicClaim <= claimCutoff) {
      owners = addPauseOwner(owners, PauseOwner.DYNAMIC_ACTIVATION_RANGE);
      dynamicClaim = state.dynamicClaim;
    }
    if (owners == 0) {
      return null;
    }
    return new PauseState(state.entity, owners, state.aiOwned, adaptiveClaim, dynamicClaim);
  }

  private static void releaseCapturedDoze(GameEntity mob, long claim) {
    dozeByEntity.compute(mob.id(), (ignored, current) -> {
      if (current == null || current.claim != claim) {
        return current;
      }

      if (current.awarenessOwned || isDozing(mob)) {
        host().setAware(mob, true);
        host().setPersistentFlag(mob, DOZING_KEY, false);
      }
      return null;
    });
  }

  private static void discardCapturedDoze(UUID entityId, long claim) {
    dozeByEntity.computeIfPresent(
        entityId,
        (ignored, current) -> current.claim == claim ? null : current
    );
  }

  private static void pruneClearedManagedState() {
    for (Map.Entry<UUID, PauseState> entry : pauseByEntity.entrySet()) {
      PauseState state = entry.getValue();
      if (state.entity().get() == null) {
        pauseByEntity.remove(entry.getKey(), state);
      }
    }
    for (Map.Entry<UUID, DozeState> entry : dozeByEntity.entrySet()) {
      DozeState state = entry.getValue();
      if (state.entity().get() == null) {
        dozeByEntity.remove(entry.getKey(), state);
      }
    }
  }

  private static void enqueueManagedCleanup(ManagedCleanupSweep sweep) {
    synchronized (managedCleanupMonitor) {
      if (managedCleanupSweeps.isEmpty()
          && !managedCleanupDraining.get()
          && managedCleanupOperations.get() == 0) {
        managedCleanupFailures.set(0L);
      }
      managedCleanupSweeps.add(sweep);
    }
    startManagedCleanupDrain();
  }

  /**
   * Forgets cleanup already handed to entity schedulers. A stopping server never runs or retires
   * it, and a stuck batch would hold back the final release. Batches from before the call carry
   * the old epoch, so a late completion cannot touch the reset counters.
   */
  private static void abandonManagedCleanup() {
    synchronized (managedCleanupMonitor) {
      managedCleanupEpoch.incrementAndGet();
      managedCleanupSweeps.clear();
      managedCleanupDraining.set(false);
      managedCleanupOperations.set(0);
      managedCleanupFailures.set(0L);
      managedCleanupMonitor.notifyAll();
    }
  }

  private static void startManagedCleanupDrain() {
    if (managedCleanupSweeps.isEmpty() || !managedCleanupDraining.compareAndSet(false, true)) {
      return;
    }

    drainManagedCleanup();
  }

  private static void drainManagedCleanup() {
    while (true) {
      int remaining = MANAGED_CLEANUP_BATCH_SIZE;
      ManagedCleanupBatch batch = new ManagedCleanupBatch();
      while (remaining > 0) {
        ManagedCleanupSweep sweep = managedCleanupSweeps.peek();
        if (sweep == null) {
          break;
        }

        remaining -= sweep.drain(remaining, batch);
        if (sweep.isComplete()) {
          managedCleanupSweeps.poll();
        }
      }

      if (!batch.seal()) {
        return;
      }
      if (managedCleanupSweeps.isEmpty()) {
        managedCleanupDraining.set(false);
        signalManagedCleanup();
        if (!managedCleanupSweeps.isEmpty()) {
          startManagedCleanupDrain();
        }
        return;
      }
    }
  }

  private static void runOwned(
      GameEntity entity,
      Runnable operation,
      Runnable retired,
      Consumer<Boolean> completion
  ) {
    AtomicBoolean terminalClaimed = new AtomicBoolean(false);
    Runnable guardedOperation = () -> {
      if (terminalClaimed.compareAndSet(false, true)) {
        completeManagedOperation(operation, completion);
      }
    };
    Runnable guardedRetirement = () -> {
      if (terminalClaimed.compareAndSet(false, true)) {
        completeManagedOperation(retired, completion);
      }
    };
    Runnable guardedRejection = () -> {
      if (terminalClaimed.compareAndSet(false, true)) {
        completeManagedOperation(retired, ignored -> completion.accept(false));
      }
    };
    boolean folia = J.isFoliaThreading();
    if ((folia && J.isOwnedByCurrentRegion(entity.handle())) || (!folia && J.isPrimaryThread())) {
      guardedOperation.run();
      return;
    }

    if (!J.runEntity(entity.handle(), guardedOperation, 0, guardedRetirement)) {
      guardedRejection.run();
    }
  }

  private static void completeManagedOperation(Runnable operation, Consumer<Boolean> completion) {
    boolean succeeded = false;
    try {
      operation.run();
      succeeded = true;
    } catch (Throwable failure) {
      reportError(failure);
    } finally {
      completion.accept(succeeded);
    }
  }

  private static void signalManagedCleanup() {
    synchronized (managedCleanupMonitor) {
      managedCleanupMonitor.notifyAll();
    }
  }

  public enum PauseOwner {
    ADAPTIVE_ENTITY_SLEEP(1),
    DYNAMIC_ACTIVATION_RANGE(1 << 1);

    private final int mask;

    PauseOwner(int mask) {
      this.mask = mask;
    }
  }

  private record PauseState(
      WeakReference<Object> entity,
      int owners,
      boolean aiOwned,
      long adaptiveClaim,
      long dynamicClaim
  ) {
  }

  private record DozeState(WeakReference<Object> entity, boolean awarenessOwned, long claim) {
  }

  private static final class ManagedCleanupSweep {
    private final Iterator<Map.Entry<UUID, PauseState>> pauses;
    private final Iterator<Map.Entry<UUID, DozeState>> dozes;
    private final PauseOwner pauseOwner;
    private final long claimCutoff;
    private boolean pausesComplete;
    private boolean dozesComplete;

    private ManagedCleanupSweep(
        Iterator<Map.Entry<UUID, PauseState>> pauses,
        Iterator<Map.Entry<UUID, DozeState>> dozes,
        PauseOwner pauseOwner,
        long claimCutoff
    ) {
      this.pauses = pauses;
      this.dozes = dozes;
      this.pauseOwner = pauseOwner;
      this.claimCutoff = claimCutoff;
      pausesComplete = pauses == null;
      dozesComplete = dozes == null;
    }

    private static ManagedCleanupSweep forPauseOwner(PauseOwner owner, long claimCutoff) {
      return new ManagedCleanupSweep(pauseByEntity.entrySet().iterator(), null, owner, claimCutoff);
    }

    private static ManagedCleanupSweep forDozes(long claimCutoff) {
      return new ManagedCleanupSweep(null, dozeByEntity.entrySet().iterator(), null, claimCutoff);
    }

    private static ManagedCleanupSweep forAll(long claimCutoff) {
      return new ManagedCleanupSweep(
          pauseByEntity.entrySet().iterator(),
          dozeByEntity.entrySet().iterator(),
          null,
          claimCutoff
      );
    }

    private int drain(int limit, ManagedCleanupBatch batch) {
      int examined = drainPauses(limit, batch);
      if (examined < limit) {
        examined += drainDozes(limit - examined, batch);
      }
      return examined;
    }

    private int drainPauses(int limit, ManagedCleanupBatch batch) {
      if (pausesComplete || limit <= 0) {
        return 0;
      }

      int examined = 0;
      while (examined < limit && pauses.hasNext()) {
        processPause(pauses.next(), batch);
        examined++;
      }
      if (!pauses.hasNext()) {
        pausesComplete = true;
      }
      return examined;
    }

    private void processPause(Map.Entry<UUID, PauseState> entry, ManagedCleanupBatch batch) {
      PauseState state = entry.getValue();
      GameEntity entity = resolve(state.entity());
      if (entity == null) {
        pauseByEntity.remove(entry.getKey(), state);
        return;
      }

      if (pauseOwner != null) {
        long claim = pauseClaim(state, pauseOwner);
        if (claim <= 0L || claim > claimCutoff) {
          return;
        }
        batch.submit(
            entity,
            () -> releaseCapturedPauseOwner(entity, pauseOwner, claim),
            () -> discardCapturedPauseOwner(entry.getKey(), pauseOwner, claim)
        );
        return;
      }

      PauseState captured = capturePauseClaims(state, claimCutoff);
      if (captured != null) {
        batch.submit(
            entity,
            () -> clearCapturedPause(entity, captured),
            () -> discardCapturedPause(entry.getKey(), captured)
        );
      }
    }

    private int drainDozes(int limit, ManagedCleanupBatch batch) {
      if (dozesComplete || limit <= 0) {
        return 0;
      }

      int examined = 0;
      while (examined < limit && dozes.hasNext()) {
        processDoze(dozes.next(), batch);
        examined++;
      }
      if (!dozes.hasNext()) {
        dozesComplete = true;
      }
      return examined;
    }

    private void processDoze(Map.Entry<UUID, DozeState> entry, ManagedCleanupBatch batch) {
      DozeState state = entry.getValue();
      if (state.claim <= 0L || state.claim > claimCutoff) {
        return;
      }

      GameEntity mob = resolve(state.entity());
      if (mob == null) {
        dozeByEntity.remove(entry.getKey(), state);
        return;
      }

      batch.submit(
          mob,
          () -> releaseCapturedDoze(mob, state.claim),
          () -> discardCapturedDoze(entry.getKey(), state.claim)
      );
    }

    private boolean isComplete() {
      return pausesComplete && dozesComplete;
    }
  }

  private static final class ManagedCleanupBatch {
    private final AtomicInteger pending = new AtomicInteger(1);
    private final long epoch = managedCleanupEpoch.get();
    private volatile boolean sealed;

    private void submit(GameEntity entity, Runnable operation, Runnable retired) {
      synchronized (managedCleanupMonitor) {
        if (epoch != managedCleanupEpoch.get()) {
          return;
        }
        managedCleanupOperations.incrementAndGet();
      }
      pending.incrementAndGet();
      runOwned(entity, operation, retired, this::complete);
    }

    private void complete(boolean succeeded) {
      synchronized (managedCleanupMonitor) {
        if (epoch != managedCleanupEpoch.get()) {
          return;
        }
        if (!succeeded) {
          managedCleanupFailures.incrementAndGet();
        }
        managedCleanupOperations.decrementAndGet();
        managedCleanupMonitor.notifyAll();
      }
      if (pending.decrementAndGet() == 0 && sealed) {
        drainManagedCleanup();
      }
    }

    private boolean seal() {
      sealed = true;
      return pending.decrementAndGet() == 0;
    }
  }

  private static final class Scratch {
    private volatile long lastTick;
    private volatile double priority = EntityPriority.BASELINE;
    private volatile double rawPriority = Double.NaN;
    private volatile long rawPriorityMs;
    private volatile double crowding = 1;
    private volatile double nearestPlayer = 1;
    private volatile long touchedMs = System.currentTimeMillis();
  }
}
