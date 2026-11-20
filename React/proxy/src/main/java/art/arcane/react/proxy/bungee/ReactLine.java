package art.arcane.react.proxy.bungee;

import art.arcane.react.proxy.ProxyRuntime;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * A player's chat line or tab-complete cursor that starts with {@code /react}. BungeeCord dispatches and completes
 * commands it knows for players itself, answering "no permission" instead of forwarding, so players never reach a
 * registered {@code /react}: those with {@code react.use} get it run on the proxy from the chat event, everyone else's
 * line goes to the backend as sent.
 *
 * @param label the typed label, lower case
 * @param args  the arguments; the last one is empty when the line ends in a space
 */
record ReactLine(String label, String[] args) {
  enum Route {
    /** Not {@code /react}: left alone. */
    IGNORE,
    /** The proxy's React runs or completes it; the line never reaches the backend. */
    PROXY,
    /** Left alone so BungeeCord forwards it and the backend's React answers. */
    BACKEND
  }

  static Route route(String line, boolean permitted) {
    if (parse(line).isEmpty()) {
      return Route.IGNORE;
    }
    return permitted ? Route.PROXY : Route.BACKEND;
  }

  /** A line counts once its label is complete: {@code /react} or {@code /react ...}, never {@code /reactor}. */
  static Optional<ReactLine> parse(String line) {
    if (line == null || line.length() < 2 || line.charAt(0) != '/') {
      return Optional.empty();
    }
    String[] split = line.substring(1).split(" ", -1);
    String label = split[0].toLowerCase(Locale.ROOT);
    if (!ProxyRuntime.COMMAND_LABELS.contains(label)) {
      return Optional.empty();
    }
    return Optional.of(new ReactLine(label, Arrays.copyOfRange(split, 1, split.length)));
  }

  /** A completion cursor still on the label, such as {@code /react}, completes command names, not arguments. */
  boolean completesArguments() {
    return args.length > 0;
  }
}
