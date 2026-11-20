package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.api.entity.EntityPriority;
import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactProtection;
import art.arcane.react.api.protect.internal.ProtectionGuards;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.platform.EntityHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.util.project.world.CustomMobChecker;
import art.arcane.react.util.project.world.EntityRemovalPolicy;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

final class BukkitEntityHost implements EntityHost {
  private static final List<String> TYPES = types();

  @Override
  public List<GameEntity> nearby(ReactPlayer player, double radius) {
    if (player == null || !(player.handle() instanceof Player bukkit)) {
      return List.of();
    }
    return wrap(bukkit.getNearbyEntities(radius, radius, radius));
  }

  @Override
  public List<GameEntity> entities(ReactWorld world) {
    return wrap(((World) world.handle()).getEntities());
  }

  @Override
  public List<GameEntity> chunkEntities(ReactWorld world, int chunkX, int chunkZ) {
    World bukkit = (World) world.handle();
    if (!bukkit.isChunkLoaded(chunkX, chunkZ)) {
      return List.of();
    }
    return wrap(List.of(bukkit.getChunkAt(chunkX, chunkZ).getEntities()));
  }

  @Override
  public int chunkEntityCount(UUID worldId, int chunkX, int chunkZ) {
    World world = worldId == null ? null : Bukkit.getWorld(worldId);
    if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
      return -1;
    }
    return world.getChunkAt(chunkX, chunkZ).getEntities().length;
  }

  @Override
  public int worldEntityCount(UUID worldId) {
    World world = worldId == null ? null : Bukkit.getWorld(worldId);
    return world == null ? 0 : WorldEntitySnapshots.count(world);
  }

  @Override
  public Collection<String> entityTypes() {
    return TYPES;
  }

  @Override
  public double priority(GameEntity entity) {
    return ReactEntity.getPriority(BukkitGameEntity.bukkit(entity));
  }

  @Override
  public double typePriority(String type) {
    try {
      return ReactConfiguration.get().getPriority().getPriority(EntityType.valueOf(type));
    } catch (IllegalArgumentException unknown) {
      return EntityPriority.BASELINE;
    }
  }

  @Override
  public boolean isProtected(GameEntity entity, ReactOperation operation) {
    return ReactProtection.isProtected(BukkitGameEntity.bukkit(entity), operation);
  }

  @Override
  public boolean allows(GameEntity entity, ReactOperation operation) {
    return ProtectionGuards.allows(BukkitGameEntity.bukkit(entity), operation);
  }

  @Override
  public boolean protectsNamed(GameEntity entity, boolean protectNamedEntities) {
    return EntityRemovalPolicy.protectsNamedEntity(BukkitGameEntity.bukkit(entity), protectNamedEntities);
  }

  @Override
  public boolean isCustomMob(GameEntity entity) {
    return CustomMobChecker.isCustom(BukkitGameEntity.bukkit(entity));
  }

  @Override
  public void remove(GameEntity entity) {
    React.kill(BukkitGameEntity.bukkit(entity));
  }

  // Chunk quarantine culling

  @Override
  public boolean isDead(GameEntity entity) {
    return BukkitGameEntity.bukkit(entity).isDead();
  }

  @Override
  public boolean isTamed(GameEntity entity) {
    return BukkitGameEntity.bukkit(entity) instanceof Tameable tameable && tameable.isTamed();
  }

  @Override
  public boolean isVillager(GameEntity entity) {
    return BukkitGameEntity.bukkit(entity) instanceof Villager;
  }

  @Override
  public boolean isBoss(GameEntity entity) {
    Entity bukkit = BukkitGameEntity.bukkit(entity);
    return bukkit instanceof EnderDragon || bukkit instanceof Wither || bukkit instanceof Warden;
  }

  @Override
  public void removeNow(GameEntity entity) {
    BukkitGameEntity.bukkit(entity).remove();
  }

  @Override
  public void kill(GameEntity entity, int seconds) {
    React.kill(BukkitGameEntity.bukkit(entity), seconds);
  }

  private static List<GameEntity> wrap(Collection<? extends Entity> entities) {
    if (entities == null || entities.isEmpty()) {
      return List.of();
    }
    List<GameEntity> result = new ArrayList<>(entities.size());
    for (Entity entity : entities) {
      if (entity != null) {
        result.add(new BukkitGameEntity(entity));
      }
    }
    return result;
  }

  private static List<String> types() {
    List<String> names = new ArrayList<>();
    for (EntityType type : EntityType.values()) {
      names.add(type.name());
    }
    return List.copyOf(names);
  }
}
