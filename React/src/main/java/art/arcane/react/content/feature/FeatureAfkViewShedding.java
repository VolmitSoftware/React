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
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Afk View Shedding feature. Idle players receive a reduced send view distance, cutting chunk packets and entity tracker fanout, and are restored instantly on activity.")
public class FeatureAfkViewShedding extends ReactFeature implements Listener {
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
  private int pressureSendViewDistanceCap = 6;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds required before the pressure notch engages.", impact = "Lower values cap view distance earlier; higher values reserve it for heavier load.")
  private double pressureEngageTickTimeMs = 60;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Average tick milliseconds the server must stay below before the pressure notch releases.", impact = "Lower values hold the cap longer for stability; higher values restore range sooner.")
  private double pressureReleaseTickTimeMs = 45;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Sustained pressure duration required before the notch engages (milliseconds).", impact = "Higher values ignore short spikes; lower values engage faster.")
  private long pressureSustainEngageMs = 6000;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Sustained recovery duration required before the notch releases (milliseconds).", impact = "Higher values avoid flapping between states; lower values restore range sooner.")
  private long pressureSustainReleaseMs = 30_000;
  private transient Map<UUID, Long> lastActivityMs;
  private transient Map<UUID, Integer> originalSendViewDistance;
  private transient Map<UUID, Integer> pressureOriginal;
  private transient boolean pressureEngaged;
  private transient long pressureSinceMs;
  private transient long pressureCalmSinceMs;
  private transient Method getSendViewDistanceMethod;
  private transient Method setSendViewDistanceMethod;
  private transient boolean supportsSendViewDistance;
  private transient boolean warnedRuntimeFailure;

  public FeatureAfkViewShedding() {
    super(ID);
  }

  @Override
  public void onActivate() {
    lastActivityMs = new ConcurrentHashMap<>();
    originalSendViewDistance = new ConcurrentHashMap<>();
    pressureOriginal = new ConcurrentHashMap<>();
    pressureEngaged = false;
    pressureSinceMs = 0;
    pressureCalmSinceMs = 0;
    warnedRuntimeFailure = false;
    supportsSendViewDistance = false;
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
    if (originalSendViewDistance != null) {
      for (UUID playerId : originalSendViewDistance.keySet()) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
          restore(player);
        }
      }

