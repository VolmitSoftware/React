package art.arcane.react.util.project.world;

import art.arcane.react.content.directorcommand.DistanceCommands;
import art.arcane.react.platform.CommandHost.DistanceKind;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Objects;

public final class DistanceSupport {

  private DistanceSupport() {
  }

  public static boolean supportsWorldDistanceSetters() {
    return supportsWorld(DistanceType.VIEW) && supportsWorld(DistanceType.SIMULATION);
  }

  public static boolean supportsWorld(DistanceType type) {
    return hasMethod(World.class, Objects.requireNonNull(type).setterName(), int.class);
  }

  public static boolean supportsPlayer(DistanceType type) {
    return hasMethod(Player.class, Objects.requireNonNull(type).setterName(), int.class);
  }

  public static boolean isValidWorldDistance(DistanceType type, int distance) {
    return DistanceCommands.isValidWorldDistance(Objects.requireNonNull(type).kind(), distance);
  }

  public static boolean isValidPlayerDistance(int distance) {
    return DistanceCommands.isValidPlayerDistance(distance);
  }

  public static void set(World world, DistanceType type, int distance) {
    Objects.requireNonNull(world);
    switch (Objects.requireNonNull(type)) {
      case VIEW -> world.setViewDistance(distance);
      case SIMULATION -> world.setSimulationDistance(distance);
      case SEND -> world.setSendViewDistance(distance);
    }
  }

  public static void set(Player player, DistanceType type, int distance) {
    Objects.requireNonNull(player);
    switch (Objects.requireNonNull(type)) {
      case VIEW -> player.setViewDistance(distance);
      case SIMULATION -> player.setSimulationDistance(distance);
      case SEND -> player.setSendViewDistance(distance);
    }
  }

  private static boolean hasMethod(Class<?> type, String name, Class<?>... params) {
    try {
      Method ignored = type.getMethod(name, params);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  public enum DistanceType {
    VIEW(DistanceKind.VIEW, "setViewDistance"),
    SIMULATION(DistanceKind.SIMULATION, "setSimulationDistance"),
    SEND(DistanceKind.SEND, "setSendViewDistance");

    private final DistanceKind kind;
    private final String setterName;

    DistanceType(DistanceKind kind, String setterName) {
      this.kind = kind;
      this.setterName = setterName;
    }

    public DistanceKind kind() {
      return kind;
    }

    public String displayName() {
      return DistanceCommands.typeName(kind);
    }

    public static DistanceType of(DistanceKind kind) {
      return switch (Objects.requireNonNull(kind)) {
        case VIEW -> VIEW;
        case SIMULATION -> SIMULATION;
        case SEND -> SEND;
      };
    }

    private String setterName() {
      return setterName;
    }
  }
}
