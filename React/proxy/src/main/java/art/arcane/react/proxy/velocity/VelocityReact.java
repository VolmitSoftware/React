package art.arcane.react.proxy.velocity;

import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.proxy.ProxyHost;
import art.arcane.react.proxy.ProxyRuntime;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/** React's view of a Velocity proxy, and the {@code /react} command. */
final class VelocityReact implements ProxyHost {
  private static final Logger CONSOLE = new Logger("React console", null) {
  };
  private final ProxyServer proxy;
  private final Object plugin;
  private final Path dataDirectory;
  private final Logger logger;
  private final ProxyRuntime runtime;

  VelocityReact(ProxyServer proxy, Object plugin, Path dataDirectory, Logger logger) {
    this.proxy = proxy;
    this.plugin = plugin;
    this.dataDirectory = dataDirectory;
    this.logger = logger;
    this.runtime = new ProxyRuntime(this);
  }

  void start() {
    runtime.start();
    CommandMeta meta = proxy.getCommandManager().metaBuilder(ProxyRuntime.COMMAND_LABELS.get(0))
        .aliases(ProxyRuntime.COMMAND_LABELS.subList(1, ProxyRuntime.COMMAND_LABELS.size()).toArray(new String[0]))
        .plugin(plugin)
        .build();
    proxy.getCommandManager().register(meta, new SimpleCommand() {
      @Override
      public void execute(Invocation invocation) {
        runtime.execute(new VelocitySender(invocation.source()), invocation.alias(), invocation.arguments());
      }

      @Override
      public List<String> suggest(Invocation invocation) {
        String[] arguments = invocation.arguments();
        return runtime.complete(new VelocitySender(invocation.source()), invocation.alias(),
            arguments.length == 0 ? new String[]{""} : arguments);
      }

      @Override
      public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(ProxyRuntime.PERMISSION) || invocation.source().hasPermission("react.*");
      }
    });
  }

  void stop() {
    proxy.getCommandManager().unregister(ProxyRuntime.COMMAND_LABELS.get(0));
    runtime.stop();
  }

  @Override
  public String kind() {
    return "Velocity";
  }

  @Override
  public String version() {
    return proxy.getVersion().getVersion();
  }

  @Override
  public int onlinePlayers() {
    return proxy.getPlayerCount();
  }

  @Override
  public List<Server> servers() {
    List<Server> servers = new ArrayList<>();
    for (RegisteredServer server : proxy.getAllServers()) {
      ServerInfo info = server.getServerInfo();
      InetSocketAddress address = info.getAddress();
      servers.add(new Server(info.getName(), address.getHostString(), address.getPort(), server.getPlayersConnected().size()));
    }
    return servers;
  }

  @Override
  public CompletableFuture<OptionalLong> ping(String name) {
    Optional<RegisteredServer> server = proxy.getServer(name);
    if (server.isEmpty()) {
      return CompletableFuture.completedFuture(OptionalLong.empty());
    }
    long started = System.nanoTime();
    return server.get().ping().handle((Object answer, Throwable failure) ->
        failure == null ? OptionalLong.of((System.nanoTime() - started) / 1_000_000L) : OptionalLong.empty());
  }

  @Override
  public File dataFolder() {
    return dataDirectory.toFile();
  }

  @Override
  public Logger logger() {
    return logger;
  }

  /** Velocity logs through Log4j, which React Web reads directly; JUL handlers cannot attach there. */
  @Override
  public Logger consoleLogger() {
    return CONSOLE;
  }

  @Override
  public String bindAddress() {
    InetSocketAddress address = proxy.getBoundAddress();
    return address.getHostString() + ":" + address.getPort();
  }

  @Override
  public String motd() {
    return PlainTextComponentSerializer.plainText().serialize(proxy.getConfiguration().getMotd());
  }

  @Override
  public Map<UUID, Integer> playerPings() {
    Map<UUID, Integer> pings = new HashMap<>();
    for (Player player : proxy.getAllPlayers()) {
      pings.put(player.getUniqueId(), (int) Math.min(Integer.MAX_VALUE, Math.max(0L, player.getPing())));
    }
    return pings;
  }

  @Override
  public boolean dispatchConsoleCommand(String command) {
    proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), command);
    return true;
  }

  @Override
  public Optional<ReactCommandSender> sender(Object handle) {
    return handle instanceof CommandSource source ? Optional.of(new VelocitySender(source)) : Optional.empty();
  }
}
