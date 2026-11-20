package art.arcane.react.platform;

import art.arcane.react.api.benchmark.BenchmarkTarget;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Runs the React subcommands whose work lives in a platform module. The command tree, syntax, help and completion
 * are platform-free; each leaf calls the matching method here. A platform that lacks a subcommand keeps the default,
 * which answers with the localized "unavailable" message, so the command still exists everywhere.
 * Every method runs on the thread the command arrived on.
 */
public interface CommandHost {
  CommandHost NONE = new CommandHost() {
  };

  static void unavailable(VolmitSender sender, String command) {
    ReactPlatform platform = ReactPlatforms.getOrNull();
    ReactLanguage.sendPrefixed(sender, CommandMessages.COMMAND_UNAVAILABLE,
        MessageArgument.untrusted("command", "/react " + command),
        MessageArgument.untrusted("platform", platform == null ? "this server" : platform.platformName()));
  }

  enum DistanceKind {
    VIEW,
    SIMULATION,
    SEND
  }

  default List<CommandTargets.Player> players() {
    return List.of();
  }

  default List<CommandTargets.World> worlds() {
    return List.of();
  }

  default Optional<CommandTargets.World> worldOf(ReactPlayer player) {
    return Optional.empty();
  }

  default List<String> rendererIds() {
    return List.of();
  }

  /**
   * Handles {@code /react language ...} before the command tree sees it. Returns false to let the tree answer.
   */
  default boolean language(ReactCommandSender sender, String[] arguments) {
    return false;
  }

  default Optional<List<String>> completeLanguage(ReactCommandSender sender, String[] arguments) {
    return Optional.empty();
  }

  default void openLanguage(VolmitSender sender) {
    unavailable(sender, "language");
  }

  default void monitor(VolmitSender sender) {
    unavailable(sender, "monitor");
  }

  default void map(VolmitSender sender, String rendererId) {
    unavailable(sender, "map");
  }

  default void monitoringOnly(VolmitSender sender) {
    unavailable(sender, "monitoring-only");
  }

  default void benchmark(VolmitSender sender, Set<BenchmarkTarget> targets) {
    unavailable(sender, "benchmark");
  }

  default void bridgeStatus(VolmitSender sender) {
    unavailable(sender, "bridge status");
  }

  default void chunkSample(VolmitSender sender) {
    unavailable(sender, "chunk sample");
  }

  default void chunkWorst(VolmitSender sender) {
    unavailable(sender, "chunk worst");
  }

  default void configGui(VolmitSender sender) {
    unavailable(sender, "config gui");
  }

  default void configMonitor(VolmitSender sender) {
    unavailable(sender, "config monitor");
  }

  default void debugDump(VolmitSender sender, boolean upload) {
    unavailable(sender, "debug dump");
  }

  default void debugEntityData(VolmitSender sender) {
    unavailable(sender, "debug entity-data");
  }

  default void devTestAll(VolmitSender sender, int radius) {
    unavailable(sender, "dev test-all");
  }

  default void devVerify(VolmitSender sender) {
    unavailable(sender, "dev verify");
  }

  default void serverDistance(VolmitSender sender, DistanceKind kind, int distance) {
    unavailable(sender, "distance server");
  }

  default void worldDistance(VolmitSender sender, CommandTargets.World world, DistanceKind kind, int distance) {
    unavailable(sender, "distance world");
  }

  default void playerDistance(VolmitSender sender, CommandTargets.Player player, DistanceKind kind, int distance) {
    unavailable(sender, "distance player");
  }

  default void environmentInfo(VolmitSender sender) {
    unavailable(sender, "environment info");
  }

  default void integrationStatus(VolmitSender sender) {
    unavailable(sender, "integration status");
  }

  default void pluginApiStatus(VolmitSender sender) {
    unavailable(sender, "plugin-api status");
  }

  default void pluginApiReload(VolmitSender sender) {
    unavailable(sender, "plugin-api reload");
  }

  default void testRun(VolmitSender sender, boolean full, boolean json) {
    unavailable(sender, "test run");
  }

  default void testLoad(VolmitSender sender, boolean confirm, int players, int durationSeconds) {
    unavailable(sender, "test loadtest");
  }
}
