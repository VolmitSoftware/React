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
import art.arcane.react.content.PAPI.ReactPlaceholders;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.JobController;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.core.controller.PdcWriteBatcher;
import art.arcane.react.core.controller.PlayerController;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.controller.TweakController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.common.plugin.SplashScreen;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.react.util.format.C;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.core.NMS;
import art.arcane.react.core.bridge.NmsBridgeRegistry;
import art.arcane.react.util.plugin.VolmitPlugin;
import art.arcane.react.util.project.config.ConfigFileSupport;
import art.arcane.react.util.project.registry.Registry;
import art.arcane.react.util.project.world.EntityKiller;
import art.arcane.volmlib.integration.ReloadAware;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.hud.HudActionBar;
import art.arcane.volmlib.util.hud.HudTitleService;
import art.arcane.volmlib.util.io.JarScanner;
import art.arcane.volmlib.util.plugin.ComponentLog;
import io.github.slimjar.app.builder.SpigotApplicationBuilder;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;


@Getter
public class React extends VolmitPlugin implements ReloadAware {
  private static final Logger FALLBACK_LOGGER = Logger.getLogger("React");
  private final AtomicBoolean alreadyDrained = new AtomicBoolean(false);
  private static final boolean SLIMJAR_DEBUG = Boolean.getBoolean("react.debug-slimjar");
  public static React instance;
  public static Thread serverThread;
  public static Ticker ticker;
  public static MultiBurst burst;
  // Lazy: Audiences links against slimjar-provided adventure types, so it must not be
  // loaded from <clinit> — the main class initializes before ApplicationBuilder.build().
  private static volatile Audiences audiencesFacade;
  private static volatile BukkitAudiences audienceProvider;
  private static HudActionBar hudBar;
  private static HudTitleService hudTitles;
  private static final int REPORTED_ERROR_HISTORY = 1024;
  // bstats.org plugin id
  private static final int BSTATS_PLUGIN_ID = 24219;
  private static final java.util.concurrent.atomic.AtomicInteger reportedErrorCounter = new java.util.concurrent.atomic.AtomicInteger();
  private static final java.util.ArrayDeque<Long> reportedErrorTimestamps = new java.util.ArrayDeque<Long>();
  private List<Runnable> startupTasks;
  private List<Runnable> prejobs;
  private Registry<IController> controllerRegistry;
  private NmsBridgeRegistry bridgeRegistry;
  private volatile boolean shutdownDrained;
  private volatile ReactPlaceholders papiExpansion;
  // ReactMetrics owns all bstats types; never reference them from this class (slimjar link trap)
  private ReactMetrics metrics;
  private volatile boolean monitoringOnly;
  private boolean ready;

  public React() {
    instance = this;
    shutdownDrained = true;
    ready = false;
    new SpigotApplicationBuilder(this)
        .debug(SLIMJAR_DEBUG)
        .build();
  }

  public static boolean hasNearbyPlayer(Location l, double blocks) {
    if (l == null || l.getWorld() == null || blocks <= 0) {
      return false;
    }

    NearbyPlayerIndexController controller = controller(NearbyPlayerIndexController.class);
    if (controller != null) {
      return controller.hasNearbyPlayer(l, blocks);
    }

    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(l)) {
      return false;
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
    log(Level.WARNING, C.YELLOW + string, null);
  }

  public static void warn(String string, Throwable failure) {
    log(Level.WARNING, C.YELLOW + string, failure);
  }

  public static void error(String string) {
    log(Level.SEVERE, C.RED + string, null);
  }

  public static void error(String string, Throwable failure) {
    log(Level.SEVERE, C.RED + string, failure);
  }

  public static void verbose(String string) {
    if (isVerboseEnabled()) {
      log(Level.INFO, C.LIGHT_PURPLE + string, null);
    }
  }

  public static void verbose(String string, Throwable failure) {
    if (isVerboseEnabled()) {
      log(Level.INFO, C.LIGHT_PURPLE + string, failure);
    }
  }

  public static void verbose(Supplier<String> messageSupplier) {
    if (isVerboseEnabled()) {
      log(Level.INFO, C.LIGHT_PURPLE + messageSupplier.get(), null);
    }
  }

