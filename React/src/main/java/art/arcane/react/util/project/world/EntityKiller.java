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

package art.arcane.react.util.project.world;

import art.arcane.react.React;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.format.C;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class EntityKiller {
  private static final long DEFAULT_CLEANUP_TIMEOUT_MS = 30_000L;
  private static final NamespacedKey nsKillerCountdown = Objects.requireNonNull(NamespacedKey.fromString("react:react-killer-countdown"));
  private static final Sound DEATH_SOUND = Sound.sound(
      Key.key("minecraft:particle.soul_escape"),
      Sound.Source.NEUTRAL,
      0.5f,
      0.9f
  );
  private static final Map<UUID, EntityKiller> ACTIVE = new ConcurrentHashMap<>();
  private static final Object REGISTRY_LOCK = new Object();
  private static final Object LISTENER_LOCK = new Object();
  private static SharedListener sharedListener;
  private static boolean accepting = true;
  private static int cleanupInFlight;
  private final Entity entity;
  private final UUID entityId;
  private final AtomicBoolean cleanupComplete;
  private final AtomicBoolean owned;
  private final AtomicBoolean stopping;
  private int seconds;
  private int tt;
  private boolean stamped;
  private boolean preserveCustomName;

  public EntityKiller(Entity e, int seconds) {
    this.entity = Objects.requireNonNull(e, "entity");
    this.entityId = Objects.requireNonNull(e.getUniqueId(), "entity unique id");
    this.cleanupComplete = new AtomicBoolean(false);
    this.owned = new AtomicBoolean(false);
    this.stopping = new AtomicBoolean(false);
    this.seconds = seconds;
    this.tt = 0;
    synchronized (REGISTRY_LOCK) {
      if (!accepting || ACTIVE.putIfAbsent(entityId, this) != null) {
        return;
      }

      owned.set(true);
      cleanupInFlight++;
      try {
        ensureListener();
        tt = J.sr(this::tick, 20);
      } catch (RuntimeException | Error throwable) {
        completeCleanup();
        throw throwable;
      }
    }
  }

  public static void startAccepting() {
    synchronized (REGISTRY_LOCK) {
      accepting = true;
    }
  }

  public static void stopAll() {
    if (!stopAll(DEFAULT_CLEANUP_TIMEOUT_MS)) {
      throw new IllegalStateException(
          "Entity killer cleanup did not drain within " + DEFAULT_CLEANUP_TIMEOUT_MS + "ms"
      );
    }
  }

  public static boolean stopAll(long timeoutMs) {
    List<EntityKiller> active;
    synchronized (REGISTRY_LOCK) {
      accepting = false;
      active = new ArrayList<>(ACTIVE.values());
    }

    for (EntityKiller killer : active) {
      try {
        killer.stop();
      } catch (Throwable throwable) {
        React.reportError(throwable);
        killer.completeCleanup();
      }
    }

    boolean drained = awaitCleanup(timeoutMs);
    releaseListenerIfIdle();
    return drained;
  }

  static int activeCount() {
    return ACTIVE.size();
  }

  static int cleanupInFlightCount() {
    synchronized (REGISTRY_LOCK) {
      return cleanupInFlight;
    }
  }

  private static boolean awaitCleanup(long timeoutMs) {
    long remainingNanos = Math.max(0L, TimeUnit.MILLISECONDS.toNanos(timeoutMs));
    long deadline = System.nanoTime() + remainingNanos;
    synchronized (REGISTRY_LOCK) {
      while (cleanupInFlight > 0) {
        if (remainingNanos <= 0L) {
          return false;
        }

        try {
          TimeUnit.NANOSECONDS.timedWait(REGISTRY_LOCK, remainingNanos);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          return false;
        }
        remainingNanos = deadline - System.nanoTime();
      }
    }
    return true;
  }

  private static void ensureListener() {
    synchronized (LISTENER_LOCK) {
      if (sharedListener == null && React.instance != null) {
        sharedListener = new SharedListener();
        React.instance.registerListener(sharedListener);
      }
    }
  }

  private static void releaseListenerIfIdle() {
    synchronized (LISTENER_LOCK) {
      if (ACTIVE.isEmpty() && sharedListener != null) {
        if (React.instance != null) {
          React.instance.unregisterListener(sharedListener);
        }
        sharedListener = null;
      }
    }
  }

  public void stop() {
    synchronized (REGISTRY_LOCK) {
      if (!owned.get() || !stopping.compareAndSet(false, true)) {
        return;
      }
    }

    try {
      if (J.runEntity(entity, this::cleanupOwned, 0, this::completeCleanup)) {
        return;
      }
    } catch (RuntimeException | Error throwable) {
      completeCleanup();
      throw throwable;
    }

    completeCleanup();
  }

  public static void reconcile(Entity entity) {
    PersistentDataContainer container = entity.getPersistentDataContainer();
    Byte hadCustomName = container.get(nsKillerCountdown, PersistentDataType.BYTE);
    if (hadCustomName == null) {
      return;
    }

    container.remove(nsKillerCountdown);
    if (hadCustomName != 0) {
      return;
    }

    entity.setCustomNameVisible(false);
    entity.setCustomName(null);
  }

  private void cleanupOwned() {
    Throwable failure = null;
    if (stamped && !preserveCustomName) {
      try {
        entity.setCustomNameVisible(false);
      } catch (Throwable throwable) {
        failure = throwable;
      }
      try {
        entity.setCustomName(null);
      } catch (Throwable throwable) {
        failure = combineCleanupFailure(failure, throwable);
      }
    }
    try {
      entity.getPersistentDataContainer().remove(nsKillerCountdown);
    } catch (Throwable throwable) {
      failure = combineCleanupFailure(failure, throwable);
    }
    try {
      completeCleanup();
    } catch (Throwable throwable) {
      failure = combineCleanupFailure(failure, throwable);
    }

    if (failure instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    if (failure instanceof Error error) {
      throw error;
    }
    if (failure != null) {
      throw new IllegalStateException("Entity killer cleanup failed", failure);
    }
  }

  private Throwable combineCleanupFailure(Throwable first, Throwable next) {
    if (first == null) {
      return next;
    }
    if (first != next) {
      first.addSuppressed(next);
    }
    return first;
  }

  private void completeCleanup() {
    if (!cleanupComplete.compareAndSet(false, true)) {
      return;
    }

    Throwable failure = null;
    synchronized (REGISTRY_LOCK) {
      ACTIVE.remove(entityId, this);
      owned.set(false);
      stopping.set(true);
      try {
        cancelTimer();
      } catch (Throwable throwable) {
        failure = throwable;
      }
      cleanupInFlight--;
      REGISTRY_LOCK.notifyAll();
    }

    try {
      releaseListenerIfIdle();
    } catch (Throwable throwable) {
      failure = combineCleanupFailure(failure, throwable);
    }

    if (failure instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    if (failure instanceof Error error) {
      throw error;
    }
  }

  private void cancelTimer() {
    if (tt != 0) {
      J.csr(tt);
      tt = 0;
    }
  }

  private void tick() {
    if (!owned.get() || stopping.get()) {
      return;
    }

    if (!J.runEntity(entity, this::tickOwned, 0, this::completeCleanup)) {
      stop();
    }
  }

  private void tickOwned() {
    if (!owned.get() || stopping.get()) {
      return;
    }

    if (entity.isDead()) {
      stop();
      return;
    }

    seconds--;
    if (seconds <= 0) {
      kill();
      return;
    }

    stampCountdown();
    if (preserveCustomName) {
      return;
    }

    entity.setCustomName(C.RED + ReactLanguage.plain(
        RuntimeMessages.ENTITY_KILLER_COUNTDOWN,
        MessageArgument.untrusted("seconds", seconds)
    ));
    entity.setCustomNameVisible(true);
  }

  private void stampCountdown() {
    if (stamped) {
      return;
    }

    stamped = true;
    preserveCustomName = entity.getCustomName() != null;
    byte hadCustomName = (byte) (preserveCustomName ? 1 : 0);
    entity.getPersistentDataContainer().set(nsKillerCountdown, PersistentDataType.BYTE, hadCustomName);
  }

  public void kill() {
    if (!owned.get() || stopping.get()) {
      return;
    }

    if (!J.runEntity(entity, this::killOwned, 0, this::completeCleanup)) {
      stop();
    }
  }

  private void killOwned() {
    if (!owned.get() || stopping.get()) {
      return;
    }

    if (entity.isDead()) {
      stop();
      return;
    }

    stop();
    Location location = entity.getLocation();
    entity.getWorld().spawnParticle(Particle.FLASH, location, 1, Color.WHITE);
    playDeathSound(location);
    entity.remove();
  }

  void playDeathSound(Location location) {
    World world = location.getWorld();
    if (world == null) {
      return;
    }

    UUID worldId = world.getUID();
    double x = location.getX();
    double y = location.getY();
    double z = location.getZ();
    if (!J.isFoliaThreading()) {
      for (Player player : world.getPlayers()) {
        playDeathSound(player, worldId, x, y, z, DEATH_SOUND);
      }
      return;
    }

    EntityController controller = React.controller(EntityController.class);
    Player[] players = controller == null ? null : controller.getFoliaPlayers();
    if (players == null) {
      return;
    }

    for (Player player : players) {
      if (player == null) {
        continue;
      }

      try {
        FoliaScheduler.runEntity(
            React.instance,
            player,
            () -> playDeathSoundOwned(player, worldId, x, y, z, DEATH_SOUND),
            0,
            null
        );
      } catch (Throwable throwable) {
        React.reportError(throwable);
      }
    }
  }

  private void playDeathSoundOwned(Player player, UUID worldId, double x, double y, double z, Sound sound) {
    if (!J.isOwnedByCurrentRegion(player)) {
      return;
    }

    playDeathSound(player, worldId, x, y, z, sound);
  }

  private void playDeathSound(Player player, UUID worldId, double x, double y, double z, Sound sound) {
    if (!player.isOnline()) {
      return;
    }

    World playerWorld = player.getWorld();
    if (playerWorld == null || !worldId.equals(playerWorld.getUID())) {
      return;
    }

    React.audiences().player(player).playSound(sound, x, y, z);
  }


  public static final class SharedListener implements Listener {
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void on(EntityPickupItemEvent e) {
      EntityKiller killer = ACTIVE.get(e.getItem().getUniqueId());
      if (killer != null) {
        killer.stop();
      }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void on(PlayerInteractEntityEvent event) {
      EntityKiller killer = ACTIVE.get(event.getRightClicked().getUniqueId());
      if (killer == null) {
        return;
      }

      React.verbose(() -> "EntityKiller: countdown cancelled by player " + event.getPlayer().getName());
      killer.stop();
    }
  }
}
