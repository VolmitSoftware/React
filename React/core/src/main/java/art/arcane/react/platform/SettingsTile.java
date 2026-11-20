package art.arcane.react.platform;

import java.util.List;
import java.util.Map;

/**
 * One item of a settings menu. {@code position} runs from the left edge to the right edge of the row, centred on the
 * middle slot: -4 to 4 in a chest, -1 to 1 in a dropper. {@code icon} is a vanilla item id such as
 * {@code comparator}; {@code fallbackIcon} is used when the platform has no item by that id. {@code dyeColor} is an
 * RGB colour for dyeable icons such as leather armour, or null. A null {@code name} keeps the item's own name.
 */
public record SettingsTile(
    String key,
    int position,
    int row,
    String icon,
    String fallbackIcon,
    String name,
    List<String> lore,
    boolean enchanted,
    Map<SettingsClick, SettingsAction> actions,
    Integer dyeColor
) {
  public SettingsTile {
    lore = lore == null ? List.of() : List.copyOf(lore);
    actions = actions == null ? Map.of() : Map.copyOf(actions);
  }

  public SettingsTile(String key, int position, int row, String icon, String fallbackIcon, String name, List<String> lore,
                      boolean enchanted, Map<SettingsClick, SettingsAction> actions) {
    this(key, position, row, icon, fallbackIcon, name, lore, enchanted, actions, null);
  }

  public int slot() {
    return slot(SettingsScreen.CHEST_COLUMNS);
  }

  public int slot(int columns) {
    return (row * columns) + position + ((columns - 1) / 2);
  }

  public SettingsAction actionFor(SettingsClick click) {
    return actions.get(click);
  }
}
