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
import art.arcane.react.api.feature.PressureGate;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Afk View Shedding feature. Idle players receive a reduced send view distance, cutting chunk packets and entity tracker fanout, and are restored instantly on activity.")
public class FeatureAfkViewShedding extends ReactFeature implements Listener {
  private static final long RECONCILE_TIMEOUT_MS = 30_000L;
  static long idleThresholdMs(int idleAfterSeconds) {
    return Math.max(10_000L, idleAfterSeconds * 1000L);
  }

  static int pressureCap(int pressureSendViewDistanceCap) {
    return Math.max(2, pressureSendViewDistanceCap);
  }

  public static final String ID = "afk-view-shedding";
  @art.arcane.react.util.project.config.ConfigDoc(value = "Main evaluation interval for afk view shedding in milliseconds.", impact = "Lower values detect idle players sooner; higher values reduce overhead.")
  private int tickIntervalMS = 5000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Seconds without movement or interaction before a player is considered idle.", impact = "Lower values shed view distance sooner; higher values are more conservative about declaring players idle.")
  private int idleAfterSeconds = 180;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Send view distance applied to idle players (chunks).", impact = "Lower values cut more chunk packets and entity tracking for idle players; higher values keep more of the world visible to them.")
  private int idleSendViewDistance = 4;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds required before idle shedding engages; 0 sheds idle players regardless of load.", impact = "Raise this to only shed idle players while the server is actually under pressure.")
  private double minTickTimeMs = 0;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Caps every player's send view distance during sustained server pressure (simulation distance is untouched).", impact = "Enable to cut chunk packets and tracker fanout for all players during incidents; disable to only shed idle players.")
  private boolean pressureNotch = true;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Send view distance cap applied to all players while pressure is engaged (chunks).", impact = "Lower values shed more packet and tracker load during pressure windows at the cost of shorter visible range.")
  private int pressureSendViewDistanceCap = 8;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds required before the pressure notch engages.", impact = "Lower values cap view distance earlier; higher values reserve it for heavier load.")
  private double pressureEngageTickTimeMs = 70;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds the server must stay below before the pressure notch releases.", impact = "Lower values hold the cap longer for stability; higher values restore range sooner.")
  private double pressureReleaseTickTimeMs = 45;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Sustained pressure duration required before the notch engages (milliseconds).", impact = "Higher values ignore short spikes; lower values engage faster.")
  private long pressureSustainEngageMs = 12_000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Sustained recovery duration required before the notch releases (milliseconds).", impact = "Higher values avoid flapping between states; lower values restore range sooner.")
  private long pressureSustainReleaseMs = 30_000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Grace period after activation before the pressure notch can engage (seconds).", impact = "Prevents startup tick spikes from capping everyone's view distance before the server settles; raise it if your server takes longer to warm up.")
  private int pressureWarmupSeconds = 45;
  private transient long activatedAtMs;
  private transient volatile Map<UUID, Long> lastActivityMs;
  private transient final PressureGate pressureGate = new PressureGate();
  private transient final Map<UUID, ViewDistanceState> distanceStates = new ConcurrentHashMap<>();
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient final AtomicInteger pendingReconciliations = new AtomicInteger(0);
  private transient final AtomicLong reconciliationFailures = new AtomicLong(0L);
  private transient final Object reconciliationMonitor = new Object();
  private transient volatile Method getSendViewDistanceMethod;
  private transient volatile Method setSendViewDistanceMethod;
  private transient volatile long activeGeneration;
  private transient volatile boolean supportsSendViewDistance;
  private transient volatile boolean warnedRuntimeFailure;

  public FeatureAfkViewShedding() {
    super(ID);
  }

