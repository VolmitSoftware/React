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

import art.arcane.react.React;
import art.arcane.react.api.monitor.ActionBarMonitor;
import art.arcane.react.api.monitor.MonitorInput;
import art.arcane.react.api.monitor.MonitorViewer;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.platform.bukkit.BukkitMonitorDisplay;
import art.arcane.react.util.common.scheduling.TickedObject;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.Objects;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = false)
public class ReactPlayer extends TickedObject implements MonitorViewer {
  private static final int ACTIVE_RATE = 50;
  private static final int INACTIVE_RATE = 1000;
  private static final int INACTIVE_DELAY = 10000;
  private final ChronoLatch saveLatch;
  private final Player player;
  private final MonitorInput input = new MonitorInput();
  private PlayerSettings settings;
  private volatile ActionBarMonitor actionBarMonitor;
  private volatile long lastActive;
  private int lastHash;
  private volatile boolean running;

  public ReactPlayer(Player player) {
    this(player, PlayerSettings.get(player.getUniqueId()));
  }

  public ReactPlayer(Player player, PlayerSettings settings) {
    super("react", player.getUniqueId().toString(), ACTIVE_RATE);
    this.settings = Objects.requireNonNull(settings);
    lastHash = this.settings.hashCode();
    this.player = player;
    saveLatch = new ChronoLatch(60000);
    setTinterval(ACTIVE_RATE);
    lastActive = System.currentTimeMillis();
    running = true;
  }

  @Override
  public UUID viewerId() {
    return player.getUniqueId();
  }

  public void saveSettings() {
    saveSettings(false);
  }

  public void saveSettings(boolean force) {
    if (force || lastHash != settings.hashCode()) {
      PlayerSettings.saveSettings(player.getUniqueId(), settings);
      React.verbose(() -> "Saved " + player.getName() + "'s settings");
    }
    lastHash = settings.hashCode();
  }

  public void wakeUp() {
    wakeUp(false);
  }

  @Override
  public boolean isMonitorSneaking() {
    return input.isMonitorSneaking();
  }

  @Override
  public boolean isLocked() {
    return input.isLocked();
  }

  @Override
  public int getScrollPosition(int maxRemainder) {
    return input.getScrollPosition(maxRemainder);
  }

  public void wakeUp(boolean children) {
    lastActive = System.currentTimeMillis();
    setTinterval(ACTIVE_RATE);
    if (children && actionBarMonitor != null) {
      actionBarMonitor.wakeUp();
    }
  }

  public void handleMove(PlayerMoveEvent e) {
    wakeUp();
    if (e.getTo() != null && e.getFrom().getWorld().equals(e.getTo().getWorld())) {
      input.moved(e.getTo().getY() - e.getFrom().getY());
    }
  }

  public void handleToggleSneak(PlayerToggleSneakEvent e) {
    input.sneak(e.isSneaking(), getPlayer().isFlying(), System.currentTimeMillis());
    wakeUp(true);
  }

  public void handleItemHeld(PlayerItemHeldEvent e) {
    if (input.scrolled(e.getPreviousSlot(), e.getNewSlot(), e.getPlayer().isSneaking())) {
      wakeUp(input.isSneaking());
    }
  }

  public void onJoin() {
    settings.init();
    if (settings.isActionBarMonitoring()) {
      setActionBarMonitoring(true);
      ReactLanguage.sendPrefixed(getPlayer(), RuntimeMessages.MONITOR_ENABLED);
    }
  }

  public void onQuit() {
    running = false;
    setActionBarMonitoring(false, false);
    saveSettings(true);
  }

  public boolean isActionBarMonitoring() {
    return actionBarMonitor != null;
  }

  public void setActionBarMonitoring(boolean monitoring) {
    setActionBarMonitoring(monitoring, true);
  }

  public void setActionBarMonitoring(boolean monitoring, boolean saveSetting) {
    if (monitoring == isActionBarMonitoring()) {
      return;
    }

    if (!monitoring && actionBarMonitor != null) {
      actionBarMonitor.stop();
      actionBarMonitor = null;
    } else if (monitoring) {
      if (actionBarMonitor == null) {
        actionBarMonitor = new ActionBarMonitor(this, BukkitMonitorDisplay.INSTANCE);
      }
      actionBarMonitor.start();
    }

    if (saveSetting) {
      getSettings().setActionBarMonitoring(isActionBarMonitoring());
      saveSettings();
    }
  }

  @Override
  public void onTick() {
    if (!running) {
      return;
    }

    long now = System.currentTimeMillis();
    if (shouldUseInactiveRate(now, lastActive, getTinterval())) {
      setTinterval(INACTIVE_RATE);
    }
    input.tick();
    if (saveLatch.flip()) {
      saveSettings();
    }
  }

  static boolean shouldUseInactiveRate(long now, long lastActive, long interval) {
    return interval <= ACTIVE_RATE && now - lastActive > INACTIVE_DELAY;
  }

  public void updateMonitors() {
    if (actionBarMonitor != null) {
      actionBarMonitor.refreshConfiguration();
    }
  }

  public void toggleActionBar() {
    setActionBarMonitoring(!isActionBarMonitoring() || actionBarMonitor == null);
  }
}
