package art.arcane.react.proxy;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.api.web.ReactWebServer;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.core.telemetry.HostTelemetrySnapshot;
import art.arcane.react.core.telemetry.RuntimeTelemetry;
import art.arcane.react.platform.CapabilityRefusal;
import art.arcane.react.platform.CommandHost;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.LanguageHost;
import art.arcane.react.platform.PlatformCapability;
import art.arcane.react.platform.PlatformKind;
import art.arcane.react.platform.PlatformScheduler;
import art.arcane.react.platform.PlayerStatsHost;
import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.RegistryHost;
import art.arcane.react.platform.RuntimeStatsHost;
import art.arcane.react.platform.SamplerHost;
import com.google.common.util.concurrent.AtomicDouble;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.logging.Level;

/** React's platform on Velocity or BungeeCord: no worlds or ticks, just the proxy's JVM, host and players. */
final class ProxyReactPlatform implements ReactPlatform {
  private final ProxyHost host;
  private final ProxyScheduler scheduler;
  private final ExecutorService async;
  private final LanguageHost languageHost;
  private final RegistryHost registryHost;
  private final SamplerHost samplerHost;
  private final PlayerStatsHost playerStatsHost;
  private final RuntimeStatsHost runtimeStatsHost;
  private final Supplier<ReactWebServer> webServer;

  ProxyReactPlatform(ProxyHost host, ProxyScheduler scheduler, ExecutorService async, LanguageHost languageHost,
                     List<Runnable> startupTasks, Supplier<SampleController> samples, RuntimeTelemetry telemetry,
                     Supplier<ReactWebServer> webServer) {
    this.host = host;
    this.scheduler = scheduler;
    this.async = async;
    this.languageHost = languageHost;
    this.webServer = webServer;
    this.registryHost = new ProxyRegistryHost(host.dataFolder(), startupTasks, this::verbose, this::warn);
    this.samplerHost = new Samplers(samples);
    this.playerStatsHost = new Players(host);
    this.runtimeStatsHost = new Runtime(telemetry);
  }

  ProxyScheduler proxyScheduler() {
    return scheduler;
  }

  @Override
  public PlatformKind kind() {
    return PlatformKind.PROXY;
  }

  @Override
  public String platformName() {
    return host.kind();
  }

  @Override
  public String minecraftVersion() {
    return host.version();
  }

  @Override
  public File dataFolder() {
    return host.dataFolder();
  }

  @Override
  public PlatformScheduler scheduler() {
    return scheduler;
  }

  @Override
  public ExecutorService asyncExecutor() {
    return async;
  }

  @Override
  public void info(String message) {
    host.logger().info(message);
  }

  @Override
  public void warn(String message) {
    host.logger().warning(message);
  }

  @Override
  public void warn(String message, Throwable failure) {
    host.logger().log(Level.WARNING, message, failure);
  }

  @Override
  public void verbose(String message) {
    host.logger().fine(message);
  }

  @Override
  public void reportError(String context, Throwable failure) {
    host.logger().log(Level.SEVERE, context, failure);
  }

  @Override
  public Optional<CapabilityRefusal> unavailable(PlatformCapability capability) {
    return Optional.of(CapabilityRefusal.unsupported(capability, host.kind()));
  }

  @Override
  public boolean dispatchConsoleCommand(String command) {
    return host.dispatchConsoleCommand(command.startsWith("/") ? command.substring(1) : command);
  }

  @Override
  public Optional<ReactWebServer> webServer() {
    return Optional.ofNullable(webServer.get());
  }

  @Override
  public Optional<SamplerHost> samplerHost() {
    return Optional.of(samplerHost);
  }

  @Override
  public Optional<RegistryHost> registryHost() {
    return Optional.of(registryHost);
  }

  @Override
  public Optional<PlayerStatsHost> playerStatsHost() {
    return Optional.of(playerStatsHost);
  }

  @Override
  public Optional<RuntimeStatsHost> runtimeStatsHost() {
    return Optional.of(runtimeStatsHost);
  }

  @Override
  public Optional<ReactCommandSender> commandSender(Object handle) {
    if (handle instanceof ReactCommandSender sender) {
      return Optional.of(sender);
    }
    return host.sender(handle);
  }

  @Override
  public Optional<CommandHost> commandHost() {
    return Optional.of(CommandHost.NONE);
  }

  @Override
  public Optional<LanguageHost> languageHost() {
    return Optional.of(languageHost);
  }

  private record Samplers(Supplier<SampleController> controller) implements SamplerHost {
    @Override
    public Optional<SampleController> sampleController() {
      return Optional.ofNullable(controller.get());
    }

    @Override
    public void attachSampler(Sampler sampler) {
    }

    @Override
    public void detachSampler(Sampler sampler) {
    }

    @Override
    public void samplerRegistered(Sampler sampler) {
    }

    @Override
    public void samplerUnregistered(String samplerId) {
    }

    @Override
    public void refreshMonitors() {
    }

    @Override
    public AtomicDouble chunkCounter(Object chunkOrBlock, Sampler sampler) {
      return new AtomicDouble();
    }

    @Override
    public Optional<Double> sampleChunk(Object chunk, Sampler sampler) {
      return Optional.empty();
    }
  }

  private record Players(ProxyHost host) implements PlayerStatsHost {
    @Override
    public int onlinePlayerCount() {
      return host.onlinePlayers();
    }

    @Override
    public Map<UUID, Integer> playerPings() {
      return host.playerPings();
    }
  }

  private record Runtime(RuntimeTelemetry telemetry) implements RuntimeStatsHost {
    @Override
    public double reactAsyncTickTimeMs() {
      return 0D;
    }

    @Override
    public double reactSyncTickTimeMs() {
      return telemetry.jobs().getUsage().getAverage();
    }

    @Override
    public double jobOverBudgetMs() {
      return telemetry.jobs().getOverBudget();
    }

    @Override
    public double jobQueuedComputeTimeMs() {
      return telemetry.jobs().getQueuedComputeTime();
    }

    @Override
    public int jobQueueSize() {
      return telemetry.jobs().getQueueSize();
    }

    @Override
    public HostTelemetrySnapshot hostTelemetry() {
      return telemetry.host().snapshot();
    }

    @Override
    public Optional<EventMeasurement> eventMeasurement() {
      return Optional.empty();
    }

    @Override
    public Optional<CostShares> costShares() {
      return Optional.empty();
    }

    @Override
    public OptionalDouble readAndResetFeatureCounter(FeatureCounter counter) {
      return OptionalDouble.empty();
    }
  }
}
