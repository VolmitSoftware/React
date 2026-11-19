package art.arcane.react.platform;

import art.arcane.react.api.web.ReactWebServer;

import java.io.File;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * What React's platform-free code needs from the server it runs on. Optional capabilities refuse through
 * {@link #unavailable(PlatformCapability)} and default methods that return empty or false.
 */
public interface ReactPlatform {
  PlatformKind kind();

  /**
   * The server or loader name, for example Paper, Folia, Fabric or NeoForge.
   */
  String platformName();

  String minecraftVersion();

  File dataFolder();

  PlatformScheduler scheduler();

  ExecutorService asyncExecutor();

  /**
   * What players call React on this platform: "plugin" on Bukkit and proxies, "mod" on mod loaders.
   */
  default String ownerNoun() {
    return kind() == PlatformKind.MODDED ? "mod" : "plugin";
  }

  default String ownerNounTitle() {
    String noun = ownerNoun();
    return noun.isEmpty() ? noun : Character.toUpperCase(noun.charAt(0)) + noun.substring(1);
  }

  void info(String message);

  void warn(String message);

  default void warn(String message, Throwable failure) {
    warn(message);
  }

  void verbose(String message);

  default void verbose(Supplier<String> message) {
    verbose(message.get());
  }

  void reportError(String context, Throwable failure);

  default void reportError(Throwable failure) {
    reportError("Unexpected React failure", failure);
  }

  default Optional<CapabilityRefusal> unavailable(PlatformCapability capability) {
    return Optional.of(CapabilityRefusal.unsupported(capability, platformName()));
  }

  default boolean dispatchConsoleCommand(String command) {
    return false;
  }

  default Optional<ReactPlayer> player(UUID id) {
    return Optional.empty();
  }

  default Optional<ReactPlayer> player(Object handle) {
    return Optional.empty();
  }

  default Optional<ReactWorld> world(String name) {
    return Optional.empty();
  }

  default Optional<ReactWorld> world(Object handle) {
    return Optional.empty();
  }

  // React Web

  /** The running React Web server, for the {@code /react web} commands; empty where none runs. */
  default Optional<ReactWebServer> webServer() {
    return Optional.empty();
  }

  // Sampler hosts

  default Optional<SamplerHost> samplerHost() {
    return Optional.empty();
  }

  default Optional<RegistryHost> registryHost() {
    return Optional.empty();
  }

  default Optional<TickStatsHost> tickStatsHost() {
    return Optional.empty();
  }

  default Optional<WorldStatsHost> worldStatsHost() {
    return Optional.empty();
  }

  default Optional<PlayerStatsHost> playerStatsHost() {
    return Optional.empty();
  }

  default Optional<RuntimeStatsHost> runtimeStatsHost() {
    return Optional.empty();
  }
}
