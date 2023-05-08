package com.volmit.react;

import art.arcane.multiburst.MultiBurst;
import com.volmit.react.core.controller.ActionController;
import com.volmit.react.core.controller.CommandController;
import com.volmit.react.core.controller.EventController;
import com.volmit.react.core.controller.PlayerController;
import com.volmit.react.core.controller.SampleController;
import com.volmit.react.util.collection.KList;
import com.volmit.react.util.format.C;
import com.volmit.react.util.io.JarScanner;
import com.volmit.react.util.plugin.VolmitPlugin;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.scheduling.Ticker;
import lombok.Getter;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;

import java.io.File;
import java.lang.annotation.Annotation;


@Getter
public class React extends VolmitPlugin {
    public static BukkitAudiences audiences;
    public static React instance;
    public static Thread serverThread;
    public static Ticker ticker;
    public static MultiBurst burst;
    private SampleController sampleController;
    private PlayerController playerController;
    private EventController eventController;
    private ActionController actionController;
    private CommandController commandController;

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

    public static void reportError(Throwable e) {
        e.printStackTrace();
    }

    @Override
    public void onLoad() {
        instance = this;
        if (Bukkit.isPrimaryThread()) {
            serverThread = Thread.currentThread();
        }
        super.onLoad();
    }

    public File jar() {
        return getFile();
    }

    @Override
    public void start() {
        instance = this;
        burst = new MultiBurst("React", Thread.MIN_PRIORITY);
        ticker = new Ticker();
        audiences = BukkitAudiences.create(this);
        eventController = new EventController();
        playerController = new PlayerController();
        sampleController = new SampleController();
        actionController = new ActionController();
        commandController = new CommandController();
        sampleController.postStart();
    }

    @Override
    public void stop() {
        burst.close();
        ticker.clear();
        ticker.close();
        eventController.stop();
        playerController.stop();
        sampleController.stop();
        actionController.stop();
        commandController.stop();
    }

    @Override
    public String getTag(String subTag) {
        return C.BOLD + "" + C.DARK_GRAY + "[" + C.BOLD + "" + C.AQUA + "React" + C.BOLD + C.DARK_GRAY + "]" + C.RESET + "" + C.GRAY + ": ";
    }

    public Ticker getTicker() {
        return ticker;
    }

    public void reload() {
        onDisable();
        onEnable();
    }

    public static KList<Object> initialize(String s) {
        return initialize(s, null);
    }

    public static KList<Object> initialize(String s, Class<? extends Annotation> slicedClass) {
        JarScanner js = new JarScanner(instance.jar(), s);
        KList<Object> v = new KList<>();
        J.attempt(js::scan);
        for (Class<?> i : js.getClasses()) {
            if (slicedClass == null || i.isAnnotationPresent(slicedClass)) {
                try {
                    v.add(i.getDeclaredConstructor().newInstance());
                } catch (Throwable ignored) {

                }
            }
        }

        return v;
    }
}
