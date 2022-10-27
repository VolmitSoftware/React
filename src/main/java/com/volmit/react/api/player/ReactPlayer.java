package com.volmit.react.api.player;

import com.volmit.react.api.monitor.ActionBarMonitor;
import com.volmit.react.api.monitor.Monitor;
import com.volmit.react.configuration.ReactConfiguration;
import com.volmit.react.util.tick.TickedObject;
import lombok.Data;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

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

    public ReactPlayer(Player player) {
        this.player = player;
        scrollPosition = 0;
        setInterval(ACTIVE_RATE);
    }

    public void wakeUp() {
        lastActive = System.currentTimeMillis();
        setInterval(ACTIVE_RATE);

        if(actionBarMonitor != null) {
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
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        sneaking = e.isSneaking();
        wakeUp();
    }

    @EventHandler
    public void on(PlayerItemHeldEvent e) {
        scrollPosition += e.getNewSlot() - e.getPreviousSlot();
        wakeUp();
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
}
