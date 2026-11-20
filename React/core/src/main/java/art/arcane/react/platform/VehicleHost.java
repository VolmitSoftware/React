package art.arcane.react.platform;

import java.util.List;
import java.util.function.Predicate;

/**
 * Minecarts and boats. Calls run on the thread that owns the vehicle.
 */
public interface VehicleHost {
  /**
   * Wraps a platform entity handle taken from {@link GameEntity#handle()}.
   */
  GameEntity vehicle(Object handle);

  boolean isMinecart(GameEntity entity);

  boolean isBoat(GameEntity entity);

  /**
   * True while the vehicle has velocity, or on Bukkit a non-zero flying or derailed minecart modifier.
   */
  boolean isMoving(GameEntity vehicle);

  double velocitySquared(GameEntity vehicle);

  boolean hasPassengers(GameEntity vehicle);

  /**
   * Zeroes the vehicle's velocity and, for minecarts, its flying and derailed modifiers.
   */
  void stop(GameEntity vehicle);

  /**
   * Online players to sample vehicles around on regionized servers.
   */
  List<GameEntity> anchors();

  boolean isOnline(GameEntity player);

  /**
   * Entities near the player that {@code claim} accepts, at most {@code limit}, through React's shared nearby sampler.
   */
  List<GameEntity> sampleNear(GameEntity player, String owner, double horizontalRadius, double verticalRadius, int limit,
                              Predicate<GameEntity> claim);
}
