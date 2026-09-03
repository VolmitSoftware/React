package art.arcane.react.util.project.world;

import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Objects;

public final class DistanceSupport {
  public static final int INHERIT_DISTANCE = -1;
  public static final int MIN_DISTANCE = 2;
  public static final int MAX_DISTANCE = 32;

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
    Objects.requireNonNull(type);
    return isStandardDistance(distance)
        || type == DistanceType.SEND && distance == INHERIT_DISTANCE;
  }

  public static boolean isValidPlayerDistance(int distance) {
    return isStandardDistance(distance) || distance == INHERIT_DISTANCE;
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

  private static boolean isStandardDistance(int distance) {
    return distance >= MIN_DISTANCE && distance <= MAX_DISTANCE;
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
    VIEW("view", "setViewDistance"),
    SIMULATION("simulation", "setSimulationDistance"),
    SEND("send view", "setSendViewDistance");

    private final String displayName;
    private final String setterName;

    DistanceType(String displayName, String setterName) {
      this.displayName = displayName;
      this.setterName = setterName;
    }

    public String displayName() {
      return displayName;
    }

    private String setterName() {
      return setterName;
    }
  }
}
