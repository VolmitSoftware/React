package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.api.benchmark.BenchmarkRunner;
import art.arcane.react.api.benchmark.BenchmarkTarget;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.core.pluginapi.PluginApiCommands;
import art.arcane.react.core.pluginapi.PluginApiPacks;
import art.arcane.react.platform.CommandHost;
import art.arcane.react.platform.CommandTargets;
import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.bukkit.BukkitCommandSender;
import art.arcane.react.platform.bukkit.BukkitLanguage;
import art.arcane.react.platform.bukkit.BukkitReactPlayer;
import art.arcane.react.platform.bukkit.BukkitReactWorld;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class BukkitCommandHost implements CommandHost {
  private final BukkitCommandReact root = new BukkitCommandReact();
  private final BukkitCommandAction action = new BukkitCommandAction();
  private final BukkitCommandBridge bridge = new BukkitCommandBridge();
  private final BukkitCommandChunk chunk = new BukkitCommandChunk();
  private final BukkitCommandConfig config = new BukkitCommandConfig();
  private final BukkitCommandDebug debug = new BukkitCommandDebug();
  private final BukkitCommandDev dev = new BukkitCommandDev();
  private final BukkitCommandEnvironment environment = new BukkitCommandEnvironment();
  private final BukkitCommandIntegration integration = new BukkitCommandIntegration();
  private final BukkitCommandTest test = new BukkitCommandTest();

  public static CommandTargets.World target(World world) {
    return new CommandTargets.World(new BukkitReactWorld(world), WorldIdentity.serialize(world));
  }

  @Override
  public List<CommandTargets.Player> players() {
    List<CommandTargets.Player> players = new ArrayList<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      players.add(new CommandTargets.Player(new BukkitReactPlayer(player)));
    }
    return players;
  }

  @Override
  public List<CommandTargets.World> worlds() {
    List<CommandTargets.World> worlds = new ArrayList<>();
    for (World world : Bukkit.getWorlds()) {
      worlds.add(target(world));
    }
    return worlds;
  }

  @Override
  public Optional<CommandTargets.World> worldOf(ReactPlayer player) {
    return player.handle() instanceof Player bukkitPlayer ? Optional.of(target(bukkitPlayer.getWorld())) : Optional.empty();
  }

  @Override
  public List<String> rendererIds() {
    List<String> ids = new ArrayList<>();
    MapController controller = React.controller(MapController.class);
    if (controller == null || controller.getRenderers() == null) {
      return ids;
    }
    for (ReactRenderer renderer : controller.getRenderers().values()) {
      ids.add(renderer.getId());
    }
    return ids;
  }

  @Override
  public boolean language(ReactCommandSender sender, String[] arguments) {
    BukkitLanguageSwitcher switcher = BukkitLanguage.switcher();
    switcher.command(BukkitCommandSender.bukkit(sender), arguments);
    return true;
  }

  @Override
  public Optional<List<String>> completeLanguage(ReactCommandSender sender, String[] arguments) {
    return Optional.of(BukkitLanguage.switcher().complete(BukkitCommandSender.bukkit(sender), arguments));
  }

  @Override
  public void openLanguage(VolmitSender sender) {
    BukkitLanguage.switcher().open(BukkitCommandSender.bukkit(sender));
  }

  @Override
  public void monitor(VolmitSender sender) {
    root.monitor(sender);
  }

  @Override
  public void map(VolmitSender sender, String rendererId) {
    root.map(sender, rendererId);
  }

  @Override
  public void monitoringOnly(VolmitSender sender) {
    root.monitoringOnly(sender);
  }

  @Override
  public void hopperNetworkNormalize(VolmitSender sender, int maxChunks, double minHopperUpdates, String world) {
    action.hopperNetworkNormalize(sender, maxChunks, minHopperUpdates, world);
  }

  @Override
  public void benchmark(VolmitSender sender, Set<BenchmarkTarget> targets) {
    BenchmarkRunner.run(sender, targets);
  }

  @Override
  public void bridgeStatus(VolmitSender sender) {
    bridge.status(sender);
  }

  @Override
  public void chunkSample(VolmitSender sender) {
    chunk.sample(sender);
  }

  @Override
  public void chunkWorst(VolmitSender sender) {
    chunk.worst(sender);
  }

  @Override
  public void configGui(VolmitSender sender) {
    config.gui(sender);
  }

  @Override
  public void configMonitor(VolmitSender sender) {
    config.monitor(sender);
  }

  @Override
  public void debugDump(VolmitSender sender, boolean upload) {
    debug.dump(sender, upload);
  }

  @Override
  public void debugEntityData(VolmitSender sender) {
    debug.entityData(sender);
  }

  @Override
  public void devTestAll(VolmitSender sender, int radius) {
    dev.testAll(sender, radius);
  }

  @Override
  public void devVerify(VolmitSender sender) {
    dev.verify(sender);
  }

  @Override
  public void serverDistance(VolmitSender sender, DistanceKind kind, int distance) {
    BukkitCommandDistance.setServer(BukkitCommandSender.bukkit(sender), kind, distance);
  }

  @Override
  public void worldDistance(VolmitSender sender, CommandTargets.World world, DistanceKind kind, int distance) {
    BukkitCommandDistance.setWorld(BukkitCommandSender.bukkit(sender), (World) world.world().handle(), kind, distance);
  }

  @Override
  public void playerDistance(VolmitSender sender, CommandTargets.Player player, DistanceKind kind, int distance) {
    BukkitCommandDistance.setPlayer(BukkitCommandSender.bukkit(sender), (Player) player.player().handle(), kind, distance);
  }

  @Override
  public void environmentInfo(VolmitSender sender) {
    environment.info(sender);
  }

  @Override
  public void integrationStatus(VolmitSender sender) {
    integration.status(sender);
  }

  @Override
  public void pluginApiStatus(VolmitSender sender) {
    PluginApiCommands.status(sender, PluginApiPacks.active());
  }

  @Override
  public void pluginApiReload(VolmitSender sender) {
    PluginApiCommands.reload(sender, PluginApiPacks.active());
  }

  @Override
  public void testRun(VolmitSender sender, boolean full, boolean json) {
    test.run(sender, full, json);
  }

  @Override
  public void testLoad(VolmitSender sender, boolean confirm, int players, int durationSeconds) {
    test.loadtest(sender, confirm, players, durationSeconds);
  }
}