  public static void verbose(Supplier<String> messageSupplier, Throwable failure) {
    if (isVerboseEnabled()) {
      log(Level.INFO, C.LIGHT_PURPLE + messageSupplier.get(), failure);
    }
  }

  public static void kill(Entity e) {
    new EntityKiller(e, 8);
  }

  public static void kill(Entity e, int delay) {
    new EntityKiller(e, delay);
  }

  public static void msg(String string) {
    log(Level.INFO, string, null);
  }

  public static void success(String string) {
    log(Level.INFO, C.GREEN + string, null);
  }

  public static void info(String string) {
    log(Level.INFO, C.WHITE + string, null);
  }

  public static void debug(String string) {
    if (isDebugEnabled()) {
      log(Level.INFO, C.DARK_PURPLE + string, null);
    }
  }

  public static void debug(Supplier<String> messageSupplier) {
    if (isDebugEnabled()) {
      log(Level.INFO, C.DARK_PURPLE + messageSupplier.get(), null);
    }
  }

  public static void reportError(Throwable e) {
    reportError("Unexpected React failure", e);
  }

  public static void reportError(String context, Throwable failure) {
    reportedErrorCounter.incrementAndGet();
    long now = System.currentTimeMillis();
    synchronized (reportedErrorTimestamps) {
      reportedErrorTimestamps.addLast(now);
      while (reportedErrorTimestamps.size() > REPORTED_ERROR_HISTORY) {
        reportedErrorTimestamps.removeFirst();
      }
    }
    error(context, failure);
  }

  private static void log(Level level, String message, Throwable failure) {
    ComponentLog.logLegacy(instance, FALLBACK_LOGGER, "[React] ", level, message, failure);
  }

