package com.volmit.react.api.player;

import art.arcane.curse.Curse;
import com.volmit.react.React;
import com.volmit.react.api.monitor.ActionBarMonitor;
import com.volmit.react.configuration.ReactConfiguration;
import com.volmit.react.util.J;
import com.volmit.react.util.tick.TickedObject;
import lombok.Data;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReactPlayer extends TickedObject {
    private static final int ACTIVE_RATE = 50;
    private static final int INACTIVE_RATE = 1000;
    private static final int INACTIVE_DELAY = 10000;

    private final Player player;
    private ActionBarMonitor actionBarMonitor;
    private int scrollPosition;
    private long lastActive;
    private boolean sneaking;
    private double yawPosition;
    private float yaw;
    private float pitch;
    private long lastShift;
    private boolean locked;

    public ReactPlayer(Player player) {
        this.player = player;
        scrollPosition = 0;
        setInterval(ACTIVE_RATE);
        yaw = 0f;
        pitch = 0f;
        yawPosition = 0;
        lastShift = 0;
        locked = false;
    }

    public void wakeUp() {
        wakeUp(false);
    }

    public void wakeUp(boolean children) {
        lastActive = System.currentTimeMillis();
        setInterval(ACTIVE_RATE);

        if(children && actionBarMonitor != null) {
            actionBarMonitor.wakeUp();
        }
    }

    public int getScrollPosition(int maxRemainder)
    {
        int pos = scrollPosition;

        if(scrollPosition < 0) {
            pos += ((-scrollPosition / maxRemainder) * maxRemainder) + maxRemainder + maxRemainder;
        }

        return pos % maxRemainder;
    }

    @EventHandler
    public void on(PlayerMoveEvent e) {
        if(!e.getPlayer().equals(player))
        {
            return;
        }

        wakeUp();

        if(e.getTo() != null) {

           yaw = e.getTo().getYaw();
           pitch = e.getTo().getPitch();
           float v = ((e.getFrom().getYaw() + 600) - (yaw +  600)) / 5;
           yawPosition += (v < 12 && v > 0.01) ? 1 : v > -12 && v < -0.01 ? -1 : 0;
            if(yawPosition <= 0) {
                yawPosition = 1000000;
            }
        }
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        if(!e.getPlayer().equals(player))
        {
            return;
        }
        sneaking = e.isSneaking();
        wakeUp(true);

        if(e.isSneaking()) {
            long ls = lastShift;
            lastShift = Math.ms();

            if(lastShift - ls < 250) {
                locked = !locked;
                lastShift = 0;
            }
        }
    }

    @EventHandler
    public void on(PlayerItemHeldEvent e) {
        if(!e.getPlayer().equals(player) || !e.getPlayer().isSneaking())
        {
            return;
        }

        scrollPosition += (e.getNewSlot() + 19) - (e.getPreviousSlot() + 19);
        wakeUp(sneaking);
    }

    public void onJoin() {
        setActionBarMonitoring(true);
    }

    public void onQuit() {
        setActionBarMonitoring(false);
    }

    public boolean isMonitoring() {
        return actionBarMonitor != null;
    }

    public void setActionBarMonitoring(boolean monitoring) {
        if(monitoring == isMonitoring()) {
            return;
        }

        if(!monitoring) {
            actionBarMonitor.stop();
            actionBarMonitor = null;
        }

        else{
            actionBarMonitor = new ActionBarMonitor(this)
                .sample(ReactConfiguration.get().getMonitorConfiguration());
            actionBarMonitor.start();
        }
    }

    @Override
    public boolean shouldTick() {
        return true;
    }

    @Override
    public void onTick() {
        if(getInterval() > ACTIVE_RATE && System.currentTimeMillis() - lastActive > INACTIVE_DELAY) {
            setInterval(INACTIVE_RATE);
        }
    }

    public void updateMonitors() {
        if(isMonitoring()) {
            setActionBarMonitoring(false);
            setActionBarMonitoring(true);
        }
    }
}
