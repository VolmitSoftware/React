package art.arcane.react.api.web.network;

import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

/** What React on a proxy reads from Velocity or BungeeCord: its servers, their players, and a ping to each. */
public interface NetworkView {
  /** {@code Velocity} or {@code BungeeCord}. */
  String kind();

  String version();

  int onlinePlayers();

  List<Server> servers();

  /** The round trip of a server list ping in milliseconds; empty when the server does not answer. */
  CompletableFuture<OptionalLong> ping(String server);

  record Server(String name, String host, int port, int players) {
    public String address() {
      return (host.indexOf(':') >= 0 ? "[" + host + "]" : host) + ":" + port;
    }
  }
}
