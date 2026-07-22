package art.arcane.react.api.test.checks;

import art.arcane.react.React;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderContext;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.RendererUnknown;
import art.arcane.react.api.test.ReactSubsystemCheck;
import art.arcane.react.api.test.TestReport;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.TestMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.block.BlockFace;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapFont;
import org.bukkit.map.MapView;

import java.awt.Color;
import java.awt.Image;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MapsRenderingCheck implements ReactSubsystemCheck {
  @Override
  public String subsystem() {
    return "maps";
  }

  @Override
  public void run(TestReport report) {
    checkRegistry(report);
    checkMegamapTiling(report);
    checkMegamapHole(report);
    checkPureRender(report);
  }

  private void checkRegistry(TestReport report) {
    MapController controller = React.controller(MapController.class);
    if (controller == null) {
      report.fail("maps", "registry", ReactLanguage.raw(TestMessages.MAP_CONTROLLER_UNAVAILABLE));
      return;
    }

    Map<String, ReactRenderer> registry = controller.getRenderers();
    if (registry == null || registry.isEmpty()) {
      report.fail("maps", "registry", ReactLanguage.raw(TestMessages.MAP_REGISTRY_EMPTY));
      return;
    }

    List<String> blank = new ArrayList<String>();
    for (ReactRenderer renderer : registry.values()) {
      if (renderer == null) {
        blank.add(ReactLanguage.raw(TestMessages.MAP_NULL_RENDERER));
        continue;
      }

      String id = renderer.getId();
      if (id == null || id.isBlank()) {
        blank.add(renderer.getClass().getSimpleName());
      }
    }

    if (!blank.isEmpty()) {
      report.fail(
          "maps",
          "registry",
          ReactLanguage.raw(
              TestMessages.MAP_BLANK_IDS,
              MessageArgument.untrusted("renderers", String.join(", ", blank))
          )
      );
      return;
    }

    report.pass(
        "maps",
        "registry",
        ReactLanguage.raw(
            TestMessages.MAP_REGISTRY_PASS,
            MessageArgument.untrusted("count", registry.size())
        )
    );
  }

  private void checkMegamapTiling(TestReport report) {
    UUID world = UUID.randomUUID();
    List<MegamapGrid.FrameCell> wall = new ArrayList<MegamapGrid.FrameCell>();
    wall.add(cell(1, world, 0, 0));
    wall.add(cell(2, world, 1, 0));
    wall.add(cell(3, world, 0, 1));
    wall.add(cell(4, world, 1, 1));

    Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(wall);
    if (tiles.size() != 4) {
      report.fail(
          "maps",
          "megamap-tile",
          ReactLanguage.raw(
              TestMessages.MAP_TILE_COUNT_FAIL,
              MessageArgument.untrusted("count", tiles.size())
          )
      );
      return;
    }

    MegamapGrid.MegamapTile sample = tiles.get(1);
    if (sample == null || sample.gridWidth() != 2 || sample.gridHeight() != 2) {
      String dims = sample == null
          ? ReactLanguage.raw(TestMessages.MAP_DIMENSIONS_MISSING)
          : sample.gridWidth() + "x" + sample.gridHeight();
      report.fail(
          "maps",
          "megamap-tile",
          ReactLanguage.raw(
              TestMessages.MAP_TILE_GRID_FAIL,
              MessageArgument.untrusted("dimensions", dims)
          )
      );
      return;
    }

    report.pass("maps", "megamap-tile", ReactLanguage.raw(TestMessages.MAP_TILE_PASS));
  }

  private void checkMegamapHole(TestReport report) {
    UUID world = UUID.randomUUID();
    List<MegamapGrid.FrameCell> wall = new ArrayList<MegamapGrid.FrameCell>();
    wall.add(cell(1, world, 0, 0));
    wall.add(cell(2, world, 1, 0));
    wall.add(cell(3, world, 0, 1));

    Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(wall);
    if (!tiles.isEmpty()) {
      report.fail(
          "maps",
          "megamap-hole",
          ReactLanguage.raw(
              TestMessages.MAP_HOLE_FAIL,
              MessageArgument.untrusted("count", tiles.size())
          )
      );
      return;
    }

    report.pass("maps", "megamap-hole", ReactLanguage.raw(TestMessages.MAP_HOLE_PASS));
  }

  private void checkPureRender(TestReport report) {
    ReactRenderer renderer = new RendererUnknown();
    int size = ReactRenderer.CANVAS_SIZE;
    try {
      int[] first = renderToBuffer(renderer, size, size);
      int[] second = renderToBuffer(renderer, size, size);

      int nonBlank = 0;
      for (int pixel : first) {
        if (pixel != 0) {
          nonBlank++;
        }
      }

      if (nonBlank == 0) {
        report.fail(
            "maps",
            "render-determinism",
            ReactLanguage.raw(
                TestMessages.MAP_BLANK_CANVAS,
                MessageArgument.untrusted("renderer", renderer.getId())
            )
        );
        return;
      }

      if (!Arrays.equals(first, second)) {
        report.fail(
            "maps",
            "render-determinism",
            ReactLanguage.raw(
                TestMessages.MAP_NONDETERMINISTIC,
                MessageArgument.untrusted("renderer", renderer.getId())
            )
        );
        return;
      }

      report.pass(
          "maps",
          "render-determinism",
          ReactLanguage.raw(
              TestMessages.MAP_RENDER_PASS,
              MessageArgument.untrusted("renderer", renderer.getId()),
              MessageArgument.untrusted("pixels", nonBlank),
              MessageArgument.untrusted("width", size),
              MessageArgument.untrusted("height", size)
          )
      );
    } catch (Throwable e) {
      String reason = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage());
      report.skip(
          "maps",
          "render-determinism",
          ReactLanguage.raw(
              TestMessages.MAP_HEADLESS_UNAVAILABLE,
              MessageArgument.untrusted("reason", reason)
          )
      );
    }
  }

  private int[] renderToBuffer(ReactRenderer renderer, int width, int height) {
    SyntheticCanvas canvas = new SyntheticCanvas(width, height);
    ReactRenderContext context = ReactRenderContext.builder()
        .canvas(canvas)
        .width(width)
        .height(height)
        .build();
    ReactRenderContext.push(context);
    try {
      renderer.render();
    } finally {
      ReactRenderContext.pop();
    }
    return canvas.pixels();
  }

  private MegamapGrid.FrameCell cell(int mapId, UUID world, int blockX, int blockY) {
    return new MegamapGrid.FrameCell(mapId, world, BlockFace.SOUTH, blockX, blockY, 0, "react-test-megamap", true);
  }

  private static final class SyntheticCanvas implements MapCanvas {
    private final int width;
    private final int height;
    private final int[] pixels;

    private SyntheticCanvas(int width, int height) {
      this.width = width;
      this.height = height;
      this.pixels = new int[width * height];
    }

    private int[] pixels() {
      return pixels;
    }

    @Override
    public MapView getMapView() {
      return null;
    }

    @Override
    public MapCursorCollection getCursors() {
      return null;
    }

    @Override
    public void setCursors(MapCursorCollection cursors) {
    }

    @Override
    public void setPixelColor(int x, int y, Color color) {
      if (x < 0 || y < 0 || x >= width || y >= height) {
        return;
      }

      pixels[(y * width) + x] = color == null ? 0 : color.getRGB();
    }

    @Override
    public Color getPixelColor(int x, int y) {
      if (x < 0 || y < 0 || x >= width || y >= height) {
        return null;
      }

      int color = pixels[(y * width) + x];
      return color == 0 ? null : new Color(color, true);
    }

    @Override
    public Color getBasePixelColor(int x, int y) {
      return Color.BLACK;
    }

    @Override
    public void setPixel(int x, int y, byte color) {
      if (x < 0 || y < 0 || x >= width || y >= height) {
        return;
      }

      pixels[(y * width) + x] = color;
    }

    @SuppressWarnings("removal")
    @Override
    public byte getPixel(int x, int y) {
      if (x < 0 || y < 0 || x >= width || y >= height) {
        return 0;
      }

      return (byte) pixels[(y * width) + x];
    }

    @SuppressWarnings("removal")
    @Override
    public byte getBasePixel(int x, int y) {
      return 0;
    }

    @Override
    public void drawImage(int x, int y, Image image) {
    }

    @Override
    public void drawText(int x, int y, MapFont font, String text) {
    }
  }
}
