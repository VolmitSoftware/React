package com.volmit.react.api.player;

import com.volmit.react.api.monitor.ActionBarMonitor;
import com.volmit.react.api.monitor.Monitor;
import com.volmit.react.configuration.ReactConfiguration;
import com.volmit.react.util.M;
import com.volmit.react.util.tick.TickedObject;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

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
    private float realYaw;
    private float realPitch;
    private float yaw;
    private float pitch;

    public ReactPlayer(Player player) {
        this.player = player;
        scrollPosition = 0;
        setInterval(ACTIVE_RATE);
        realYaw = 0f;
        realPitch = 0f;
        yaw = 0f;
        pitch = 0f;
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
        wakeUp();

        if(e.getTo() != null) {
           realYaw = e.getTo().getYaw();
           realPitch = e.getTo().getPitch();
        }
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        sneaking = e.isSneaking();
        wakeUp(true);
    }

    @EventHandler
    public void on(PlayerItemHeldEvent e) {
        scrollPosition += e.getNewSlot() - e.getPreviousSlot();
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

        yaw = (float) M.lerp(yaw, realYaw, 0.1d);
        pitch = (float) M.lerp(pitch, realPitch, 0.1d);
    }
}
