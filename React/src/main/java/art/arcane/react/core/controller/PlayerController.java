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

package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.model.PlayerSettings;
import art.arcane.react.model.ReactPlayer;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.react.util.plugin.IController;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@EqualsAndHashCode(callSuper = true)
@Data
public class PlayerController extends TickedObject implements IController {
  private static final long PRELOADED_PROFILE_RETENTION_MS = 60_000L;
  private static final long SAVE_DRAIN_TIMEOUT_MS = 30_000L;
  private static final int MAX_STARTUP_RESTORE_ATTEMPTS = 4;

  private transient final Map<UUID, ReactPlayer> players = new ConcurrentHashMap<>();
  private transient final Map<UUID, PreloadedProfile> preloadedProfiles = new ConcurrentHashMap<>();
  private transient final AtomicLong lifecycleGeneration = new AtomicLong(0L);
  private transient final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
  private transient volatile boolean active;

  public PlayerController() {
    super("react", "player", 30000);
  }

  @Override
  public void onTick() {
    long cutoff = System.currentTimeMillis() - PRELOADED_PROFILE_RETENTION_MS;
    for (Map.Entry<UUID, PreloadedProfile> entry : preloadedProfiles.entrySet()) {
      if (entry.getValue().loadedAtMS < cutoff) {
        preloadedProfiles.remove(entry.getKey(), entry.getValue());
      }
    }
  }

  @Override
  public String getName() {
    return "Player";
  }

  public ReactPlayer getPlayer(Player player) {
    if (player == null) {
      return null;
    }

    ReactPlayer reactPlayer = players.get(player.getUniqueId());
    if (reactPlayer != null || !player.isOnline() || !player.hasPermission("react.use")) {
      return reactPlayer;
    }

    return join(player);
  }

