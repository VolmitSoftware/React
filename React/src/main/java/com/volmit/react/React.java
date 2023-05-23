package com.volmit.react;

import art.arcane.chrono.PrecisionStopwatch;
import art.arcane.multiburst.MultiBurst;
import com.volmit.react.api.action.Action;
import com.volmit.react.api.feature.Feature;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.api.tweak.Tweak;
import com.volmit.react.content.sampler.SamplerMemoryUsed;
import com.volmit.react.content.tweak.TweakUnknown;
import com.volmit.react.core.controller.*;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.plugin.IController;
import com.volmit.react.util.registry.Registry;
import com.volmit.react.util.world.EntityKiller;
import com.volmit.react.util.collection.KList;
import com.volmit.react.util.format.C;
import com.volmit.react.util.io.JarScanner;
import com.volmit.react.util.plugin.SplashScreen;
import com.volmit.react.util.plugin.VolmitPlugin;
import com.volmit.react.util.scheduling.J;
import com.volmit.react.util.scheduling.Ticker;
import lombok.Getter;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.minecraft.world.level.block.BlockHopper;
import net.minecraft.world.level.block.entity.TileEntityHopper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;


@Getter
public class React extends VolmitPlugin {
    public static BukkitAudiences audiences;
    public static React instance;
    public static Thread serverThread;
    public static Ticker ticker;
    public static MultiBurst burst;
    private List<Runnable> startupTasks;
    private List<Runnable> prejobs;
    private Registry<IController> controllerRegistry;
    private boolean ready;

    public React() {
        instance = this;
        ready = false;
    }

    public static boolean hasNearbyPlayer(Location l, double blocks) {
        for(Player i : l.getWorld().getPlayers()) {
            if(i.getLocation().distanceSquared(l) <= blocks * blocks) {
                return true;
            }
        }

        return false;
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

    public static void kill(Entity e) {
        new EntityKiller(e, 5);
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
        PrecisionStopwatch psw = PrecisionStopwatch.start();
        startupTasks = new CopyOnWriteArrayList<>();
        prejobs = new CopyOnWriteArrayList<>();
        burst = new MultiBurst("React", Thread.MIN_PRIORITY);
        ticker = new Ticker();
        audiences = BukkitAudiences.create(this);
        controllerRegistry = new Registry<>(IController.class, "com.volmit.react.core.controller");

        for(Runnable i : startupTasks) {
            i.run();
        }

        startupTasks.clear();

        for(IController i : controllerRegistry.all()) {
            i.start();
        }

        for(Runnable i : startupTasks) {
            i.run();
        }

        startupTasks.clear();

        info(SplashScreen.splash);

        for(IController i : controllerRegistry.all()) {
            i.postStart();

            if(i instanceof Listener l) {
                registerListener(l);
            }
        }

        ready = true;

        for(Runnable i : prejobs) {
            controller(JobController.class).queue(i);
        }

        React.info("React Started in " + Form.duration(psw.getMilliseconds(), 0));
    }

    @Override
    public void stop() {
        controllerRegistry.all().forEach(IController::stop);
        burst.close();
        ticker.clear();
        ticker.close();
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

    public static <T extends IController> T controller(Class<T> c) {
        return instance.controllerRegistry.get(c);
    }

    public static <T extends Action<?>> T action(Class<T> c) {
        return controller(ActionController.class).getActions().get(c);
    }

    public static <T extends Sampler> T sampler(Class<T> c) {
        return controller(SampleController.class).getSamplers().get(c);
    }

    public static <T extends Tweak> T tweak(Class<T> c) {
        return controller(TweakController.class).getTweaks().get(c);
    }

    public static <T extends Feature> T feature(Class<T> c) {
        return controller(FeatureController.class).getFeatures().get(c);
    }

    public static <T extends IController> T controller(String c) {
        return (T)instance.controllerRegistry.get(c);
    }

    public static <T extends Action<?>> T action(String c) {
        return (T)controller(ActionController.class).getActions().get(c);
    }

    public static <T extends Sampler> T sampler(String c) {
        return (T)controller(SampleController.class).getSamplers().get(c);
    }

    public static <T extends Tweak> T tweak(String c) {
        return (T)controller(TweakController.class).getTweaks().get(c);
    }

    public static <T extends Feature> T feature(String c) {
        return (T)controller(FeatureController.class).getFeatures().get(c);
    }
}
