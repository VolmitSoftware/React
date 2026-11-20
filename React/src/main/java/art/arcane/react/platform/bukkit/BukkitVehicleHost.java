package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.VehicleHost;
import art.arcane.react.util.project.world.NearbyEntitySampler;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

final class BukkitVehicleHost implements VehicleHost {
  @Override
  public GameEntity vehicle(Object handle) {
    return new BukkitGameEntity((Entity) handle);
  }

  @Override
  public boolean isMinecart(GameEntity entity) {
    return entity.handle() instanceof Minecart;
  }

  @Override
  public boolean isBoat(GameEntity entity) {
    return entity.handle() instanceof Boat;
  }

  @Override
  public boolean isMoving(GameEntity vehicle) {
    Entity entity = BukkitGameEntity.bukkit(vehicle);
    if (nonZero(entity.getVelocity())) {
      return true;
    }
    return entity instanceof Minecart minecart
        && (nonZero(minecart.getFlyingVelocityMod()) || nonZero(minecart.getDerailedVelocityMod()));
  }

  @Override
  public double velocitySquared(GameEntity vehicle) {
    return BukkitGameEntity.bukkit(vehicle).getVelocity().lengthSquared();
  }

  @Override
  public boolean hasPassengers(GameEntity vehicle) {
    return !BukkitGameEntity.bukkit(vehicle).getPassengers().isEmpty();
  }

  @Override
  public void stop(GameEntity vehicle) {
    Entity entity = BukkitGameEntity.bukkit(vehicle);
    entity.setVelocity(new Vector(0, 0, 0));
    if (entity instanceof Minecart minecart) {
      minecart.setDerailedVelocityMod(new Vector(0, 0, 0));
      minecart.setFlyingVelocityMod(new Vector(0, 0, 0));
    }
  }

  @Override
  public List<GameEntity> anchors() {
    EntityController controller = React.controller(EntityController.class);
    Player[] players = controller == null ? null : controller.getFoliaPlayers();
    if (players == null || players.length == 0) {
      return List.of();
    }
    List<GameEntity> result = new ArrayList<>(players.length);
    for (Player player : players) {
      if (player != null) {
        result.add(new BukkitGameEntity(player));
      }
    }
    return result;
  }

  @Override
  public boolean isOnline(GameEntity player) {
    return player.handle() instanceof Player bukkit && bukkit.isOnline();
  }

  @Override
  public List<GameEntity> sampleNear(GameEntity player, String owner, double horizontalRadius, double verticalRadius,
                                     int limit, Predicate<GameEntity> claim) {
    EntityController controller = React.controller(EntityController.class);
    if (controller == null || !(player.handle() instanceof Player bukkit)) {
      return List.of();
    }
    List<Entity> nearby = controller.getNearbyEntitySampler().sample(
        bukkit,
        new NearbyEntitySampler.Request(owner, horizontalRadius, verticalRadius, limit),
        entity -> claim.test(new BukkitGameEntity(entity))
    );
    List<GameEntity> result = new ArrayList<>(nearby.size());
    for (Entity entity : nearby) {
      result.add(new BukkitGameEntity(entity));
    }
    return result;
  }

  private static boolean nonZero(Vector vector) {
    return vector != null && (vector.getX() != 0 || vector.getY() != 0 || vector.getZ() != 0);
  }
}
