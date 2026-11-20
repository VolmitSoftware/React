package art.arcane.react.platform;

import java.util.UUID;

public interface MapHubScreenHost {
  /**
   * Open or replace the viewer's hub menu. Item movement is blocked; clicks on a tile with an action for the click
   * kind reach the handler on the viewer's owner thread.
   */
  void open(UUID viewerId, MapHubScreen screen, ClickHandler handler);

  void close(UUID viewerId);

  void playPageTurn(UUID viewerId);

  @FunctionalInterface
  interface ClickHandler {
    void onClick(UUID viewerId, MapHubTile tile, MapHubClick click, MapHubAction action);
  }
}