  @Override
  public void onActivate() {
    lastActivityMs = new ConcurrentHashMap<>();
    pressureGate.reset();
    activatedAtMs = System.currentTimeMillis();
    warnedRuntimeFailure = false;
    supportsSendViewDistance = false;
    activeGeneration = lifecycleGeneration.incrementAndGet();
    try {
      getSendViewDistanceMethod = Player.class.getMethod("getSendViewDistance");
      setSendViewDistanceMethod = Player.class.getMethod("setSendViewDistance", int.class);
      supportsSendViewDistance = true;
    } catch (NoSuchMethodException e) {
      setEnabled(false);
      React.warn("Afk View Shedding disabled: per-player send view distance is not available on this server software. Use Paper/Purpur to enable this feature.");
    }
  }

  @Override
  public void onDeactivate() {
    long generation = activeGeneration;
    activeGeneration = 0L;
    lifecycleGeneration.incrementAndGet();

    pressureGate.reset();

    Map<UUID, Long> activity = lastActivityMs;
    lastActivityMs = null;
    if (activity != null) {
      activity.clear();
    }

    long failureBaseline = reconciliationFailures.get();
    releaseGeneration(generation, indexPlayers(playerSnapshot()));
    if (!awaitReconciliations(RECONCILE_TIMEOUT_MS)
        || reconciliationFailures.get() != failureBaseline) {
      throw new IllegalStateException(
          "AFK view-distance cleanup did not drain within " + RECONCILE_TIMEOUT_MS + "ms"
      );
    }
  }

  @Override
  public int getTickInterval() {
    return Math.max(1000, tickIntervalMS);
  }

  @Override
  public void onTick() {
    long generation = activeGeneration;
    Map<UUID, Long> activity = lastActivityMs;
    if (!supportsSendViewDistance || generation == 0L || activity == null) {
      return;
    }

    Player[] players = playerSnapshot();
    double tickMs = sample(SamplerTickTime.ID);
    tickPressureNotch(tickMs, players, generation);

    if (generation != activeGeneration || minTickTimeMs > 0 && tickMs < minTickTimeMs) {
      return;
    }

    long now = System.currentTimeMillis();
    long idleAfterMs = idleThresholdMs(idleAfterSeconds);
    for (Player player : players) {
      if (generation != activeGeneration) {
        return;
      }
      if (player == null) {
        continue;
      }

      UUID playerId = player.getUniqueId();
      Long last = activity.get(playerId);
      if (last == null) {
        activity.put(playerId, now);
        continue;
      }

      if (now - last >= idleAfterMs) {
        requestClaim(player, ClaimKind.IDLE, generation, Math.max(2, idleSendViewDistance), last.longValue());
      }
    }
  }

  private void tickPressureNotch(double tickMs, Player[] players, long generation) {
    if (!pressureNotch || generation != activeGeneration) {
      return;
    }

    long now = System.currentTimeMillis();
    boolean warmedUp = now - activatedAtMs >= Math.max(0, pressureWarmupSeconds) * 1000L;
    boolean pressure = warmedUp && !(tickMs < pressureEngageTickTimeMs);
    boolean calm = !(tickMs > pressureReleaseTickTimeMs);
    boolean wasEngaged = pressureGate.isEngaged();

    if (wasEngaged) {
      applyPressureCaps(players, generation);
    }

    boolean nowEngaged = pressureGate.update(now, pressure, calm, pressureSustainEngageMs, pressureSustainReleaseMs);
    if (!wasEngaged && nowEngaged) {
      applyPressureCaps(players, generation);
    } else if (wasEngaged && !nowEngaged) {
      releasePressure(players, generation);
    }
  }

  private void applyPressureCaps(Player[] players, long generation) {
    int cap = pressureCap(pressureSendViewDistanceCap);
    for (Player player : players) {
      if (generation != activeGeneration) {
        return;
      }

      requestClaim(player, ClaimKind.PRESSURE, generation, cap, 0L);
    }
  }

  private void releasePressure(Player[] players, long generation) {
    pressureGate.reset();
    releaseClaims(ClaimKind.PRESSURE, generation, indexPlayers(players));
  }

