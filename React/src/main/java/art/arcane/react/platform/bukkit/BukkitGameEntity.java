package art.arcane.react.platform.bukkit;

import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

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

  @Override
  public boolean isLiving() {
    return entity instanceof LivingEntity;
  }

  @Override
  public boolean isMob() {
    return entity instanceof Mob;
  }

  @Override
  public boolean isMonster() {
    return entity instanceof Monster;
  }

  @Override
  public double speed() {
    return entity.getVelocity().length();
  }

  @Override
  public double health() {
    return entity instanceof LivingEntity living ? living.getHealth() : 0D;
  }

  @Override
  public double maxHealth() {
    if (!(entity instanceof LivingEntity living)) {
      return Double.NaN;
    }
    AttributeInstance attribute = living.getAttribute(Attribute.MAX_HEALTH);
    return attribute == null ? Double.NaN : attribute.getValue();
  }

  @Override
  public boolean isTameable() {
    return entity instanceof Tameable;
  }

  @Override
  public boolean isTamed() {
    return entity instanceof Tameable tameable && tameable.isTamed();
  }

  @Override
  public boolean isBreedable() {
    return entity instanceof Breedable;
  }

  @Override
  public boolean isAdult() {
    return !(entity instanceof Breedable breedable) || breedable.isAdult();
  }

  @Override
  public boolean isAgeLocked() {
    return entity instanceof Breedable breedable && breedable.getAgeLock();
  }
}