  private static boolean isVerboseEnabled() {
    try {
      return instance != null && ReactConfiguration.get().isVerbose();
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static boolean isDebugEnabled() {
    try {
      return instance != null && ReactConfiguration.get().isDebug();
    } catch (Throwable ignored) {
      return false;
    }
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

  public static HudActionBar hud() {
    return hudBar;
  }

  public static Audiences audiences() {
    Audiences facade = audiencesFacade;
    if (facade == null) {
      synchronized (React.class) {
        facade = audiencesFacade;
        if (facade == null) {
          facade = new Audiences();
          audiencesFacade = facade;
        }
      }
    }
    return facade;
  }

  static void closeAudienceProvider() {
    BukkitAudiences provider;
    synchronized (Audiences.class) {
      provider = audienceProvider;
      audienceProvider = null;
    }

    if (provider == null) {
      return;
    }

    try {
      provider.close();
    } catch (Throwable ex) {
      reportError(ex);
    }
  }

  public static final class Audiences {
    private Audiences() {
    }

    public Audience player(Player player) {
      return player instanceof Audience audience ? audience : provider().player(player);
    }

    public Audience sender(CommandSender sender) {
      if (sender instanceof Audience audience) {
        return audience;
      }
      return provider().sender(sender);
    }

    private static BukkitAudiences provider() {
      BukkitAudiences provider = audienceProvider;
      if (provider == null) {
        synchronized (Audiences.class) {
          provider = audienceProvider;
          if (provider == null) {
            provider = BukkitAudiences.create(instance);
            audienceProvider = provider;
          }
        }
      }
      return provider;
    }
  }

  public static HudTitleService titles() {
    return hudTitles;
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
    closeAudienceProvider();
    alreadyDrained.set(false);
    shutdownDrained = true;
    PrecisionStopwatch psw = PrecisionStopwatch.start();
    ReactConfiguration.get();
    ReactLanguage.initialize();
    startupTasks = new CopyOnWriteArrayList<>();
    prejobs = new CopyOnWriteArrayList<>();
    burst = new MultiBurst("React", Thread.MIN_PRIORITY);
    ticker = new Ticker();
    hudBar = new HudActionBar(this);
    hudTitles = new HudTitleService(this);
    bridgeRegistry = new NmsBridgeRegistry();
    bridgeRegistry.setMappingsLoader(new art.arcane.react.core.bridge.MappingsLoader());
    NMS.reset();
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
    bridgeRegistry.warnUnavailable(React::warn);

    for (Runnable i : prejobs) {
      controller(JobController.class).queue(i);
    }

    ConfigFileSupport.flushCreatedConfigSummary();
    React.info("React Started in " + Form.duration(psw.getMilliseconds(), 0));
    registerPapiExpansion();
    setupMetrics();
  }

  private void setupMetrics() {
    if (BSTATS_PLUGIN_ID <= 0 || !ReactConfiguration.get().isMetrics()) {
      return;
    }

    metrics = ReactMetrics.start(this, BSTATS_PLUGIN_ID);
  }

  private void registerPapiExpansion() {
    if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
      return;
    }

    ReactPlaceholders placeholders = new ReactPlaceholders(getLogger());

    if (placeholders.start()) {
      papiExpansion = placeholders;
    }
  }

  private void unregisterPapiExpansion() {
    ReactPlaceholders placeholders = papiExpansion;

    if (placeholders == null) {
      return;
    }

    papiExpansion = null;
    placeholders.stop();
  }

  @Override
  public void stop() {
    if (!alreadyDrained.compareAndSet(false, true)) {
      return;
    }
    boolean drained = true;
    ready = false;
    unregisterPapiExpansion();
    closeAudienceProvider();
    if (metrics != null) {
      metrics.shutdown();
      metrics = null;
    }
    if (ticker != null) {
      drained = ticker.close();
    }
    if (controllerRegistry != null) {
      Set<IController> stopped = Collections.newSetFromMap(new IdentityHashMap<>());
      drained &= stopController(FeatureController.class, stopped);
      drained &= stopController(TweakController.class, stopped);
      drained &= stopController(ActionController.class, stopped);
      drained &= stopController(EntityController.class, stopped);
      drained &= stopController(PdcWriteBatcher.class, stopped);
      drained &= stopController(PlayerController.class, stopped);
      for (IController controller : controllerRegistry.all()) {
        if (controller == null || !stopped.add(controller)) {
          continue;
        }

        try {
          controller.stop();
        } catch (Throwable throwable) {
          drained = false;
          React.reportError(throwable);
        }
      }
    }
    shutdownDrained = drained;
    if (bridgeRegistry != null) {
      bridgeRegistry.clear();
      bridgeRegistry = null;
    }
    if (hudBar != null) {
      hudBar.shutdown();
      hudBar = null;
    }
    if (hudTitles != null) {
      hudTitles.shutdown();
      hudTitles = null;
    }
    if (burst != null) {
      burst.close();
    }
    closeAudienceProvider();
  }

  private boolean stopController(Class<? extends IController> type, Set<IController> stopped) {
    IController controller = controllerRegistry.get(type);
    if (controller == null || !stopped.add(controller)) {
      return true;
    }

    try {
      controller.stop();
      return true;
    } catch (Throwable throwable) {
      React.reportError(throwable);
      return false;
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPluginDisable(PluginDisableEvent event) {
    if (event.getPlugin() == this) {
      stop();
    }
  }

  @Override
  public void onPreUnload(ReloadAware.PreUnloadReason reason) {
    React.verbose("BileTools pre-unload hook fired (" + reason + "). Shutting down React controllers.");
    stop();
  }

  @Override
  public String getTag(String subTag) {
    return C.BOLD + "" + C.DARK_GRAY + "[" + C.BOLD + "" + C.AQUA + "React" + C.BOLD + C.DARK_GRAY + "]" + C.RESET + "" + C.GRAY + ": ";
  }

  public Ticker getTicker() {
    return ticker;
  }

  public void setMonitoringOnly(boolean monitoringOnly) {
    this.monitoringOnly = monitoringOnly;

    FeatureController featureController = controller(FeatureController.class);
    if (featureController != null) {
      featureController.reconcileRuntimeMode();
    }

    TweakController tweakController = controller(TweakController.class);
    if (tweakController != null) {
      tweakController.reconcileRuntimeMode();
    }
  }

  public boolean reload() {
    try {
      onDisable();
      if (!shutdownDrained) {
        error("React reload stopped safely because the previous runtime did not finish shutting down. Restart the server before enabling React again.");
        return false;
      }
      onEnable();
      return true;
    } catch (Throwable ex) {
      error("React reload failed: " + ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
      reportError(ex);
      return false;
    }

  }
}