  private void requestClaim(Player player, ClaimKind kind, long generation, int target, long expectedActivityMs) {
    if (player == null || generation != activeGeneration) {
      return;
    }

    ViewDistanceState state = distanceStates.get(player.getUniqueId());
    if (state != null && state.hasClaim(kind, generation, target)) {
      return;
    }

    J.runEntity(player, () -> applyClaim(player, kind, generation, target, expectedActivityMs));
  }

  private void applyClaim(Player player, ClaimKind kind, long generation, int target, long expectedActivityMs) {
    if (generation != activeGeneration
        || !player.isOnline()
        || !isClaimRequestCurrent(player.getUniqueId(), kind, expectedActivityMs)) {
      return;
    }

    UUID playerId = player.getUniqueId();
    try {
      while (generation == activeGeneration) {
        ViewDistanceState state = distanceStates.get(playerId);
        if (state == null) {
          int current = (int) getSendViewDistanceMethod.invoke(player);
          if (generation != activeGeneration || current <= target) {
            return;
          }

          ViewDistanceState candidate = new ViewDistanceState(current, player);
          ViewDistanceState existing = distanceStates.putIfAbsent(playerId, candidate);
          state = existing == null ? candidate : existing;
        }

        synchronized (state) {
          if (generation != activeGeneration || distanceStates.get(playerId) != state) {
            continue;
          }
          if (!state.claim(kind, generation, target)) {
            return;
          }

          state.remember(player);
          int current = (int) getSendViewDistanceMethod.invoke(player);
          reconcileLocked(playerId, player, state, current);
          return;
        }
      }
    } catch (Throwable e) {
      failRuntime(e);
    }
  }

  private boolean isClaimRequestCurrent(UUID playerId, ClaimKind kind, long expectedActivityMs) {
    if (kind == ClaimKind.PRESSURE) {
      return pressureGate.isEngaged();
    }

    Map<UUID, Long> activity = lastActivityMs;
    Long currentActivityMs = activity == null ? null : activity.get(playerId);
    return currentActivityMs != null && currentActivityMs.longValue() == expectedActivityMs;
  }

  private void releaseGeneration(long generation, Map<UUID, Player> players) {
    if (generation == 0L) {
      return;
    }

    for (Map.Entry<UUID, ViewDistanceState> entry : distanceStates.entrySet()) {
      UUID playerId = entry.getKey();
      ViewDistanceState state = entry.getValue();
      synchronized (state) {
        state.releaseGeneration(generation);
      }
      scheduleReconcile(playerId, state, players.get(playerId));
    }
  }

  private void releaseClaims(ClaimKind kind, long generation, Map<UUID, Player> players) {
    for (Map.Entry<UUID, ViewDistanceState> entry : distanceStates.entrySet()) {
      UUID playerId = entry.getKey();
      ViewDistanceState state = entry.getValue();
      boolean changed;
      synchronized (state) {
        changed = state.release(kind, generation);
      }
      if (changed) {
        scheduleReconcile(playerId, state, players.get(playerId));
      }
    }
  }

  private void releaseClaim(Player player, ClaimKind kind, long generation) {
    UUID playerId = player.getUniqueId();
    ViewDistanceState state = distanceStates.get(playerId);
    if (state == null) {
      return;
    }

    boolean changed;
    synchronized (state) {
      changed = state.release(kind, generation);
    }
    if (changed) {
      scheduleReconcile(playerId, state, player);
    }
  }

  private void scheduleReconcile(
      UUID playerId,
      ViewDistanceState state,
      Player snapshotPlayer
  ) {
    Player player = snapshotPlayer == null ? state.player() : snapshotPlayer;
    if (player == null) {
      synchronized (state) {
        if (state.isUnclaimed()) {
          distanceStates.remove(playerId, state);
        }
      }
      return;
    }

    ReconcileFlight flight;
    synchronized (state) {
      state.remember(player);
      if (state.reconcileFlight != null) {
        return;
      }
      flight = new ReconcileFlight(playerId, state);
      state.reconcileFlight = flight;
    }

    pendingReconciliations.incrementAndGet();
    dispatchReconcile(flight);
  }

