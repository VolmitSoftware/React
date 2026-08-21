package art.arcane.react.api.protect;

import art.arcane.react.api.protect.internal.ProtectionBinding;
import art.arcane.react.api.protect.internal.ProtectionInstaller;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class ReactProtection {
  private ReactProtection() {
  }

  public static boolean available() {
    return ProtectionInstaller.binding() != null;
  }

  public static boolean protect(Entity entity, Plugin owner, ReactOperation... operations) {
    return protect(entity, owner, ReactOperations.of(operations));
  }

  public static boolean protect(Entity entity, Plugin owner, int operations) {
    ProtectionBinding current = ProtectionInstaller.binding();

    if (current == null || owner == null) {
      return false;
    }

    return current.protect(entity, owner.getName(), operations);
  }

  public static boolean release(Entity entity, Plugin owner) {
    ProtectionBinding current = ProtectionInstaller.binding();

    if (current == null || owner == null) {
      return false;
    }

    return current.release(entity, owner.getName());
  }

  public static boolean invalidate(Entity entity) {
    ProtectionBinding current = ProtectionInstaller.binding();
    return current != null && current.invalidate(entity);
  }

  public static boolean isProtected(Entity entity, ReactOperation operation) {
    ProtectionBinding current = ProtectionInstaller.binding();
    return current != null && current.isProtected(entity, operation);
  }

  public static int operationsFor(Entity entity) {
    ProtectionBinding current = ProtectionInstaller.binding();
    return current == null ? ReactOperations.NONE : current.operationsFor(entity);
  }

  public static String ownerOf(Entity entity) {
    ProtectionBinding current = ProtectionInstaller.binding();
    return current == null ? "" : current.ownerOf(entity);
  }
}
