package art.arcane.react.platform;

import java.util.List;
import java.util.Map;

/**
 * One item of a settings menu. {@code position} runs from -4 to 4 across the row, centred on the middle slot.
 * {@code icon} is a vanilla item id such as {@code comparator}; {@code fallbackIcon} is used when the platform has no
 * item by that id.
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
    Map<SettingsClick, SettingsAction> actions
) {
  public SettingsTile {
    lore = lore == null ? List.of() : List.copyOf(lore);
    actions = actions == null ? Map.of() : Map.copyOf(actions);
  }

  public int slot() {
    return (row * 9) + position + 4;
  }

  public SettingsAction actionFor(SettingsClick click) {
    return actions.get(click);
  }
}
