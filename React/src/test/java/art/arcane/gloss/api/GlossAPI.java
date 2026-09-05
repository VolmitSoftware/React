package art.arcane.gloss.api;

import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;

public interface GlossAPI {
  void refreshDropName(Item item, String bundleHeaderFormat, String bundleEntryFormat,
                       String bundleMoreFormat, int bundleEntryLimit);

  void removeDropPresentation(Item item);

  default boolean refreshEntityOverlay(LivingEntity entity, int stackCount) {
    return false;
  }
}
