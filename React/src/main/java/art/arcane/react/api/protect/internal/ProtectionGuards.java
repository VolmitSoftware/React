package art.arcane.react.api.protect.internal;

import art.arcane.react.api.protect.ReactOperation;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class ProtectionGuards {
  private ProtectionGuards() {
  }

  public static boolean allows(Entity entity, ReactOperation operation) {
    ProtectionBinding binding = ProtectionInstaller.binding();

    if (binding == null) {
      return true;
    }

    if (binding.isProtected(entity, operation)) {
      return false;
    }

    return binding.guardAllows(entity, operation);
  }

  public static boolean spawnProtected(EntityType type, String worldName, CreatureSpawnEvent.SpawnReason reason) {
    ProtectionBinding binding = ProtectionInstaller.binding();
    return binding != null && binding.spawnProtected(type, worldName, reason);
  }

  public static int hydrate(Entity entity) {
    ProtectionBinding binding = ProtectionInstaller.binding();
    return binding == null ? 0 : binding.hydrate(entity);
  }

  public static int sweep(long nowMs) {
    return sweep(nowMs, ProtectionRegistry.DEFAULT_MASK_RETENTION_MS);
  }

  public static int sweep(long nowMs, long retentionMs) {
    ProtectionBinding binding = ProtectionInstaller.binding();
    return binding == null ? 0 : binding.sweep(nowMs, retentionMs);
  }
}
