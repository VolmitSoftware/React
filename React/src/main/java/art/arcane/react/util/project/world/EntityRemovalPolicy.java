package art.arcane.react.util.project.world;

import org.bukkit.entity.Entity;

public final class EntityRemovalPolicy {
  private EntityRemovalPolicy() {
  }

  public static boolean protectsNamedEntity(Entity entity, boolean protectNamedEntities) {
    if (!protectNamedEntities || entity == null) {
      return false;
    }

    String customName = entity.getCustomName();
    return customName != null && !customName.isBlank();
  }
}
