/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react;

import art.arcane.chrono.PrecisionStopwatch;
import art.arcane.multiburst.MultiBurst;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.content.PAPI.PapiExpansion;
import art.arcane.react.core.controller.*;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.config.ConfigMigrationManager;
import art.arcane.react.util.config.ConfigFileSupport;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.react.util.format.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.JarScanner;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.plugin.SplashScreen;
import art.arcane.react.util.plugin.VolmitPlugin;
import art.arcane.react.util.registry.Registry;
import art.arcane.react.util.scheduling.J;
import art.arcane.react.util.scheduling.Ticker;
import art.arcane.react.util.world.EntityKiller;
import lombok.Getter;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


@Getter
public class React extends VolmitPlugin {
    public static BukkitAudiences audiences;
    public static React instance;
    public static Thread serverThread;
    public static Ticker ticker;
    public static MultiBurst burst;
    private static final ThreadLocal<Location> NEARBY_PLAYER_SCRATCH = ThreadLocal.withInitial(() -> new Location(null, 0, 0, 0));
    private List<Runnable> startupTasks;
    private List<Runnable> prejobs;
    private Registry<IController> controllerRegistry;
    private boolean ready;

    public React() {
        instance = this;
        ready = false;
    }

    public static boolean hasNearbyPlayer(Location l, double blocks) {
        if (l == null || l.getWorld() == null || blocks <= 0) {
            return false;
        }

        double radiusSquared = blocks * blocks;
        double lx = l.getX();
        double ly = l.getY();
        double lz = l.getZ();
        Location scratch = NEARBY_PLAYER_SCRATCH.get();
        scratch.setWorld(l.getWorld());

        for (Player player : l.getWorld().getPlayers()) {
            player.getLocation(scratch);
            double dx = scratch.getX() - lx;
            if (dx > blocks || dx < -blocks) {
                continue;
            }

            double dy = scratch.getY() - ly;
            if (dy > blocks || dy < -blocks) {
                continue;
            }

            double dz = scratch.getZ() - lz;
            if (dz > blocks || dz < -blocks) {
                continue;
            }

            if ((dx * dx) + (dy * dy) + (dz * dz) <= radiusSquared) {
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
        if (ReactConfiguration.get().isVerbose()) {
            msg(C.LIGHT_PURPLE + string);
        }
    }

    public static void kill(Entity e) {
        new EntityKiller(e, 8);
    }

    public static void kill(Entity e, int delay) {
        new EntityKiller(e, delay);
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
        return (T) instance.controllerRegistry.get(c);
    }

    public static <T extends Action<?>> T action(String c) {
        return (T) controller(ActionController.class).getActions().get(c);
    }

    public static <T extends Sampler> T sampler(String c) {
        SampleController sampleController = controller(SampleController.class);
        if (sampleController == null || sampleController.getSamplers() == null) {
            return null;
        }
        return (T) sampleController.getSamplers().get(c);
    }

    public static <T extends Tweak> T tweak(String c) {
        return (T) controller(TweakController.class).getTweaks().get(c);
    }

    public static <T extends Feature> T feature(String c) {
        return (T) controller(FeatureController.class).getFeatures().get(c);
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
        ConfigMigrationManager.backupLegacyJsonConfigsOnce();
        startupTasks = new CopyOnWriteArrayList<>();
        prejobs = new CopyOnWriteArrayList<>();
        burst = new MultiBurst("React", Thread.MIN_PRIORITY);
        ticker = new Ticker();
        audiences = BukkitAudiences.create(this);
        controllerRegistry = new Registry<>(IController.class, "art.arcane.react.core.controller");

        for (Runnable i : startupTasks) {
            i.run();
        }

        startupTasks.clear();

        for (IController i : controllerRegistry.all()) {
            i.start();
        }

        for (Runnable i : startupTasks) {
            i.run();
        }

        startupTasks.clear();

        info(SplashScreen.splash);

        for (IController i : controllerRegistry.all()) {
            i.postStart();

            if (i instanceof Listener l) {
                registerListener(l);
            }
        }

        ready = true;

        for (Runnable i : prejobs) {
            controller(JobController.class).queue(i);
        }

        int deletedLegacyJson = ConfigMigrationManager.deleteMigratedLegacyJsonFiles();
        if (deletedLegacyJson > 0) {
            React.info("Deleted " + deletedLegacyJson + " migrated legacy JSON config files.");
        }

        ConfigFileSupport.flushCreatedConfigSummary();
        React.info("React Started in " + Form.duration(psw.getMilliseconds(), 0));
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PapiExpansion().register();
        }
    }

    @Override
    public void stop() {
        ready = false;
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
        try {
            onDisable();
            onEnable();
        } catch (Throwable ignored) { // threads break, i dont care
        }

    }
}
