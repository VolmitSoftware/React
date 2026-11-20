package art.arcane.react.proxy.bungee;

import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.proxy.ProxyHost;
import art.arcane.react.proxy.ProxyRuntime;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/** React's view of a BungeeCord proxy, and the {@code /react} command. */
final class BungeeReact implements ProxyHost {
  private final ReactBungeePlugin plugin;
  private final ProxyServer proxy;
  private final ProxyRuntime runtime;

  BungeeReact(ReactBungeePlugin plugin) {
    this.plugin = plugin;
    this.proxy = plugin.getProxy();
    this.runtime = new ProxyRuntime(this);
  }

  void start() {
    runtime.start();
    proxy.getPluginManager().registerCommand(plugin, new ReactCommand());
    proxy.getPluginManager().registerListener(plugin, new Passthrough());
  }

  void stop() {
    proxy.getPluginManager().unregisterListeners(plugin);
    proxy.getPluginManager().unregisterCommands(plugin);
    runtime.stop();
  }

  private final class ReactCommand extends Command implements TabExecutor {
    private ReactCommand() {
      super(ProxyRuntime.COMMAND_LABELS.get(0), ProxyRuntime.PERMISSION,
          ProxyRuntime.COMMAND_LABELS.subList(1, ProxyRuntime.COMMAND_LABELS.size()).toArray(new String[0]));
    }

    @Override
    public void execute(CommandSender sender, String[] arguments) {
      runtime.execute(new BungeeSender(sender), getName(), arguments);
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] arguments) {
      return runtime.complete(new BungeeSender(sender), getName(), arguments.length == 0 ? new String[]{""} : arguments);
    }
  }

  public static final class Passthrough implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(ChatEvent event) {
      if (event.isCancelled() || !(event.getSender() instanceof ProxiedPlayer player)) {
        return;
      }
      String message = event.getMessage();
      switch (BackendPassthrough.route(message, player.hasPermission(ProxyRuntime.PERMISSION), player.getPendingConnection().getVersion())) {
        case FORWARD_PACKET -> event.setMessage(message.substring(1));
        case RESEND -> {
          event.setCancelled(true);
          player.chat(message);
        }
        case PROXY -> {
        }
      }
    }
  }

  @Override
  public String kind() {
    return proxy.getName();
  }

  @Override
  public String version() {
    String version = proxy.getVersion();
    return version == null ? "" : version;
  }

  @Override
  public int onlinePlayers() {
    return proxy.getOnlineCount();
  }

  @Override
  public List<Server> servers() {
    List<Server> servers = new ArrayList<>();
    for (ServerInfo info : List.copyOf(proxy.getServers().values())) {
      SocketAddress address = info.getSocketAddress();
      if (address instanceof InetSocketAddress inet) {
        servers.add(new Server(info.getName(), inet.getHostString(), inet.getPort(), info.getPlayers().size()));
      }
    }
    return servers;
  }

  @Override
  public CompletableFuture<OptionalLong> ping(String name) {
    ServerInfo info = proxy.getServerInfo(name);
    CompletableFuture<OptionalLong> result = new CompletableFuture<>();
    if (info == null) {
      result.complete(OptionalLong.empty());
      return result;
    }
    long started = System.nanoTime();
    info.ping((ServerPing answer, Throwable failure) -> result.complete(failure == null && answer != null
        ? OptionalLong.of((System.nanoTime() - started) / 1_000_000L) : OptionalLong.empty()));
    return result;
  }

  @Override
  public File dataFolder() {
    return plugin.getDataFolder();
  }

  @Override
  public Logger logger() {
    return plugin.getLogger();
  }

  @Override
  public Logger consoleLogger() {
    return proxy.getLogger();
  }

  @Override
  public String bindAddress() {
    ListenerInfo listener = firstListener();
    if (listener != null && listener.getSocketAddress() instanceof InetSocketAddress inet) {
      return inet.getHostString() + ":" + inet.getPort();
    }
    return "";
  }

  @Override
  public String motd() {
    ListenerInfo listener = firstListener();
    return listener == null || listener.getMotd() == null ? "" : listener.getMotd();
  }

  private ListenerInfo firstListener() {
    Iterator<ListenerInfo> listeners = proxy.getConfig().getListeners().iterator();
    return listeners.hasNext() ? listeners.next() : null;
  }

  @Override
  public Map<UUID, Integer> playerPings() {
    Map<UUID, Integer> pings = new HashMap<>();
    for (ProxiedPlayer player : proxy.getPlayers()) {
      pings.put(player.getUniqueId(), Math.max(0, player.getPing()));
    }
    return pings;
  }

  @Override
  public boolean dispatchConsoleCommand(String command) {
    return proxy.getPluginManager().dispatchCommand(proxy.getConsole(), command);
  }

  @Override
  public Optional<ReactCommandSender> sender(Object handle) {
    return handle instanceof CommandSender sender ? Optional.of(new BungeeSender(sender)) : Optional.empty();
  }
}