  @Override
  public void start() {
    long generation;
    lifecycleLock.writeLock().lock();
    try {
      generation = lifecycleGeneration.incrementAndGet();
      preloadedProfiles.clear();
      active = true;
    } finally {
      lifecycleLock.writeLock().unlock();
    }
    List<StartupPlayer> onlinePlayers = new ArrayList<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      onlinePlayers.add(new StartupPlayer(player.getUniqueId(), player));
    }
    if (!onlinePlayers.isEmpty()) {
      J.a(() -> restoreOnlinePlayers(onlinePlayers, generation));
    }
  }

  public ReactPlayer get(Player player) {
    return player == null ? null : players.get(player.getUniqueId());
  }

  @Override
  public void stop() {
    List<ReactPlayer> stoppedPlayers;
    lifecycleLock.writeLock().lock();
    try {
      active = false;
      lifecycleGeneration.incrementAndGet();
      preloadedProfiles.clear();
      stoppedPlayers = new ArrayList<>(players.values());
      players.clear();
    } finally {
      lifecycleLock.writeLock().unlock();
    }
    for (ReactPlayer reactPlayer : stoppedPlayers) {
      disposePlayer(reactPlayer);
    }
    if (!PlayerSettings.flushPendingSaves(SAVE_DRAIN_TIMEOUT_MS)) {
      throw new IllegalStateException(
          "Player settings write queue did not drain within " + SAVE_DRAIN_TIMEOUT_MS + "ms"
      );
    }
  }

  @Override
  public void postStart() {

  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(AsyncPlayerPreLoginEvent event) {
    if (!active || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
      return;
    }

    long generation = lifecycleGeneration.get();
    UUID playerId = event.getUniqueId();
    PlayerSettings settings = PlayerSettings.get(playerId);
    lifecycleLock.readLock().lock();
    try {
      if (active && generation == lifecycleGeneration.get()) {
        preloadedProfiles.put(playerId, new PreloadedProfile(settings, System.currentTimeMillis()));
      }
    } finally {
      lifecycleLock.readLock().unlock();
    }
  }

  @EventHandler
  public void on(PlayerJoinEvent e) {
    join(e.getPlayer());
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    preloadedProfiles.remove(e.getPlayer().getUniqueId());
    quit(e.getPlayer());
  }

  @EventHandler
  public void on(PlayerMoveEvent event) {
    ReactPlayer reactPlayer = players.get(event.getPlayer().getUniqueId());
    if (reactPlayer != null) {
      reactPlayer.handleMove(event);
    }
  }

  @EventHandler
  public void on(PlayerToggleSneakEvent event) {
    ReactPlayer reactPlayer = players.get(event.getPlayer().getUniqueId());
    if (reactPlayer != null) {
      reactPlayer.handleToggleSneak(event);
    }
  }

  @EventHandler
  public void on(PlayerItemHeldEvent event) {
    ReactPlayer reactPlayer = players.get(event.getPlayer().getUniqueId());
    if (reactPlayer != null) {
      reactPlayer.handleItemHeld(event);
    }
  }

  public ReactPlayer join(Player player) {
    if (player == null) {
      return null;
    }

    lifecycleLock.readLock().lock();
    try {
      if (!active || !player.hasPermission("react.use")) {
        return null;
      }

      UUID playerId = player.getUniqueId();
      return players.computeIfAbsent(
          playerId,
          ignored -> {
            PreloadedProfile preloaded = preloadedProfiles.remove(playerId);
            return preloaded == null
                ? createPlayer(player)
                : createPlayer(player, preloaded.settings);
          }
      );
    } finally {
      lifecycleLock.readLock().unlock();
    }
  }

  public void quit(Player player) {
    if (player == null) {
      return;
    }

    lifecycleLock.readLock().lock();
    try {
      ReactPlayer reactPlayer = players.remove(player.getUniqueId());
      if (reactPlayer != null) {
        disposePlayer(reactPlayer);
      }
    } finally {
      lifecycleLock.readLock().unlock();
    }
  }

  private void disposePlayer(ReactPlayer reactPlayer) {
    try {
      reactPlayer.onQuit();
    } finally {
      reactPlayer.unregister();
    }
  }

  public void updateMonitors() {
    for (ReactPlayer reactPlayer : players.values()) {
      reactPlayer.updateMonitors();
    }
  }

  ReactPlayer createPlayer(Player player) {
    ReactPlayer reactPlayer = new ReactPlayer(player);
    return initializePlayer(reactPlayer);
  }

  ReactPlayer createPlayer(Player player, PlayerSettings settings) {
    ReactPlayer reactPlayer = new ReactPlayer(player, settings);
    return initializePlayer(reactPlayer);
  }

  private ReactPlayer initializePlayer(ReactPlayer reactPlayer) {
    try {
      reactPlayer.onJoin();
      return reactPlayer;
    } catch (Throwable throwable) {
      reactPlayer.unregister();
      throw throwable;
    }
  }

  private void restoreOnlinePlayers(List<StartupPlayer> onlinePlayers, long generation) {
    for (StartupPlayer startupPlayer : onlinePlayers) {
      if (generation != lifecycleGeneration.get()) {
        return;
      }

      PlayerSettings settings = PlayerSettings.get(startupPlayer.playerId);
      if (generation != lifecycleGeneration.get()) {
        return;
      }

      scheduleStartupRestore(startupPlayer, settings, generation, 0);
    }
  }

  private void scheduleStartupRestore(
      StartupPlayer startupPlayer,
      PlayerSettings settings,
      long generation,
      int attempt
  ) {
    if (!active || generation != lifecycleGeneration.get()) {
      return;
    }

    AtomicBoolean terminal = new AtomicBoolean(false);
    Runnable retry = () -> retryStartupRestore(
        startupPlayer,
        settings,
        generation,
        attempt,
        terminal
    );
    boolean scheduled;
    try {
      scheduled = J.runEntity(
          startupPlayer.player,
          () -> completeStartupRestore(startupPlayer, settings, generation, terminal),
          0,
          retry
      );
    } catch (RuntimeException | Error failure) {
      React.reportError(failure);
      retry.run();
      return;
    }
    if (!scheduled) {
      retry.run();
    }
  }

  private void completeStartupRestore(
      StartupPlayer startupPlayer,
      PlayerSettings settings,
      long generation,
      AtomicBoolean terminal
  ) {
    if (!terminal.compareAndSet(false, true)) {
      return;
    }

    lifecycleLock.readLock().lock();
    try {
      if (!active
          || generation != lifecycleGeneration.get()
          || !startupPlayer.player.isOnline()
          || !startupPlayer.player.hasPermission("react.use")) {
        return;
      }
      players.computeIfAbsent(
          startupPlayer.playerId,
          ignored -> createPlayer(startupPlayer.player, settings)
      );
    } finally {
      lifecycleLock.readLock().unlock();
    }
  }

  private void retryStartupRestore(
      StartupPlayer startupPlayer,
      PlayerSettings settings,
      long generation,
      int attempt,
      AtomicBoolean terminal
  ) {
    if (!terminal.compareAndSet(false, true)) {
      return;
    }
    if (!active || generation != lifecycleGeneration.get()) {
      return;
    }
    if (attempt + 1 >= MAX_STARTUP_RESTORE_ATTEMPTS) {
      seedPreloadedProfile(startupPlayer.playerId, settings, generation);
      return;
    }

    try {
      J.a(() -> scheduleStartupRestore(startupPlayer, settings, generation, attempt + 1));
    } catch (RuntimeException | Error failure) {
      React.reportError(failure);
      seedPreloadedProfile(startupPlayer.playerId, settings, generation);
    }
  }

  private void seedPreloadedProfile(UUID playerId, PlayerSettings settings, long generation) {
    lifecycleLock.readLock().lock();
    try {
      if (active && generation == lifecycleGeneration.get() && !players.containsKey(playerId)) {
        preloadedProfiles.put(playerId, new PreloadedProfile(settings, System.currentTimeMillis()));
      }
    } finally {
      lifecycleLock.readLock().unlock();
    }
  }

  private record StartupPlayer(UUID playerId, Player player) {
  }

  private record PreloadedProfile(PlayerSettings settings, long loadedAtMS) {
  }
}
