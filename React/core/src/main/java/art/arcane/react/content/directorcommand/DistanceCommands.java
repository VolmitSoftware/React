package art.arcane.react.content.directorcommand;

import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.platform.CommandHost.DistanceKind;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageKey;

import java.util.Objects;

/**
 * The limits and replies of {@code /react distance}, shared by every platform. Platforms validate with these rules,
 * apply the change on their own thread, and answer through a {@link Reply} that knows where the sender lives.
 */
public final class DistanceCommands {
  public static final int INHERIT_DISTANCE = -1;
  public static final int MIN_DISTANCE = 2;
  public static final int MAX_DISTANCE = 32;
  public static final String SERVER_TARGET = "all loaded worlds";

  private DistanceCommands() {
  }

  @FunctionalInterface
  public interface Reply {
    void send(MessageKey key, MessageArgument... arguments);
  }

  public static boolean isValidWorldDistance(DistanceKind kind, int distance) {
    Objects.requireNonNull(kind);
    return isStandardDistance(distance) || kind == DistanceKind.SEND && distance == INHERIT_DISTANCE;
  }

  public static boolean isValidPlayerDistance(int distance) {
    return isStandardDistance(distance) || distance == INHERIT_DISTANCE;
  }

  public static String typeName(DistanceKind kind) {
    return switch (Objects.requireNonNull(kind)) {
      case VIEW -> "view";
      case SIMULATION -> "simulation";
      case SEND -> "send view";
    };
  }

  public static String failureContext(String scope, String target, DistanceKind kind) {
    return "Failed to set " + scope + " " + typeName(kind) + " distance for " + target;
  }

  public static void invalid(Reply reply, boolean inheritanceSupported) {
    String range = inheritanceSupported
        ? "between " + MIN_DISTANCE + " and " + MAX_DISTANCE + " chunks, or " + INHERIT_DISTANCE + " to inherit"
        : "between " + MIN_DISTANCE + " and " + MAX_DISTANCE + " chunks";
    reply.send(CommandMessages.DISTANCE_INVALID, MessageArgument.untrusted("range", range));
  }

  public static void unavailable(Reply reply, String scope, DistanceKind kind) {
    reply.send(CommandMessages.DISTANCE_UNAVAILABLE,
        MessageArgument.untrusted("scope", scope),
        MessageArgument.untrusted("type", typeName(kind)));
  }

  public static void set(Reply reply, String scope, String target, DistanceKind kind, int distance) {
    String value = distance == INHERIT_DISTANCE ? "inherit" : Integer.toString(distance);
    reply.send(CommandMessages.DISTANCE_SET,
        MessageArgument.untrusted("scope", scope),
        MessageArgument.untrusted("target", target),
        MessageArgument.untrusted("type", typeName(kind)),
        MessageArgument.untrusted("distance", value));
  }

  public static void failed(Reply reply, String scope, String target, DistanceKind kind) {
    reply.send(CommandMessages.DISTANCE_FAILED,
        MessageArgument.untrusted("scope", scope),
        MessageArgument.untrusted("target", target),
        MessageArgument.untrusted("type", typeName(kind)));
  }

  public static void scheduleFailed(Reply reply) {
    reply.send(CommandMessages.DISTANCE_SCHEDULE_FAILED);
  }

  private static boolean isStandardDistance(int distance) {
    return distance >= MIN_DISTANCE && distance <= MAX_DISTANCE;
  }
}