  private void dispatchReconcile(ReconcileFlight flight) {
    Player player = flight.state.player();
    if (player == null) {
      completeReconcileFlight(flight, true, true);
      return;
    }

    AtomicBoolean completionOnce = new AtomicBoolean(false);
    Runnable operation = () -> {
      boolean succeeded = reconcile(flight.playerId, player, flight);
      if (completionOnce.compareAndSet(false, true)) {
        completeReconcileFlight(flight, succeeded, false);
      }
    };
    Runnable retired = () -> {
      if (completionOnce.compareAndSet(false, true)) {
        completeReconcileFlight(flight, true, true);
      }
    };

    try {
      if (!J.runEntity(player, operation, 0, retired)
          && completionOnce.compareAndSet(false, true)) {
        completeReconcileFlight(flight, false, false);
      }
    } catch (Throwable failure) {
      React.reportError(failure);
      if (completionOnce.compareAndSet(false, true)) {
        completeReconcileFlight(flight, false, false);
      }
    }
  }

  private void completeReconcileFlight(ReconcileFlight flight, boolean succeeded, boolean retired) {
    boolean retry = false;
    synchronized (flight.state) {
      if (flight.state.reconcileFlight != flight) {
        return;
      }

      if (retired
          || !succeeded
          || distanceStates.get(flight.playerId) != flight.state
          || flight.appliedRevision == flight.state.revision) {
        flight.state.reconcileFlight = null;
        if (flight.state.isUnclaimed()) {
          distanceStates.remove(flight.playerId, flight.state);
        }
      } else {
        retry = true;
      }
    }

    if (retry) {
      dispatchReconcile(flight);
      return;
    }

    if (!succeeded) {
      reconciliationFailures.incrementAndGet();
    }
    pendingReconciliations.decrementAndGet();
    signalReconciliations();
  }

  private boolean reconcile(UUID playerId, Player player, ReconcileFlight flight) {
    if (!player.isOnline()) {
      synchronized (flight.state) {
        flight.appliedRevision = flight.state.revision;
      }
      return true;
    }

    try {
      synchronized (flight.state) {
        if (distanceStates.get(playerId) != flight.state) {
          return true;
        }

        int current = (int) getSendViewDistanceMethod.invoke(player);
        reconcileLocked(playerId, player, flight.state, current);
        flight.appliedRevision = flight.state.revision;
      }
      return true;
    } catch (Throwable e) {
      failRuntime(e);
      return false;
    }
  }

  private boolean awaitReconciliations(long timeoutMillis) {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
    synchronized (reconciliationMonitor) {
      while (pendingReconciliations.get() > 0) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) {
          return false;
        }
        try {
          TimeUnit.NANOSECONDS.timedWait(reconciliationMonitor, remaining);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
    }
    return true;
  }

  private void signalReconciliations() {
    synchronized (reconciliationMonitor) {
      reconciliationMonitor.notifyAll();
    }
  }

  private void reconcileLocked(UUID playerId, Player player, ViewDistanceState state, int current) throws ReflectiveOperationException {
    int desired = state.desiredDistance();
    if (current != desired) {
      setSendViewDistanceMethod.invoke(player, desired);
    }

    if (state.isUnclaimed()) {
      distanceStates.remove(playerId, state);
    }
  }

  private Player[] playerSnapshot() {
    EntityController controller = React.controller(EntityController.class);
    return controller == null ? new Player[0] : controller.getFoliaPlayers();
  }

  static Map<UUID, Player> indexPlayers(Player[] players) {
    Map<UUID, Player> indexed = new HashMap<>(Math.max(1, players.length * 2));
    for (Player player : players) {
      if (player != null) {
        indexed.put(player.getUniqueId(), player);
      }
    }
    return indexed;
  }

