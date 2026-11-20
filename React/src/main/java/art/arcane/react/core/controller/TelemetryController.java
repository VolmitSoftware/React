package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.metric.internal.PublishedMetricStore;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.integration.ThirdPartyMetricRegistry;
import art.arcane.react.content.sampler.GCStatsTracker;
import art.arcane.react.core.telemetry.HostTelemetryFeed;
import art.arcane.react.core.telemetry.HostTelemetrySnapshot;
import art.arcane.react.core.telemetry.SelfTelemetry;
import art.arcane.react.util.plugin.IController;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class TelemetryController implements IController, Listener {
  private static final long REFRESH_INTERVAL_MS = 1_000L;

  private final transient SelfTelemetry self;
  private final transient List<String> registeredSamplerIds;
  private transient ScheduledExecutorService refreshExecutor;
  private transient HostTelemetryFeed hostFeed;

  public TelemetryController() {
    self = new SelfTelemetry(new SelfTelemetry.Sources() {
      @Override
      public HistoryController history() {
        return React.controller(HistoryController.class);
      }

      @Override
      public boolean webAvailable() {
        return React.controller(WebController.class) != null;
      }

      @Override
      public int activeWebSocketSessions() {
        WebController controller = React.controller(WebController.class);
        return controller == null ? 0 : controller.activeWebSocketSessions();
      }

      @Override
      public PublishedMetricStore publishedStore() {
        IntegrationController controller = React.controller(IntegrationController.class);
        ThirdPartyMetricRegistry registry = controller == null ? null : controller.getThirdPartyMetrics();
        return registry == null ? null : registry.store();
      }
    });
    registeredSamplerIds = new ArrayList<>();
  }

  public static HostTelemetrySnapshot currentHostSnapshot() {
    TelemetryController controller = React.instance == null ? null : React.controller(TelemetryController.class);
    return controller == null ? HostTelemetrySnapshot.empty() : controller.hostSnapshot();
  }

  @Override
  public String getId() {
    return "telemetry";
  }

  @Override
  public String getName() {
    return "Telemetry";
  }

  @Override
  public void start() {
    self.start();
    registeredSamplerIds.clear();
    hostFeed = null;
  }

  @Override
  public void postStart() {
    long nowMs = System.currentTimeMillis();
    for (Player player : Bukkit.getOnlinePlayers()) {
      self.playerActive(player.getUniqueId(), nowMs);
    }
    GCStatsTracker.acquire();
    HostTelemetryFeed feed = HostTelemetryFeed.forDataFolder(React.instance.getDataFolder().toPath());
    hostFeed = feed;
    registerSamplers();
    refreshExecutor = Executors.newSingleThreadScheduledExecutor(daemonFactory());
    refreshExecutor.scheduleWithFixedDelay(
        () -> refresh(feed),
        0L,
        REFRESH_INTERVAL_MS,
        TimeUnit.MILLISECONDS
    );
  }

  @Override
  public void stop() {
    ScheduledExecutorService executor = refreshExecutor;
    refreshExecutor = null;
    if (executor != null) {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
          React.warn("Timed out waiting for React telemetry refresh to stop.");
        }
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        React.warn("Interrupted while waiting for React telemetry refresh to stop.", failure);
      }
    }
    SampleController sampleController = React.controller(SampleController.class);
    self.stop(sampleController);
    if (sampleController != null) {
      for (String id : List.copyOf(registeredSamplerIds)) {
        sampleController.unregisterSampler(id);
      }
    }
    registeredSamplerIds.clear();
    hostFeed = null;
    GCStatsTracker.release();
  }

  public HostTelemetrySnapshot hostSnapshot() {
    HostTelemetryFeed feed = hostFeed;
    return feed == null ? HostTelemetrySnapshot.empty() : feed.snapshot();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerJoin(PlayerJoinEvent event) {
    self.playerJoined(event.getPlayer().getUniqueId(), System.currentTimeMillis());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerQuit(PlayerQuitEvent event) {
    self.playerQuit(event.getPlayer().getUniqueId(), System.currentTimeMillis());
  }

  private void refresh(HostTelemetryFeed feed) {
    long nowMs = System.currentTimeMillis();
    feed.refresh(nowMs);
    self.refresh(nowMs);
  }

  private void registerSamplers() {
    SampleController sampleController = React.controller(SampleController.class);
    if (sampleController == null) {
      return;
    }
    self.registerReactSamplers(sampleController);
    registerHostSamplers(sampleController);
    self.registerPlayerSamplers(sampleController);
  }

  private void registerHostSamplers(SampleController sampleController) {
    HostTelemetryFeed feed = hostFeed;
    if (feed == null) {
      return;
    }
    for (Sampler sampler : feed.samplers()) {
      if (sampleController.registerSampler(sampler)) {
        registeredSamplerIds.add(sampler.getId());
      }
    }
  }

  private ThreadFactory daemonFactory() {
    return runnable -> {
      Thread thread = new Thread(runnable, "react-host-telemetry");
      thread.setDaemon(true);
      return thread;
    };
  }
}
