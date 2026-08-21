package art.arcane.gloss.api;

import org.bukkit.entity.Item;

public interface GlossAPI {
  void refreshDropName(Item item, String bundleHeaderFormat, String bundleEntryFormat,
                       String bundleMoreFormat, int bundleEntryLimit);

  void removeDropPresentation(Item item);
}
