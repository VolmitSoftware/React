package com.volmit.react;

import art.arcane.multiburst.MultiBurst;
import com.volmit.react.core.controller.ActionController;
import com.volmit.react.core.controller.EventController;
import com.volmit.react.core.controller.PlayerController;
import com.volmit.react.core.controller.SampleController;
import com.volmit.react.legacyutil.*;
import com.volmit.react.legacyutil.Ticker;
import lombok.Getter;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


@Getter
public class React extends VolmitPlugin {
    public static BukkitAudiences audiences;
    @Instance
    public static React instance;
    public static Thread serverThread;
    public static Ticker ticker;
    public static MultiBurst burst;
    public static BukkitAudiences adventure;

    @Control
    private SampleController sampleController;
    @Control
    private PlayerController playerController;
    @Control
    private EventController eventController;
    @Control
    private ActionController actionController;

    public React() {
        instance = this;
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

    @Override
    public void onLoad() {
        instance = this;
        if (Bukkit.isPrimaryThread()) {
            serverThread = Thread.currentThread();
        }
        super.onLoad();
    }

    @Override
    public void onEnable() {
        instance = this;
        burst = new MultiBurst("React", Thread.MIN_PRIORITY);
        if (Bukkit.isPrimaryThread()) {
            serverThread = Thread.currentThread();
        }
        ticker = new Ticker(burst);
        adventure = BukkitAudiences.create(this);
        super.onEnable();
        ticker.register(new ControllerTicker(actionController, 100));
    }

    @Override
    public void onDisable() {
        stop();
        super.onDisable();
    }

    public File jar() {
        return getFile();
    }

    @Override
    public void start() {
        sampleController.postStart();
    }

    @Override
    public void stop() {
        ticker.close();
        burst.close();
    }

    @Override
    public String getTag(String subTag) {
        return C.BOLD + "" + C.DARK_GRAY + "[" + C.BOLD + "" + C.AQUA + "React" + C.BOLD + C.DARK_GRAY + "]" + C.RESET + "" + C.GRAY + ": ";
    }

    public Ticker getTicker() {
        return ticker;
    }

    public void reload() {
        stop();
        start();
    }
}