      originalSendViewDistance.clear();
    }

    if (pressureEngaged) {
      releasePressure();
    }

    if (lastActivityMs != null) {
      lastActivityMs.clear();
    }
  }

  @Override
  public int getTickInterval() {
    return Math.max(1000, tickIntervalMS);
  }

  @Override
  public void onTick() {
    if (!supportsSendViewDistance || lastActivityMs == null) {
      return;
    }

    double tickMs = sampleTickMs();
    tickPressureNotch(tickMs);

    if (minTickTimeMs > 0 && tickMs < minTickTimeMs) {
      return;
    }

    long now = System.currentTimeMillis();
    long idleAfterMs = Math.max(10_000L, idleAfterSeconds * 1000L);
    for (Player player : Bukkit.getOnlinePlayers()) {
      UUID playerId = player.getUniqueId();
      Long last = lastActivityMs.get(playerId);
      if (last == null) {
        lastActivityMs.put(playerId, now);
        continue;
      }

      if (now - last >= idleAfterMs && !originalSendViewDistance.containsKey(playerId)) {
        shed(player);
      }
    }
  }

  private void tickPressureNotch(double tickMs) {
    if (!pressureNotch) {
      return;
    }

    long now = System.currentTimeMillis();
    if (!pressureEngaged) {
      if (tickMs < pressureEngageTickTimeMs) {
        pressureSinceMs = 0;
        return;
      }

      if (pressureSinceMs == 0) {
        pressureSinceMs = now;
        return;
      }

      if (now - pressureSinceMs >= Math.max(0, pressureSustainEngageMs)) {
        pressureEngaged = true;
        pressureCalmSinceMs = 0;
        applyPressureCaps();
      }

      return;
    }

    applyPressureCaps();
    if (tickMs > pressureReleaseTickTimeMs) {
      pressureCalmSinceMs = 0;
      return;
    }

    if (pressureCalmSinceMs == 0) {
      pressureCalmSinceMs = now;
      return;
    }

    if (now - pressureCalmSinceMs >= Math.max(0, pressureSustainReleaseMs)) {
      releasePressure();
    }
  }

  private void applyPressureCaps() {
    int cap = Math.max(2, pressureSendViewDistanceCap);
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (pressureOriginal.containsKey(player.getUniqueId())) {
        continue;
      }

      J.runEntity(player, () -> {
        if (!player.isOnline() || pressureOriginal == null) {
          return;
        }

        try {
          int current = (int) getSendViewDistanceMethod.invoke(player);
          if (current <= cap) {
            return;
          }

          pressureOriginal.put(player.getUniqueId(), current);
          setSendViewDistanceMethod.invoke(player, cap);
        } catch (Throwable e) {
          failRuntime(e);
        }
      });
    }
  }

  private void releasePressure() {
    pressureEngaged = false;
    pressureSinceMs = 0;
    pressureCalmSinceMs = 0;

    for (Map.Entry<UUID, Integer> entry : pressureOriginal.entrySet()) {
      UUID playerId = entry.getKey();
      int original = entry.getValue();
      Player player = Bukkit.getPlayer(playerId);
      if (player == null || !player.isOnline()) {
        continue;
      }

      // If the player went idle while capped, their idle-restore value is the capped
      // distance; fix the stored value instead of touching the live distance.
      if (originalSendViewDistance.containsKey(playerId)) {
        originalSendViewDistance.put(playerId, original);
        continue;
      }

      J.runEntity(player, () -> {
        if (!player.isOnline()) {
          return;
        }

        try {
          setSendViewDistanceMethod.invoke(player, original);
        } catch (Throwable e) {
          failRuntime(e);
        }
      });
    }

    pressureOriginal.clear();
  }

  private void shed(Player player) {
    J.runEntity(player, () -> {
      if (!player.isOnline() || originalSendViewDistance == null) {
        return;
      }

      try {
        int current = (int) getSendViewDistanceMethod.invoke(player);
        int target = Math.max(2, idleSendViewDistance);
        if (current <= target) {
          return;
        }

        originalSendViewDistance.put(player.getUniqueId(), current);
        setSendViewDistanceMethod.invoke(player, target);
      } catch (Throwable e) {
        failRuntime(e);
      }
    });
  }

  private void restore(Player player) {
    if (originalSendViewDistance == null) {
      return;
    }

    Integer original = originalSendViewDistance.remove(player.getUniqueId());
    if (original == null) {
      return;
    }

    J.runEntity(player, () -> {
      if (!player.isOnline()) {
        return;
      }

      try {
        setSendViewDistanceMethod.invoke(player, original.intValue());
      } catch (Throwable e) {
        failRuntime(e);
      }
    });
  }

  private void failRuntime(Throwable e) {
    if (warnedRuntimeFailure) {
      return;
    }

    warnedRuntimeFailure = true;
    setEnabled(false);
    React.warn("Afk View Shedding disabled due to runtime incompatibility: " + e.getClass().getSimpleName() + ": " + e.getMessage());
  }

  private double sampleTickMs() {
    try {
      art.arcane.react.api.sampler.Sampler sampler = React.sampler(art.arcane.react.content.sampler.SamplerTickTime.ID);
      return sampler == null ? 0D : sampler.sample();
    } catch (Throwable ignored) {
      return 0D;
    }
  }

  private void markActive(Player player) {
    if (lastActivityMs == null) {
      return;
    }

    UUID playerId = player.getUniqueId();
    long now = System.currentTimeMillis();
    Long last = lastActivityMs.get(playerId);
    if (last == null || now - last >= 1000L) {
      lastActivityMs.put(playerId, now);
    }

    if (originalSendViewDistance != null && originalSendViewDistance.containsKey(playerId)) {
      restore(player);
    }
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
    if (lastActivityMs != null) {
      lastActivityMs.remove(e.getPlayer().getUniqueId());
    }
    if (originalSendViewDistance != null) {
      originalSendViewDistance.remove(e.getPlayer().getUniqueId());
    }
    if (pressureOriginal != null) {
      pressureOriginal.remove(e.getPlayer().getUniqueId());
    }
  }
}
