package art.arcane.react.proxy;

import art.arcane.react.api.web.HistoryWebMetricsSource;
import art.arcane.react.api.web.MetricsSerializer;
import art.arcane.react.api.web.ReactWebServer;
import art.arcane.react.api.web.WebConfigWatcher;
import art.arcane.react.api.web.network.NetworkHub;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.telemetry.RuntimeTelemetry;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.util.director.ReactDirector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * React on Velocity or BungeeCord. Start binds the proxy platform, runs the samplers that need no world (JVM, host,
 * players), starts the network hub and React Web. The entry points forward {@code /react} here.
 */
public final class ProxyRuntime {
  public static final List<String> COMMAND_LABELS = List.of("react", "re");
  public static final String PERMISSION = "react.use";
  private final ProxyHost host;
  private final ReactDirector director = new ReactDirector();
  private volatile Running running;

  private record Running(ProxyReactPlatform platform, ScheduledExecutorService main, ExecutorService async, SampleController samples,
                         RuntimeTelemetry telemetry, NetworkHub network, ReactWebServer web, WebConfigWatcher webConfig) {
  }

  public ProxyRuntime(ProxyHost host) {
    this.host = Objects.requireNonNull(host, "host");
  }

  public void start() {
    if (running != null) {
      throw new IllegalStateException("React is already running");
    }
    File dataFolder = host.dataFolder();
    if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) {
      host.logger().severe("React could not create " + dataFolder + " and did not start");
      return;
    }
    ScheduledExecutorService main = Executors.newSingleThreadScheduledExecutor(threads("React Main"));
    ExecutorService async = Executors.newCachedThreadPool(threads("React Async"));
    List<Runnable> startupTasks = new ArrayList<>();
    SampleController samples = new SampleController();
    RuntimeTelemetry telemetry = new RuntimeTelemetry(dataFolder);
    ProxyLanguageHost languageHost = new ProxyLanguageHost(dataFolder, host.logger());
    HistoryWebMetricsSource metrics = new HistoryWebMetricsSource(() -> samples, telemetry::history, new MetricsSerializer());
    NetworkHub network = new NetworkHub(host);
    ReactWebServer[] web = new ReactWebServer[1];
    ProxyReactPlatform platform = new ProxyReactPlatform(host, new ProxyScheduler(main, async, telemetry.jobs()), async, languageHost,
        startupTasks, () -> samples, telemetry, () -> web[0]);
    ReactPlatforms.bind(platform);
    try {
      languageHost.load();
    } catch (IOException failure) {
      ReactPlatforms.unbind();
      main.shutdownNow();
      async.shutdownNow();
      host.logger().log(Level.SEVERE, "React could not read react.toml and did not start", failure);
      return;
    }
    ProxyWebHost webHost = new ProxyWebHost(host, metrics, network);
    web[0] = new ReactWebServer(webHost);
    WebConfigWatcher webConfig = new WebConfigWatcher(new File(dataFolder, "web.toml"), webHost, web[0]);
    running = new Running(platform, main, async, samples, telemetry, network, web[0], webConfig);
    try {
      ReactLanguage.initialize();
      telemetry.startJobs();
      samples.start();
      for (Runnable task : startupTasks) {
        task.run();
      }
      startupTasks.clear();
      samples.postStart();
      telemetry.start(samples);
      network.start();
      platform.proxyScheduler().markReady();
      web[0].start();
      web[0].postStart();
      webConfig.start();
    } catch (RuntimeException failure) {
      host.logger().log(Level.SEVERE, "React failed to start on " + host.kind(), failure);
      stop();
      return;
    }
    host.logger().info("React started on " + host.kind() + " " + host.version() + " with " + samples.getSamplers().size()
        + " samplers, watching " + host.servers().size() + " servers; configuration in " + dataFolder);
  }

  public void stop() {
    Running active = running;
    running = null;
    if (active == null) {
      return;
    }
    active.platform().proxyScheduler().deactivate();
    active.webConfig().stop();
    try {
      active.web().stop();
    } catch (RuntimeException failure) {
      host.logger().log(Level.WARNING, "React Web did not stop cleanly", failure);
    }
    active.network().stop();
    try {
      active.telemetry().stop(active.samples());
    } catch (RuntimeException failure) {
      host.logger().log(Level.WARNING, "React telemetry did not stop cleanly", failure);
    }
    try {
      active.samples().stop();
    } catch (RuntimeException failure) {
      host.logger().log(Level.WARNING, "Samplers did not stop cleanly", failure);
    }
    ReactLanguage.close();
    active.main().shutdownNow();
    active.async().shutdownNow();
    try {
      active.async().awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
    ReactPlatforms.unbind();
  }

  public boolean isRunning() {
    return running != null;
  }

  /** Runs {@code /<label> <args>} off the proxy's command thread, on React's main thread. */
  public void execute(ReactCommandSender sender, String label, String[] args) {
    Running active = running;
    if (active == null) {
      sender.sendMessage("React is not running on this proxy.");
      return;
    }
    active.main().execute(() -> director.execute(sender, label, args));
  }

  public List<String> complete(ReactCommandSender sender, String label, String[] args) {
    if (running == null) {
      return List.of();
    }
    return director.complete(sender, label, args);
  }

  private static ThreadFactory threads(String name) {
    AtomicInteger sequence = new AtomicInteger();
    return (Runnable task) -> {
      Thread thread = new Thread(task, name + " " + sequence.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
  }
}
