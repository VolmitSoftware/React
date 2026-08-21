package art.arcane.react.api.protect.internal;

import art.arcane.react.api.protect.ReactOperation;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;

public interface ProtectionBinding {
  int operationsFor(Entity entity);

  boolean isProtected(Entity entity, ReactOperation operation);

  String ownerOf(Entity entity);

  boolean protect(Entity entity, String ownerName, int operations);

  boolean release(Entity entity, String ownerName);

  boolean invalidate(Entity entity);

  boolean spawnProtected(EntityType type, String worldName, CreatureSpawnEvent.SpawnReason reason);

  boolean guardAllows(Entity entity, ReactOperation operation);

  int hydrate(Entity entity);

  int sweep(long nowMs, long retentionMs);
}
