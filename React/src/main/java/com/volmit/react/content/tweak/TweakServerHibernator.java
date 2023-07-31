package com.volmit.react.content.tweak;

import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTweak;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.Listener;

public class TweakServerHibernator extends ReactTweak implements Listener {
    public static final String ID = "server-hibernator";
    private transient boolean firstRun = true;
    private double secondsPerTick = 1.0;


    public TweakServerHibernator() {
        super(ID);
    }

    @Override
    public void onActivate() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(React.instance, () -> {
            if (Bukkit.getOnlinePlayers().size() == 0) {
                if (this.firstRun) {
                    React.info("React Engaged Catatonic Sleep.");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all");
                }
                try {
                    Thread.sleep((long) (1000 * secondsPerTick));
                    this.firstRun = false;
                } catch (Exception ignored) {
                }
            } else {
                if (!firstRun) {
                    React.info("React Disengaged Catatonic Sleep, Waking Up.");
                }
                this.firstRun = true;
            }

        }, 0L, 1L);

    }

    @Override
    public void onDeactivate() {
        Bukkit.getScheduler().cancelTasks(React.instance);

    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {
    }
}
