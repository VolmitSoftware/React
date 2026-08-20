package art.arcane.gloss.api;

import org.bukkit.entity.Item;

public interface GlossAPI {
  void refreshDropName(Item item, String bundleFormat, int bundleEntryLimit);
}
