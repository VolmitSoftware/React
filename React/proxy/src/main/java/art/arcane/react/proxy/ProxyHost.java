package art.arcane.react.proxy;

import art.arcane.react.api.web.network.NetworkView;
import art.arcane.react.platform.ReactCommandSender;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/** What React needs from Velocity or BungeeCord beyond the server list: its folder, log, players and console. */
public interface ProxyHost extends NetworkView {
  File dataFolder();

  Logger logger();

  /** The logger whose lines React Web's console shows next to the Log4j capture. */
  Logger consoleLogger();

  /** {@code host:port} players connect to. */
  String bindAddress();

  String motd();

  Map<UUID, Integer> playerPings();

  boolean dispatchConsoleCommand(String command);

  /** A Velocity or BungeeCord command source as React's command sender. */
  Optional<ReactCommandSender> sender(Object handle);
}
