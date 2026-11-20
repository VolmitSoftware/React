package art.arcane.react.platform;

import java.util.UUID;

/**
 * Shows React's settings menu as a chest menu. Item movement is blocked; a left click fires a tick later and a
 * double click is dropped, as on Bukkit. Clicks on a tile with an action for the click reach the handler on the
 * viewer's owner thread.
 */
public interface SettingsScreenHost {
  /**
   * Opens the screen, reusing the viewer's open settings menu when it has the same size and title.
   */
  void open(UUID viewerId, SettingsScreen screen, ClickHandler handler);

  void close(UUID viewerId);

  void playPageTurn(UUID viewerId);

  @FunctionalInterface
  interface ClickHandler {
    void onClick(UUID viewerId, SettingsTile tile, SettingsClick click, SettingsAction action);
  }
}
