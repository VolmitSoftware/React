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
import art.arcane.react.util.common.plugin.SplashScreen;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.react.util.format.C;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.core.bridge.NmsBridgeRegistry;
import art.arcane.react.util.plugin.VolmitPlugin;
import art.arcane.react.util.project.config.ConfigFileSupport;
import art.arcane.react.util.project.config.ConfigMigrationManager;
import art.arcane.react.util.project.registry.Registry;
import art.arcane.react.util.project.world.EntityKiller;
import art.arcane.volmlib.integration.ReloadAware;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.JarScanner;
import io.github.slimjar.app.builder.SpigotApplicationBuilder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;


@Getter
public class React extends VolmitPlugin implements ReloadAware {
  private final AtomicBoolean alreadyDrained = new AtomicBoolean(false);
  private static final boolean SLIMJAR_DEBUG = Boolean.getBoolean("react.debug-slimjar");
  public static React instance;
  public static Thread serverThread;
  public static Ticker ticker;
  public static MultiBurst burst;
  private static final int REPORTED_ERROR_HISTORY = 1024;
  private static final java.util.concurrent.atomic.AtomicInteger reportedErrorCounter = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.ArrayDeque<Long> reportedErrorTimestamps = new java.util.ArrayDeque<Long>();
  private List<Runnable> startupTasks;
  private List<Runnable> prejobs;
  private Registry<IController> controllerRegistry;
  private NmsBridgeRegistry bridgeRegistry;
  private boolean ready;

  public React() {
    instance = this;
    ready = false;
    long libraryLoadStart = System.currentTimeMillis();
    getLogger().info("Loading libraries...");
    new SpigotApplicationBuilder(this)
        .debug(SLIMJAR_DEBUG)
        .build();
    long libraryLoadElapsed = System.currentTimeMillis() - libraryLoadStart;
    getLogger().info("Libraries loaded. (" + libraryLoadElapsed + "ms)");
  }

  public static boolean hasNearbyPlayer(Location l, double blocks) {
    if (l == null || l.getWorld() == null || blocks <= 0) {
      return false;
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(l)) {
      return false;
    }

    NearbyPlayerIndexController controller = controller(NearbyPlayerIndexController.class);
    if (controller != null) {
      return controller.hasNearbyPlayer(l, blocks);
    }

    double radiusSquared = blocks * blocks;
    double lx = l.getX();
    double ly = l.getY();
    double lz = l.getZ();

    for (Player player : l.getWorld().getPlayers()) {
      Location playerLocation = player.getLocation();
      double dx = playerLocation.getX() - lx;
      if (dx > blocks || dx < -blocks) {
        continue;
      }

      double dy = playerLocation.getY() - ly;
      if (dy > blocks || dy < -blocks) {
        continue;
      }

      double dz = playerLocation.getZ() - lz;
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
    reportedErrorCounter.incrementAndGet();
    long now = System.currentTimeMillis();
    synchronized (reportedErrorTimestamps) {
      reportedErrorTimestamps.addLast(now);
      while (reportedErrorTimestamps.size() > REPORTED_ERROR_HISTORY) {
        reportedErrorTimestamps.removeFirst();
      }
    }
    e.printStackTrace();
  }

  public static int reportedErrorCount() {
    return reportedErrorCounter.get();
  }

  public static int reportedErrorsSince(long sinceMillis) {
    int total = 0;
    synchronized (reportedErrorTimestamps) {
      for (Long timestamp : reportedErrorTimestamps) {
        if (timestamp != null && timestamp >= sinceMillis) {
          total++;
        }
      }
    }
    return total;
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
        } catch (Throwable ex) {
          verbose("Skipping initialization for " + i.getName() + ": " + ex.getClass().getSimpleName()
              + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
        }
      }
    }

    return v;
  }

  public static NmsBridgeRegistry bridgeRegistry() {
    return instance.bridgeRegistry;
  }

  public static <T extends IController> T controller(Class<T> c) {
    Registry<IController> reg = instance.controllerRegistry;
    return reg == null ? null : reg.get(c);
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
    Registry<IController> reg = instance.controllerRegistry;
    return reg == null ? null : (T) reg.get(c);
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
    getDataFolder().mkdirs();
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
    bridgeRegistry = new NmsBridgeRegistry();
    bridgeRegistry.setMappingsLoader(new art.arcane.react.core.bridge.MappingsLoader());
    if (ReactConfiguration.get().isUnsafeBytecode()) {
      art.arcane.react.core.bridge.BytecodeAgent.install();
      if (art.arcane.react.core.bridge.BytecodeAgent.isInstalled()) {
        info("Bytecode agent attached.");
      }
    }
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
    bridgeRegistry.warnUnavailable(msg -> getLogger().warning(msg));

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
    if (!alreadyDrained.compareAndSet(false, true)) {
      return;
    }
    ready = false;
    if (bridgeRegistry != null) {
      bridgeRegistry.clear();
      bridgeRegistry = null;
    }
    if (ticker != null) {
      ticker.close();
    }
    if (controllerRegistry != null) {
      for (IController controller : controllerRegistry.all()) {
        if (controller == null) {
          continue;
        }

        try {
          controller.stop();
        } catch (Throwable throwable) {
          React.reportError(throwable);
        }
      }
    }
    if (burst != null) {
      burst.close();
    }
  }

  @Override
  public void onPreUnload(ReloadAware.PreUnloadReason reason) {
    React.info("BileTools pre-unload hook fired (" + reason + "). Shutting down React controllers.");
    stop();
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
    } catch (Throwable ex) {
      error("React reload failed: " + ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      reportError(ex);
    }

  }
}
