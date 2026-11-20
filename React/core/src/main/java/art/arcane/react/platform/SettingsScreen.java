package art.arcane.react.platform;

import java.util.List;

/**
 * A menu screen. {@code columns} is 9 for a chest of {@code rows} rows, or 3 for a 3x3 dropper.
 */
public record SettingsScreen(String title, int rows, String background, List<SettingsTile> tiles, int columns) {
  public static final int CHEST_COLUMNS = 9;
  public static final int DROPPER_COLUMNS = 3;

  public SettingsScreen {
    tiles = tiles == null ? List.of() : List.copyOf(tiles);
    columns = columns == DROPPER_COLUMNS ? DROPPER_COLUMNS : CHEST_COLUMNS;
    if (columns == DROPPER_COLUMNS) {
      rows = 3;
    }
  }

  public SettingsScreen(String title, int rows, String background, List<SettingsTile> tiles) {
    this(title, rows, background, tiles, CHEST_COLUMNS);
  }

  public boolean dropper() {
    return columns == DROPPER_COLUMNS;
  }
}
