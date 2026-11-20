package art.arcane.react.proxy.bungee;

import art.arcane.react.proxy.ProxyRuntime;

import java.util.Locale;

/**
 * How a chat line from a player reaches the backend when it is {@code /react} and the player lacks {@code react.use}
 * on the proxy, so the backend's own React answers it as it would without a proxy. BungeeCord checks a registered
 * command's permission itself and answers "no permission" instead of forwarding, so the line has to leave its
 * dispatch before that check.
 */
final class BackendPassthrough {
  /** 1.19: commands arrive as their own packet, which BungeeCord forwards as received whatever the chat event holds. */
  static final int COMMAND_PACKET_PROTOCOL = 759;

  enum Route {
    /** The proxy's React runs it. */
    PROXY,
    /** The event's text drops its slash so BungeeCord skips dispatch and forwards the client's command packet. */
    FORWARD_PACKET,
    /** Older clients send commands as chat: the event is cancelled and the line sent to the backend as the player's chat. */
    RESEND
  }

  private BackendPassthrough() {
  }

  static Route route(String message, boolean permitted, int protocol) {
    if (permitted || !isReactCommand(message)) {
      return Route.PROXY;
    }
    return protocol >= COMMAND_PACKET_PROTOCOL ? Route.FORWARD_PACKET : Route.RESEND;
  }

  static boolean isReactCommand(String message) {
    if (message == null || message.length() < 2 || message.charAt(0) != '/') {
      return false;
    }
    int end = message.indexOf(' ');
    String label = (end < 0 ? message.substring(1) : message.substring(1, end)).toLowerCase(Locale.ROOT);
    return ProxyRuntime.COMMAND_LABELS.contains(label);
  }
}
