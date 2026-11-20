package art.arcane.react.content.directorcommand;

import art.arcane.react.api.web.ReactWebServer;
import art.arcane.react.api.web.network.NetworkHub;
import art.arcane.react.api.web.network.NetworkLink;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.platform.CommandHost;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.director.DirectorHosts;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.Optional;

/**
 * {@code /react network status|list|join}. On a proxy it shows the servers behind it; on a backend, its registration
 * with the proxy named in web.toml.
 */
@Director(
    name = "network",
    aliases = {"net"},
    origin = DirectorOrigin.BOTH,
    description = "Show the proxy network and this server's place in it",
    descriptionKey = "command.description.network"
)
public class CommandNetwork implements DirectorExecutor {

  @Director(
      name = "status",
      description = "Show the network summary or this server's link to its proxy",
      descriptionKey = "command.description.network.status"
  )
  public void status() {
    VolmitSender sender = sender();
    Optional<ReactWebServer> web = DirectorHosts.webServer();
    if (web.isEmpty()) {
      CommandHost.unavailable(sender, "network status");
      return;
    }
    Optional<NetworkHub> hub = web.get().networkHub();
    if (hub.isPresent()) {
      NetworkHub.ProxyDto proxy = hub.get().snapshot(web.get().serverName()).proxy();
      ReactLanguage.sendPrefixed(sender, CommandMessages.NETWORK_PROXY,
          MessageArgument.untrusted("server", proxy.kind() + " " + proxy.version()),
          MessageArgument.untrusted("players", proxy.players()),
          MessageArgument.untrusted("count", proxy.online()),
          MessageArgument.untrusted("total", proxy.servers()),
          MessageArgument.untrusted("registered", proxy.react()));
      return;
    }
    Optional<NetworkLink.Status> link = web.get().networkStatus();
    if (link.isEmpty()) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.NETWORK_NOT_JOINED);
      return;
    }
    NetworkLink.Status status = link.get();
    String state = switch (status.state()) {
      case CONNECTING -> ReactLanguage.plain(CommandMessages.NETWORK_LINK_CONNECTING);
      case REGISTERED -> ReactLanguage.plain(CommandMessages.NETWORK_LINK_REGISTERED, MessageArgument.untrusted("name", status.member()));
      case REFUSED -> ReactLanguage.plain(CommandMessages.NETWORK_LINK_REFUSED, MessageArgument.untrusted("reason", status.detail()));
      case UNREACHABLE -> ReactLanguage.plain(CommandMessages.NETWORK_LINK_UNREACHABLE, MessageArgument.untrusted("reason", status.detail()));
    };
    ReactLanguage.sendPrefixed(sender, CommandMessages.NETWORK_LINK,
        MessageArgument.untrusted("url", status.proxyUrl()),
        MessageArgument.untrusted("state", state));
  }

  @Director(
      name = "list",
      aliases = {"ls", "servers"},
      description = "List every server behind the proxy",
      descriptionKey = "command.description.network.list"
  )
  public void list() {
    VolmitSender sender = sender();
    Optional<NetworkHub> hub = hub(sender, "network list");
    if (hub.isEmpty()) {
      return;
    }
    NetworkHub.NetworkDto network = hub.get().snapshot(DirectorHosts.webServer().map(ReactWebServer::serverName).orElse(""));
    if (network.servers().isEmpty()) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.NETWORK_NO_SERVERS);
      return;
    }
    status();
    for (NetworkHub.ServerDto server : network.servers()) {
      String react = server.react()
          ? ReactLanguage.plain(CommandMessages.NETWORK_SERVER_REACT,
          MessageArgument.untrusted("average", server.tps() == null ? "-" : Form.f(server.tps(), 1)),
          MessageArgument.untrusted("tick", server.mspt() == null ? "-" : Form.f(server.mspt(), 1)),
          MessageArgument.untrusted("used", server.memoryUsedBytes() == null ? "-" : Form.memSize(server.memoryUsedBytes(), 1)
              + (server.memoryMaxBytes() == null ? "" : " / " + Form.memSize(server.memoryMaxBytes(), 1))))
          : ReactLanguage.plain(CommandMessages.NETWORK_SERVER_NO_REACT);
      ReactLanguage.send(sender, CommandMessages.NETWORK_SERVER,
          MessageArgument.untrusted("name", server.name()),
          MessageArgument.untrusted("address", server.address()),
          MessageArgument.untrusted("status", server.status()),
          MessageArgument.untrusted("players", server.players()),
          MessageArgument.untrusted("duration", server.pingMs() == null ? "-" : server.pingMs() + " ms"),
          MessageArgument.untrusted("detail", react));
    }
  }

  @Director(
      name = "join",
      description = "Show what a backend puts in web.toml to join this proxy",
      descriptionKey = "command.description.network.join"
  )
  public void join() {
    VolmitSender sender = sender();
    Optional<NetworkHub> hub = hub(sender, "network join");
    if (hub.isEmpty()) {
      return;
    }
    if (sender.isPlayer()) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.NETWORK_JOIN_CONSOLE_ONLY);
      return;
    }
    ReactWebServer web = DirectorHosts.webServer().orElseThrow();
    String secret = web.getConfig().getNetworkSecret();
    if (secret.isBlank()) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.NETWORK_NO_SECRET);
      return;
    }
    String url;
    try {
      url = web.resolveDirectUrl();
    } catch (IllegalStateException unavailable) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.WEB_PAIR_UNAVAILABLE, MessageArgument.untrusted("reason", unavailable.getMessage()));
      return;
    }
    ReactLanguage.sendPrefixed(sender, CommandMessages.NETWORK_JOIN,
        MessageArgument.untrusted("url", url),
        MessageArgument.untrusted("value", secret));
  }

  private static Optional<NetworkHub> hub(VolmitSender sender, String command) {
    Optional<ReactWebServer> web = DirectorHosts.webServer();
    if (web.isEmpty()) {
      CommandHost.unavailable(sender, command);
      return Optional.empty();
    }
    Optional<NetworkHub> hub = web.get().networkHub();
    if (hub.isEmpty()) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.NETWORK_PROXY_ONLY);
    }
    return hub;
  }
}
