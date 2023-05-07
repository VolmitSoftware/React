package com.volmit.react.model;

import com.volmit.react.React;
import com.volmit.react.api.monitor.ActionBarMonitor;
import com.volmit.react.legacyutil.ChronoLatch;
import com.volmit.react.legacyutil.MortarSender;
import com.volmit.react.legacyutil.TickedObject;
import lombok.Data;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

@Data
public class ReactPlayer extends TickedObject {
    private static final int ACTIVE_RATE = 50;
    private static final int INACTIVE_RATE = 1000;
    private static final int INACTIVE_DELAY = 10000;
    private final ChronoLatch saveLatch;
    private final Player player;
    private PlayerSettings settings;
    private ActionBarMonitor actionBarMonitor;
    private int scrollPosition;
    private long lastActive;
    private boolean sneaking;
    private double yawPosition;
    private float yaw;
    private float pitch;
    private long lastShift;
    private boolean locked;
    private Vector velocity;
    private boolean speedValidForMonitor;
    private int speedTickCooldown;
    private int lastHash = 0;

    public ReactPlayer(Player player) {
        super("react", player.getUniqueId().toString(), ACTIVE_RATE);
        settings = PlayerSettings.get(player.getUniqueId());
        lastHash = settings.hashCode();
        this.player = player;
        saveLatch = new ChronoLatch(60000);
        scrollPosition = 0;
        setInterval(ACTIVE_RATE);
        yaw = 0f;
        pitch = 0f;
        yawPosition = 0;
        lastShift = 0;
        speedTickCooldown = 0;
        locked = false;
        velocity = new Vector(0, 0, 0);
        speedValidForMonitor = true;
    }


    public void saveSettings() {
        saveSettings(false);
    }

    public void saveSettings(boolean force) {
        if (force || lastHash != settings.hashCode()) {
            PlayerSettings.saveSettings(player.getUniqueId(), settings);
            React.verbose("Saved " + player.getName() + "'s settings");
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
        setInterval(ACTIVE_RATE);

        if (children && actionBarMonitor != null) {
            actionBarMonitor.wakeUp();
        }
    }

    public int getScrollPosition(int maxRemainder) {
        int pos = scrollPosition;

        if (scrollPosition < 0) {
            pos += ((-scrollPosition / maxRemainder) * maxRemainder) + maxRemainder + maxRemainder;
        }

        return pos % maxRemainder;
    }

    @EventHandler
    public void on(PlayerMoveEvent e) {
        if (!e.getPlayer().equals(player)) {
            return;
        }

        wakeUp();

        if (e.getTo() != null) {
            yaw = e.getTo().getYaw();
            pitch = e.getTo().getPitch();
            float v = ((e.getFrom().getYaw() + 600) - (yaw + 600)) / 5;
            yawPosition += (v < 12 && v > 0.01) ? 1 : v > -12 && v < -0.01 ? -1 : 0;
            if (yawPosition <= 0) {
                yawPosition = 1000000;
            }

            if (e.getFrom().getWorld().getName().equals(e.getTo().getWorld().getName())) {
                velocity = e.getTo().toVector().subtract(e.getFrom().toVector()).clone().add(velocity);
            }
        }
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        if (!e.getPlayer().equals(player)) {
            return;
        }
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

    @EventHandler
    public void on(PlayerItemHeldEvent e) {
        if (!e.getPlayer().equals(player) || !e.getPlayer().isSneaking()) {
            return;
        }

        scrollPosition += (e.getNewSlot() + 19) - (e.getPreviousSlot() + 19);
        wakeUp(sneaking);
    }

    public void onJoin() {
        settings.init();

        if (settings.isActionBarMonitoring()) {
            setActionBarMonitoring(true);
            new MortarSender(getPlayer(), React.instance.getTag()).sendMessage("Monitor Enabled");
        } else {
            player.sendMessage("Not wasnt monitoring");
        }
    }

    public void onQuit() {
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

        if (!monitoring) {
            actionBarMonitor.stop();
            actionBarMonitor = null;
        } else {
            actionBarMonitor = new ActionBarMonitor(this);
            actionBarMonitor.start();
        }

        if (saveSetting) {
            getSettings().setActionBarMonitoring(isActionBarMonitoring());
            saveSettings();
        }
    }

    @Override
    public boolean shouldTick() {
        return true;
    }

    @Override
    public void onTick() {
        if (getInterval() > ACTIVE_RATE && System.currentTimeMillis() - lastActive > INACTIVE_DELAY) {
            setInterval(INACTIVE_RATE);
        }

        velocity.multiply(0.75);
        velocity.setX(velocity.getX() < 0.01 && velocity.getX() > -0.01 ? 0 : velocity.getX());
        velocity.setY(velocity.getY() < 0.01 && velocity.getY() > -0.01 ? 0 : velocity.getY());
        velocity.setZ(velocity.getZ() < 0.01 && velocity.getZ() > -0.01 ? 0 : velocity.getZ());
        speedTickCooldown--;
        speedValidForMonitor = velocity.getY() > -1;
        if (saveLatch.flip()) {
            saveSettings();
        }
    }

    public void updateMonitors() {
        if (isActionBarMonitoring()) {
            setActionBarMonitoring(false);
            setActionBarMonitoring(true);
        }
    }

    public void toggleActionBar() {
        setActionBarMonitoring(!isActionBarMonitoring());
    }
}