  private void failRuntime(Throwable e) {
    if (warnedRuntimeFailure) {
      return;
    }

    warnedRuntimeFailure = true;
    setEnabled(false);
    React.reportError("Afk View Shedding disabled due to runtime incompatibility: "
        + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
  }

  private void markActive(Player player) {
    long generation = activeGeneration;
    Map<UUID, Long> activity = lastActivityMs;
    if (generation == 0L || activity == null) {
      return;
    }

    UUID playerId = player.getUniqueId();
    long now = System.currentTimeMillis();
    Long last = activity.get(playerId);
    if (last == null || now - last >= 1000L) {
      activity.put(playerId, now);
    }

    releaseClaim(player, ClaimKind.IDLE, generation);
  }

  @EventHandler
  public void on(PlayerMoveEvent e) {
    markActive(e.getPlayer());
  }

  @EventHandler
  public void on(PlayerInteractEvent e) {
    markActive(e.getPlayer());
  }

  @EventHandler
  public void on(PlayerJoinEvent e) {
    markActive(e.getPlayer());
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    Player player = e.getPlayer();
    UUID playerId = player.getUniqueId();
    Map<UUID, Long> activity = lastActivityMs;
    if (activity != null) {
      activity.remove(playerId);
    }

    ViewDistanceState state = distanceStates.get(playerId);
    if (state == null) {
      return;
    }

    synchronized (state) {
      state.releaseGeneration(activeGeneration);
      if (state.isUnclaimed()) {
        distanceStates.remove(playerId, state);
      }
    }
  }

  enum ClaimKind {
    IDLE,
    PRESSURE
  }

  private static final class ReconcileFlight {
    private final UUID playerId;
    private final ViewDistanceState state;
    private volatile long appliedRevision = -1L;

    private ReconcileFlight(UUID playerId, ViewDistanceState state) {
      this.playerId = playerId;
      this.state = state;
    }
  }

  static final class ViewDistanceState {
    private final int originalDistance;
    private WeakReference<Player> playerReference;
    private long idleGeneration;
    private long pressureGeneration;
    private int idleTarget;
    private int pressureTarget;
    private long revision;
    private ReconcileFlight reconcileFlight;

    ViewDistanceState(int originalDistance, Player player) {
      this.originalDistance = originalDistance;
      playerReference = new WeakReference<>(player);
    }

    synchronized boolean claim(ClaimKind kind, long generation, int target) {
      if (generation == 0L || originalDistance <= target) {
        return false;
      }

      if (kind == ClaimKind.IDLE) {
        idleGeneration = generation;
        idleTarget = target;
      } else {
        pressureGeneration = generation;
        pressureTarget = target;
      }
      revision++;
      return true;
    }

    synchronized boolean release(ClaimKind kind, long generation) {
      if (kind == ClaimKind.IDLE) {
        if (idleGeneration != generation) {
          return false;
        }
        idleGeneration = 0L;
        revision++;
        return true;
      }

      if (pressureGeneration != generation) {
        return false;
      }
      pressureGeneration = 0L;
      revision++;
      return true;
    }

    synchronized boolean hasClaim(ClaimKind kind, long generation, int target) {
      if (kind == ClaimKind.IDLE) {
        return idleGeneration == generation && idleTarget == target;
      }
      return pressureGeneration == generation && pressureTarget == target;
    }

    synchronized boolean releaseGeneration(long generation) {
      boolean changed = false;
      if (idleGeneration == generation) {
        idleGeneration = 0L;
        changed = true;
      }
      if (pressureGeneration == generation) {
        pressureGeneration = 0L;
        changed = true;
      }
      if (changed) {
        revision++;
      }
      return changed;
    }

    synchronized int desiredDistance() {
      int desired = originalDistance;
      if (idleGeneration != 0L) {
        desired = Math.min(desired, idleTarget);
      }
      if (pressureGeneration != 0L) {
        desired = Math.min(desired, pressureTarget);
      }
      return desired;
    }

    synchronized boolean isUnclaimed() {
      return idleGeneration == 0L && pressureGeneration == 0L;
    }

    synchronized void remember(Player player) {
      playerReference = new WeakReference<>(player);
    }

    synchronized Player player() {
      return playerReference.get();
    }
  }
}
