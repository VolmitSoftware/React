package art.arcane.react.platform;

import java.util.List;
import java.util.Map;

/**
 * One chest slot. {@code position} is the centred column -4..4 (0 is the middle slot); {@code row} is 0-based.
 */
public record MapHubTile(
    String key,
    int position,
    int row,
    MapHubIcon icon,
    String name,
    List<String> lore,
    Map<MapHubClick, MapHubAction> actions
) {
  public MapHubTile {
    lore = lore == null ? List.of() : List.copyOf(lore);
    actions = actions == null ? Map.of() : Map.copyOf(actions);
  }

  public int slot() {
    return (row * 9) + position + 4;
  }

  public MapHubAction actionFor(MapHubClick click) {
    return actions.get(click);
  }
}
