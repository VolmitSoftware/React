package com.volmit.react;

import art.arcane.multiburst.MultiBurst;
import art.arcane.spatial.matter.SpatialMatter;
import com.volmit.react.content.feature.FeatureBlockVisualization;
import com.volmit.react.core.controller.*;
import com.volmit.react.core.nms.GlowingEntities;
import com.volmit.react.model.VisualizerType;
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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.lang.annotation.Annotation;


@Getter
public class React extends VolmitPlugin {
    public static BukkitAudiences audiences;
    public static React instance;
    public static Thread serverThread;
    public static Ticker ticker;
    public static Ticker monitorTicker;
    public static MultiBurst burst;
    private GlowingEntities glowingEntities;
    private SampleController sampleController;
    private PlayerController playerController;
    private FeatureController featureController;
    private TweakController tweakController;
    private EventController eventController;
    private ActionController actionController;
    private CommandController commandController;
    private ObserverController observerController;
    private EntityController entityController;
    private JobController jobController;

    public React() {
        instance = this;
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
        burst = new MultiBurst("React", Thread.MIN_PRIORITY);
        ticker = new Ticker();
        monitorTicker = new Ticker();
        glowingEntities = new GlowingEntities(this);
        audiences = BukkitAudiences.create(this);
        jobController = new JobController();
        commandController = new CommandController();
        eventController = new EventController();
        playerController = new PlayerController();
        sampleController = new SampleController();
        actionController = new ActionController();
        featureController = new FeatureController();
        tweakController = new TweakController();
        observerController = new ObserverController();
        entityController = new EntityController();
        sampleController.postStart();
        tweakController.postStart();
        featureController.postStart();
        actionController.postStart();

        info(SplashScreen.splash);

//        Edict.builder()
//            .parserPackage("com.volmit.react.testedict.parser")
//            .contextResolverPackage("com.volmit.react.testedict.context")
//            .build()
//            .registerPackage("com.volmit.react.testedict.command")
//            .initialize(getCommand("react"));
    }

    @Override
    public void stop() {
        burst.close();
        ticker.clear();
        monitorTicker.clear();
        ticker.close();
        monitorTicker.close();
        eventController.stop();
        playerController.stop();
        sampleController.stop();
        actionController.stop();
        commandController.stop();
        featureController.stop();
        entityController.stop();
        tweakController.stop();
        jobController.stop();
        glowingEntities.disable();
    }

    public void visualize(Block block, VisualizerType type) {
        FeatureBlockVisualization v = (FeatureBlockVisualization) getFeatureController().getFeature(FeatureBlockVisualization.ID);

        if(v.isEnabled()) {
            v.log(block, type);
        }
    }

    @Override
    public String getTag(String subTag) {
        return C.BOLD + "" + C.DARK_GRAY + "[" + C.BOLD + "" + C.AQUA + "React" + C.BOLD + C.DARK_GRAY + "]" + C.RESET + "" + C.GRAY + ": ";
    }

    public Ticker getTicker() {
        return ticker;
    }

    public Ticker getMonitorTicker() {
        return monitorTicker;
    }

    public void reload() {
        onDisable();
        onEnable();
    }
}
