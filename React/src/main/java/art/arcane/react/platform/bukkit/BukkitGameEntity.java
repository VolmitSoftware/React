package art.arcane.react.platform.bukkit;

import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

public record BukkitGameEntity(Entity entity) implements GameEntity {
  public BukkitGameEntity {
    Objects.requireNonNull(entity, "entity");
  }

  public static Entity bukkit(GameEntity entity) {
    return (Entity) entity.handle();
  }

  public static GameLocation location(Location location) {
    World world = location == null ? null : location.getWorld();
    return world == null ? null : new GameLocation(world.getUID(), location.getX(), location.getY(), location.getZ());
  }

  @Override
  public UUID id() {
    return entity.getUniqueId();
  }

  @Override
  public String type() {
    return entity.getType().name();
  }

  @Override
  public GameLocation location() {
    return location(entity.getLocation());
  }

  @Override
  public boolean isValid() {
    return !entity.isDead();
  }

  @Override
  public int ticksLived() {
    return entity.getTicksLived();
  }

  @Override
  public boolean hasCustomName() {
    return entity.getCustomName() != null;
  }

  @Override
  public boolean isPlayer() {
    return entity instanceof Player;
  }

  @Override
  public Object handle() {
    return entity;
  }
}
