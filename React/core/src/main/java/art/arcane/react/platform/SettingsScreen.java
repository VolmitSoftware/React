package art.arcane.react.platform;

import java.util.List;

public record SettingsScreen(String title, int rows, String background, List<SettingsTile> tiles) {
  public SettingsScreen {
    tiles = tiles == null ? List.of() : List.copyOf(tiles);
  }
}
