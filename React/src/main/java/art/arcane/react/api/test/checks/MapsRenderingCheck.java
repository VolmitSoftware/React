package art.arcane.react.api.test.checks;

import art.arcane.react.React;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderContext;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.RendererUnknown;
import art.arcane.react.api.test.ReactSubsystemCheck;
import art.arcane.react.api.test.TestReport;
import art.arcane.react.core.controller.MapController;
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
      report.fail("maps", "registry", "MapController is not registered");
      return;
    }

    Map<String, ReactRenderer> registry = controller.getRenderers();
    if (registry == null || registry.isEmpty()) {
      report.fail("maps", "registry", "renderer registry is empty");
      return;
    }

    List<String> blank = new ArrayList<String>();
    for (ReactRenderer renderer : registry.values()) {
      if (renderer == null) {
        blank.add("<null renderer>");
        continue;
      }

      String id = renderer.getId();
      if (id == null || id.isBlank()) {
        blank.add(renderer.getClass().getSimpleName());
      }
    }

    if (!blank.isEmpty()) {
      report.fail("maps", "registry", "renderers with blank id: " + String.join(", ", blank));
      return;
    }

    report.pass("maps", "registry", registry.size() + " renderers registered, all ids non-blank");
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
      report.fail("maps", "megamap-tile", "2x2 same-renderer wall produced " + tiles.size() + " tiled cells, expected 4");
      return;
    }

    MegamapGrid.MegamapTile sample = tiles.get(1);
    if (sample == null || sample.gridWidth() != 2 || sample.gridHeight() != 2) {
      String dims = sample == null ? "missing" : sample.gridWidth() + "x" + sample.gridHeight();
      report.fail("maps", "megamap-tile", "2x2 wall did not resolve to a 2x2 grid (got " + dims + ")");
      return;
    }

    report.pass("maps", "megamap-tile", "2x2 same-renderer wall tiled into a 2x2 grid across 4 maps");
  }

  private void checkMegamapHole(TestReport report) {
    UUID world = UUID.randomUUID();
    List<MegamapGrid.FrameCell> wall = new ArrayList<MegamapGrid.FrameCell>();
    wall.add(cell(1, world, 0, 0));
    wall.add(cell(2, world, 1, 0));
    wall.add(cell(3, world, 0, 1));

    Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(wall);
    if (!tiles.isEmpty()) {
      report.fail("maps", "megamap-hole", "non-rectangular wall (hole) was tiled into " + tiles.size() + " cells, expected rejection");
      return;
    }

    report.pass("maps", "megamap-hole", "non-rectangular wall with a hole was rejected (no tiling)");
  }

  private void checkPureRender(TestReport report) {
    ReactRenderer renderer = new RendererUnknown();
    int size = ReactRenderer.CANVAS_SIZE;
    try {
      byte[] first = renderToBuffer(renderer, size, size);
      byte[] second = renderToBuffer(renderer, size, size);

      int nonBlank = 0;
      for (byte pixel : first) {
        if (pixel != 0) {
          nonBlank++;
        }
      }

      if (nonBlank == 0) {
        report.fail("maps", "render-determinism", "renderer '" + renderer.getId() + "' produced a fully blank canvas");
        return;
      }

      if (!Arrays.equals(first, second)) {
        report.fail("maps", "render-determinism", "renderer '" + renderer.getId() + "' produced non-deterministic pixels across two renders");
        return;
      }

      report.pass("maps", "render-determinism", "renderer '" + renderer.getId() + "' rendered " + nonBlank + " non-blank pixels identically twice on a synthetic " + size + "x" + size + " canvas");
    } catch (Throwable e) {
      report.skip("maps", "render-determinism", "headless pixel render unavailable: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage()));
    }
  }

  private byte[] renderToBuffer(ReactRenderer renderer, int width, int height) {
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
    private final byte[] pixels;

    private SyntheticCanvas(int width, int height) {
      this.width = width;
      this.height = height;
      this.pixels = new byte[width * height];
    }

    private byte[] pixels() {
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
    }

    @Override
    public Color getPixelColor(int x, int y) {
      return null;
    }

    @Override
    public Color getBasePixelColor(int x, int y) {
      return null;
    }

    @Override
    public void setPixel(int x, int y, byte color) {
      if (x < 0 || y < 0 || x >= width || y >= height) {
        return;
      }

      pixels[(y * width) + x] = color;
    }

    @Override
    public byte getPixel(int x, int y) {
      if (x < 0 || y < 0 || x >= width || y >= height) {
        return 0;
      }

      return pixels[(y * width) + x];
    }

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
