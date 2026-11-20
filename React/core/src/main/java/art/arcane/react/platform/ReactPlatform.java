package art.arcane.react.platform;

import art.arcane.react.api.web.ReactWebServer;
import art.arcane.react.platform.cost.OwnerCostProbe;

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

  // Features, tweaks and actions

  default Optional<ComponentHost> componentHost() {
    return Optional.empty();
  }

  default Optional<WorldHost> worldHost() {
    return Optional.empty();
  }

  default Optional<EntityHost> entityHost() {
    return Optional.empty();
  }

  default Optional<HopperHost> hopperHost() {
    return Optional.empty();
  }

  default Optional<ViewDistanceHost> viewDistanceHost() {
    return Optional.empty();
  }

  // Chunks, players, world rules and maps

  default Optional<ChunkHost> chunkHost() {
    return Optional.empty();
  }

  default Optional<PlayerHost> playerHost() {
    return Optional.empty();
  }

  default Optional<IncidentHost> incidentHost() {
    return Optional.empty();
  }

  default Optional<GameRuleHost> gameRuleHost() {
    return Optional.empty();
  }

  default Optional<DropHost> dropHost() {
    return Optional.empty();
  }

  default Optional<ShorthandHost> shorthandHost() {
    return Optional.empty();
  }

  default Optional<IntegrationMetricsHost> integrationMetricsHost() {
    return Optional.empty();
  }

  default Optional<ChunkHeatmapHost> chunkHeatmapHost() {
    return Optional.empty();
  }

  default Optional<MapAnchorHost> mapAnchorHost() {
    return Optional.empty();
  }

  default Optional<MapDataHost> mapDataHost() {
    return Optional.empty();
  }

  // Stats

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

  // Commands, messaging and localization

  /**
   * Wraps a platform command sender, such as a console or player object, for React's commands and messages.
   */
  default Optional<ReactCommandSender> commandSender(Object handle) {
    return handle instanceof ReactCommandSender sender ? Optional.of(sender) : Optional.empty();
  }

  default Optional<CommandHost> commandHost() {
    return Optional.empty();
  }

  default Optional<LanguageHost> languageHost() {
    return Optional.empty();
  }

  // Owner cost
  default Optional<OwnerCostProbe> ownerCostProbe() {
    return Optional.empty();
  }
}
