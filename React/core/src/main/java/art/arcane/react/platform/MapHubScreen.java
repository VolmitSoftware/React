package art.arcane.react.platform;

import java.util.List;

/**
 * A chest menu of {@code rows} rows (1..6). Empty slots show {@code background}.
 */
public record MapHubScreen(String title, int rows, MapHubIcon background, List<MapHubTile> tiles) {
  public MapHubScreen {
    tiles = tiles == null ? List.of() : List.copyOf(tiles);
  }
}
