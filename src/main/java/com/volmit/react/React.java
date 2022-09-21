package com.volmit.react;

import com.volmit.react.api.monitor.ActionBarMonitor;
import com.volmit.react.controller.SampleController;
import com.volmit.react.sampler.SamplerChunksLoaded;
import com.volmit.react.sampler.SamplerEntities;
import com.volmit.react.sampler.SamplerMemoryGarbage;
import com.volmit.react.sampler.SamplerMemoryPressure;
import com.volmit.react.sampler.SamplerMemoryUsed;
import com.volmit.react.sampler.SamplerMemoryUsedAfterGC;
import com.volmit.react.sampler.SamplerProcessorProcessLoad;
import com.volmit.react.sampler.SamplerProcessorSystemLoad;
import com.volmit.react.sampler.SamplerTicksPerSecond;
import com.volmit.react.util.C;
import com.volmit.react.util.Control;
import com.volmit.react.util.Instance;
import com.volmit.react.util.J;
import com.volmit.react.util.VolmitPlugin;
import com.volmit.react.util.tick.Ticker;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

@Getter
public class React extends VolmitPlugin {
    @Instance
    public static React instance;
    private Ticker ticker;

    @Control
    private SampleController sampleController;

    @Override
    public void onEnable() {
        instance = this;
        ticker = new Ticker();
        super.onEnable();
    }

    @Override
    public void start() {
        sampleController.postStart();
    }

    @EventHandler
    public void on(PlayerJoinEvent e) {
        new ActionBarMonitor(e.getPlayer())
            .sample(SamplerTicksPerSecond.ID)
            .sample(SamplerMemoryUsedAfterGC.ID)
            .start();
    }

    @Override
    public void stop() {
        ticker.close();
    }

    @Override
    public String getTag(String subTag) {
        return C.BOLD + "" + C.DARK_GRAY + "[" + C.BOLD + "" + C.AQUA + "React" + C.BOLD + C.DARK_GRAY + "]" + C.RESET + "" + C.GRAY + ": ";
    }

    public static void warn(String string) {
        msg(C.YELLOW + string);
    }

    public static void error(String string) {
        msg(C.RED + string);
    }

    public static void verbose(String string) {
        msg(C.LIGHT_PURPLE + string);
    }

    public static void msg(String string) {
        try {
            if (instance == null) {
                System.out.println("[React]: " + string);
                return;
            }

            String msg = C.GRAY + "[" + C.AQUA + "React" + C.GRAY + "]: " + string;
            Bukkit.getConsoleSender().sendMessage(msg);
        } catch (Throwable e) {
            System.out.println("[React]: " + string);
        }
    }

    public static void success(String string) {
        msg(C.GREEN + string);
    }

    public static void info(String string) {
        msg(C.WHITE + string);
    }

    public static void debug(String string) {
        msg(C.DARK_PURPLE + string);
    }
}
