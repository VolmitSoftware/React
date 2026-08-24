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

import art.arcane.chrono.ChronoLatch;
import art.arcane.react.React;
import art.arcane.react.api.monitor.ActionBarMonitor;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.util.common.scheduling.TickedObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.Objects;

@Data
@EqualsAndHashCode(callSuper = false)
public class ReactPlayer extends TickedObject {
  private static final int ACTIVE_RATE = 50;
  private static final int INACTIVE_RATE = 1000;
  private static final int INACTIVE_DELAY = 10000;
  private transient final Object motionLock = new Object();
  private final ChronoLatch saveLatch;
  private final Player player;
  private PlayerSettings settings;
  private volatile ActionBarMonitor actionBarMonitor;
  private volatile int scrollPosition;
  private volatile long lastActive;
  private volatile boolean sneaking;
  private double yawPosition;
  private float yaw;
  private float pitch;
  private long lastShift;
  private volatile boolean locked;
  private volatile double verticalVelocity;
  private volatile boolean speedValidForMonitor;
  private volatile int speedTickCooldown;
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
    scrollPosition = 0;
    setTinterval(ACTIVE_RATE);
    yaw = 0f;
    pitch = 0f;
    yawPosition = 0;
    lastActive = System.currentTimeMillis();
    lastShift = 0;
    speedTickCooldown = 0;
    locked = false;
    verticalVelocity = 0D;
    speedValidForMonitor = true;
    running = true;
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

  public boolean isMonitorSneaking() {
    return sneaking && isSpeedValidForMonitor() && speedTickCooldown <= 0;
  }

  public void wakeUp(boolean children) {
    lastActive = System.currentTimeMillis();
    setTinterval(ACTIVE_RATE);

    if (children && actionBarMonitor != null) {
      actionBarMonitor.wakeUp();
    }
  }

  public int getScrollPosition(int maxRemainder) {
    return Math.floorMod(scrollPosition, maxRemainder);
  }

  public void handleMove(PlayerMoveEvent e) {
    wakeUp();

    if (e.getTo() != null) {
      yaw = e.getTo().getYaw();
      pitch = e.getTo().getPitch();
      float v = ((e.getFrom().getYaw() + 600) - (yaw + 600)) / 5;
      yawPosition += (v < 12 && v > 0.01) ? 1 : v > -12 && v < -0.01 ? -1 : 0;
      if (yawPosition <= 0) {
        yawPosition = 1000000;
      }

      if (e.getFrom().getWorld().equals(e.getTo().getWorld())) {
        synchronized (motionLock) {
          verticalVelocity += e.getTo().getY() - e.getFrom().getY();
        }
      }
    }
  }

  public void handleToggleSneak(PlayerToggleSneakEvent e) {
    sneaking = e.isSneaking();
    wakeUp(true);

    if (e.isSneaking()) {
      if (getPlayer().isFlying()) {
        speedTickCooldown = 9;
      }
      long ls = lastShift;
      lastShift = System.currentTimeMillis();

      if (lastShift - ls < 250 && isMonitorSneaking()) {
        locked = !locked;
        lastShift = 0;
      }
    }
  }

  public void handleItemHeld(PlayerItemHeldEvent e) {
    if (!e.getPlayer().isSneaking()) {
      return;
    }

    scrollPosition += (e.getNewSlot() + 19) - (e.getPreviousSlot() + 19);
    wakeUp(sneaking);
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
        actionBarMonitor = new ActionBarMonitor(this);
      }
      actionBarMonitor.start();
    }

    if (saveSetting) {
      getSettings().setActionBarMonitoring(isActionBarMonitoring());
      saveSettings();
    }
  }


  @Override
  public boolean shouldTick() {
    return isTickDue(System.currentTimeMillis(), getTlastTick(), getTinterval());
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

    synchronized (motionLock) {
      verticalVelocity *= 0.75D;
      if (verticalVelocity < 0.01D && verticalVelocity > -0.01D) {
        verticalVelocity = 0D;
      }
      speedValidForMonitor = verticalVelocity > -1D;
    }
    if (speedTickCooldown > 0) {
      speedTickCooldown--;
    }
    if (saveLatch.flip()) {
      saveSettings();
    }
  }

  static boolean isTickDue(long now, long lastTick, long interval) {
    return now - lastTick >= interval;
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
