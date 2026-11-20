package art.arcane.react.platform;

import java.util.List;

public record MapItemView(int mapId, MapItemTags tags, List<String> legacyLore, boolean hasCustomName) {
  public MapItemView {
    tags = tags == null ? MapItemTags.NONE : tags;
    legacyLore = legacyLore == null ? List.of() : List.copyOf(legacyLore);
  }

  public boolean hasMapId() {
    return mapId >= 0;
  }
}
