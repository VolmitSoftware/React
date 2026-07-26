package art.arcane.react.api.test.checks;

import art.arcane.react.React;
import art.arcane.react.api.rendering.MapColors;
import art.arcane.react.api.rendering.MapTheme;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderContext;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.Region;
import art.arcane.react.api.rendering.RendererUnknown;
import art.arcane.react.api.test.ReactSubsystemCheck;
import art.arcane.react.api.test.TestReport;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.TestMessages;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.block.BlockFace;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapFont;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapView;

import java.awt.Color;
import java.awt.Image;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MapsRenderingCheck implements ReactSubsystemCheck {
  private static final Region PROBE_REGION = new Region(19, 23, 41, 37);
  private static final String OVERFLOW_TEXT =
      "OverflowProbe 0123456789 abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ "
          + "éüñß Привет 世界 안녕";

  @Override
  public String subsystem() {
    return "maps";
  }

  @Override
  public void run(TestReport report) {
    checkRegistry(report);
    checkMegamapTiling(report);
    checkMegamapHole(report);
    checkMegamapCapacity(report);
    checkMegamapViewport(report);
    checkMegamapDefects(report);
    checkMegamapExpansion(report);
    checkPureRender(report);
    checkPalette(report);
    checkClipContainment(report);
    checkTextBounds(report);
    checkRendererContainment(report);
    checkFlushFidelity(report);
    checkFlushStability(report);
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

  private void checkMegamapCapacity(TestReport report) {
    MegamapGrid.MegamapCapability magnify = MegamapGrid.MegamapCapability.magnify();
    MegamapGrid.MegamapCapability adaptive = MegamapGrid.MegamapCapability.adaptive(4, 4);
    MegamapGrid.MegamapCapability wideOnly = MegamapGrid.MegamapCapability.adaptive(2, 1, 4, 4);
    List<String> problems = new ArrayList<String>();

    if (magnify.adaptsTo(3, 1) || magnify.adaptive()) {
      problems.add("magnify capability reported adaptive");
    }
    if (!adaptive.adaptsTo(3, 1) || !adaptive.adaptsTo(4, 4)) {
      problems.add("adaptive capability rejected a grid inside its bounds");
    }
    if (adaptive.adaptsTo(5, 4) || adaptive.adaptsTo(4, 5)) {
      problems.add("adaptive capability accepted a grid past its maximum");
    }
    if (wideOnly.adaptsTo(1, 1) || !wideOnly.adaptsTo(2, 1)) {
      problems.add("minimum grid bound is not enforced");
    }
    if (adaptive.contentColumns(1) != 1 || adaptive.contentColumns(3) != 3 || adaptive.contentColumns(9) != 4) {
      problems.add("contentColumns did not clamp to the capability maximum");
    }
    if (adaptive.contentRows(1) != 1 || adaptive.contentRows(2) != 2 || adaptive.contentRows(9) != 4) {
      problems.add("contentRows did not clamp to the capability maximum");
    }
    if (adaptive.detailFor(1, 1) != MegamapGrid.MegamapDetail.COMPACT
        || adaptive.detailFor(3, 1) != MegamapGrid.MegamapDetail.EXPANDED
        || adaptive.detailFor(2, 2) != MegamapGrid.MegamapDetail.RICH
        || adaptive.detailFor(3, 3) != MegamapGrid.MegamapDetail.MAXIMAL) {
      problems.add("detail tier did not scale with grid area");
    }
    if (magnify.detailFor(3, 3) != MegamapGrid.MegamapDetail.COMPACT) {
      problems.add("magnify capability reported an expanded detail tier");
    }
    if (!MegamapGrid.MegamapDetail.RICH.atLeast(MegamapGrid.MegamapDetail.EXPANDED)
        || MegamapGrid.MegamapDetail.COMPACT.atLeast(MegamapGrid.MegamapDetail.RICH)) {
      problems.add("detail tier ordering is wrong");
    }

    if (!problems.isEmpty()) {
      report.fail(
          "maps",
          "megamap-capacity",
          ReactLanguage.raw("test.map.megamap_capacity_fail", "Megamap capability derivation is wrong:")
              + " " + String.join("; ", problems)
      );
      return;
    }

    report.pass(
        "maps",
        "megamap-capacity",
        ReactLanguage.raw("test.map.megamap_capacity_pass", "Megamap capabilities derive columns, rows and detail from the grid.")
    );
  }

  private void checkMegamapViewport(TestReport report) {
    int size = ReactRenderer.CANVAS_SIZE;
    MegamapGrid.MegamapCapability adaptive = MegamapGrid.MegamapCapability.adaptive(4, 4);
    MegamapGrid.MegamapCapability magnify = MegamapGrid.MegamapCapability.magnify();
    List<String> problems = new ArrayList<String>();

    MegamapGrid.MegamapViewport wide = MegamapGrid.viewportFor(
        new MegamapGrid.MegamapTile(3, 1, 2, 0), adaptive, 16, size);
    if (!wide.adaptive() || wide.width() != 3 * size || wide.height() != size || wide.offsetX() != 2 * size) {
      problems.add("adaptive 3x1 did not receive a 384x128 logical canvas");
    }

    MegamapGrid.MegamapViewport capped = MegamapGrid.viewportFor(
        new MegamapGrid.MegamapTile(5, 5, 0, 0), adaptive, 16, size);
    if (capped.adaptive() || !capped.capped()) {
      problems.add("an oversized wall was not capped back to magnification");
    }

    int covered = 0;
    for (int tileX = 0; tileX < 3; tileX++) {
      MegamapGrid.MegamapViewport viewport = MegamapGrid.viewportFor(
          new MegamapGrid.MegamapTile(3, 1, tileX, 0), magnify, 16, size);
      if (viewport.adaptive() || viewport.scale() != 1 || viewport.width() != size) {
        problems.add("magnified 3x1 tile " + tileX + " was not uniformly scaled");
      }
      if (viewport.contentX() != size - (tileX * size)) {
        problems.add("magnified 3x1 tile " + tileX + " was not centred");
      }
      if (viewport.fullyCovers(size)) {
        covered++;
      }
    }
    if (covered != 1) {
      problems.add("expected exactly one fully covered frame on a magnified 3x1 wall, saw " + covered);
    }

    for (int tileY = 0; tileY < 2; tileY++) {
      for (int tileX = 0; tileX < 2; tileX++) {
        MegamapGrid.MegamapViewport viewport = MegamapGrid.viewportFor(
            new MegamapGrid.MegamapTile(2, 2, tileX, tileY), magnify, 16, size);
        if (viewport.scale() != 2 || !viewport.fullyCovers(size)) {
          problems.add("magnified 2x2 tile " + tileX + "," + tileY + " left uncovered pixels");
        }
      }
    }

    if (!problems.isEmpty()) {
      report.fail(
          "maps",
          "megamap-viewport",
          ReactLanguage.raw("test.map.megamap_viewport_fail", "Megamap viewport geometry is wrong:")
              + " " + String.join("; ", problems)
      );
      return;
    }

    report.pass(
        "maps",
        "megamap-viewport",
        ReactLanguage.raw("test.map.megamap_viewport_pass", "Adaptive walls get one logical canvas and magnified walls stay uniform and letterboxed.")
    );
  }

  private void checkMegamapDefects(TestReport report) {
    UUID world = UUID.randomUUID();
    List<String> problems = new ArrayList<String>();

    List<MegamapGrid.FrameCell> ragged = new ArrayList<MegamapGrid.FrameCell>();
    ragged.add(cell(1, world, 0, 0));
    ragged.add(cell(2, world, 1, 0));
    ragged.add(cell(3, world, 0, 1));
    MegamapGrid.MegamapSolution raggedSolution = MegamapGrid.analyze(ragged);
    if (!raggedSolution.tiles().isEmpty()) {
      problems.add("an L-shaped wall produced tiles");
    }
    if (defectReason(raggedSolution, 1) != MegamapGrid.DefectReason.HOLE
        || defectReason(raggedSolution, 3) != MegamapGrid.DefectReason.HOLE) {
      problems.add("an L-shaped wall did not report a hole");
    }

    List<MegamapGrid.FrameCell> duplicates = new ArrayList<MegamapGrid.FrameCell>();
    duplicates.add(cell(7, world, 0, 0));
    duplicates.add(cell(7, world, 1, 0));
    if (defectReason(MegamapGrid.analyze(duplicates), 7) != MegamapGrid.DefectReason.DUPLICATE_MAP_ID) {
      problems.add("duplicate map ids were not reported");
    }

    List<MegamapGrid.FrameCell> rotated = new ArrayList<MegamapGrid.FrameCell>();
    rotated.add(cell(1, world, 0, 0));
    rotated.add(cell(2, world, 1, 0));
    rotated.add(new MegamapGrid.FrameCell(3, world, BlockFace.SOUTH, 2, 0, 0, "react-test-megamap", false));
    MegamapGrid.MegamapSolution rotatedSolution = MegamapGrid.analyze(rotated);
    if (rotatedSolution.tiles().size() != 2) {
      problems.add("a rotated frame was not excluded from the wall");
    }
    if (defectReason(rotatedSolution, 3) != MegamapGrid.DefectReason.ROTATED) {
      problems.add("a rotated frame was not reported");
    }

    List<MegamapGrid.FrameCell> mixed = new ArrayList<MegamapGrid.FrameCell>();
    mixed.add(new MegamapGrid.FrameCell(1, world, BlockFace.SOUTH, 0, 0, 0, "react-test-megamap", true));
    mixed.add(new MegamapGrid.FrameCell(2, world, BlockFace.SOUTH, 1, 0, 0, "react-test-other", true));
    MegamapGrid.MegamapSolution mixedSolution = MegamapGrid.analyze(mixed);
    if (defectReason(mixedSolution, 1) != MegamapGrid.DefectReason.MIXED_RENDERER
        || defectReason(mixedSolution, 2) != MegamapGrid.DefectReason.MIXED_RENDERER) {
      problems.add("neighbouring frames with different maps were not reported");
    }

    List<MegamapGrid.FrameCell> lone = new ArrayList<MegamapGrid.FrameCell>();
    lone.add(cell(9, world, 0, 0));
    if (!MegamapGrid.analyze(lone).isEmpty()) {
      problems.add("a single isolated frame produced tiles or defects");
    }

    if (!problems.isEmpty()) {
      report.fail(
          "maps",
          "megamap-defects",
          ReactLanguage.raw("test.map.megamap_defects_fail", "Broken map walls were not diagnosed:")
              + " " + String.join("; ", problems)
      );
      return;
    }

    report.pass(
        "maps",
        "megamap-defects",
        ReactLanguage.raw("test.map.megamap_defects_pass", "Ragged, rotated, duplicated and mixed map walls all report a reason.")
    );
  }

  private void checkMegamapExpansion(TestReport report) {
    MapController controller = React.controller(MapController.class);
    if (controller == null) {
      report.skip("maps", "megamap-expansion", ReactLanguage.raw(TestMessages.MAP_CONTROLLER_UNAVAILABLE));
      return;
    }

    Map<String, ReactRenderer> registry = controller.getRenderers();
    if (registry == null || registry.isEmpty()) {
      report.skip("maps", "megamap-expansion", ReactLanguage.raw(TestMessages.MAP_REGISTRY_EMPTY));
      return;
    }

    int[][] grids = {{1, 1}, {2, 1}, {1, 2}, {1, 3}, {2, 2}, {3, 1}, {3, 3}};
    int rendered = 0;
    int unavailable = 0;
    List<String> problems = new ArrayList<String>();
    for (ReactRenderer renderer : registry.values()) {
      if (renderer == null) {
        continue;
      }

      boolean exercised = false;
      for (int[] grid : grids) {
        try {
          if (!renderMegamapWall(renderer, grid[0], grid[1])) {
            problems.add(renderer.getId() + " left a blank frame at " + grid[0] + "x" + grid[1]);
          }
          exercised = true;
        } catch (Throwable e) {
          unavailable++;
          exercised = false;
          break;
        }
      }

      if (exercised) {
        rendered++;
      }
    }

    if (!problems.isEmpty()) {
      report.fail(
          "maps",
          "megamap-expansion",
          ReactLanguage.raw("test.map.megamap_expansion_fail", "Renderers failed on an expanded wall:")
              + " " + String.join(", ", problems)
      );
      return;
    }

    if (rendered == 0) {
      report.skip(
          "maps",
          "megamap-expansion",
          ReactLanguage.raw("test.map.renderer_containment_skip", "No renderer could be exercised headlessly.")
      );
      return;
    }

    report.pass(
        "maps",
        "megamap-expansion",
        ReactLanguage.raw("test.map.megamap_expansion_pass", "Every renderer fills every frame of a 1x1, 2x1, 1x2, 1x3, 2x2, 3x1 and 3x3 wall.")
            + " (" + rendered + " rendered, " + unavailable + " unavailable)"
    );
  }

  private boolean renderMegamapWall(ReactRenderer renderer, int gridWidth, int gridHeight) {
    int size = ReactRenderer.CANVAS_SIZE;
    MegamapGrid.MegamapCapability capability = renderer.megamapCapability();
    for (int tileY = 0; tileY < gridHeight; tileY++) {
      for (int tileX = 0; tileX < gridWidth; tileX++) {
        MegamapGrid.MegamapTile tile = new MegamapGrid.MegamapTile(gridWidth, gridHeight, tileX, tileY);
        MegamapGrid.MegamapViewport viewport = MegamapGrid.viewportFor(tile, capability, 16, size);
        SyntheticCanvas canvas = new SyntheticCanvas(size, size);
        ReactRenderContext context = ReactRenderContext.builder()
            .canvas(canvas)
            .width(viewport.width())
            .height(viewport.height())
            .offsetX(viewport.offsetX())
            .offsetY(viewport.offsetY())
            .scaleX(viewport.scale())
            .scaleY(viewport.scale())
            .textScale(viewport.textScale())
            .gridWidth(gridWidth)
            .gridHeight(gridHeight)
            .build();

        ReactRenderContext.push(context);
        try {
          renderer.render();
        } finally {
          context.flush();
          ReactRenderContext.pop();
        }

        if (canvas.outOfBounds() > 0) {
          return false;
        }

        if (viewport.coversAnything(size) && inkInside(canvas.pixels(), size, new Region(0, 0, size, size)) == 0) {
          return false;
        }
      }
    }

    return true;
  }

  private MegamapGrid.DefectReason defectReason(MegamapGrid.MegamapSolution solution, int mapId) {
    MegamapGrid.MegamapDefect defect = solution.defectFor(mapId);
    return defect == null ? null : defect.reason();
  }

  private void checkPureRender(TestReport report) {
    ReactRenderer renderer = new RendererUnknown();
    int size = ReactRenderer.CANVAS_SIZE;
    try {
      SyntheticCanvas first = renderToCanvas(renderer, size, size, 1, null);
      SyntheticCanvas second = renderToCanvas(renderer, size, size, 1, null);

      int nonBlank = 0;
      for (int pixel : first.pixels()) {
        if (pixel != SyntheticCanvas.BLANK) {
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

      if (!Arrays.equals(first.pixels(), second.pixels())) {
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

  private void checkPalette(TestReport report) {
    List<String> problems = new ArrayList<String>();
    Map<Integer, String> byIndex = new HashMap<Integer, String>();
    for (TinyColor token : MapTheme.TOKENS) {
      int rgb = token.toRGB() & 0xFFFFFF;
      int index = MapColors.indexFor(rgb) & 0xFF;
      if ((MapColors.colorFor(rgb).getRGB() & 0xFFFFFF) != rgb) {
        problems.add(token.toHex() + " is not a palette entry");
      }

      String previous = byIndex.put(index, token.toHex());
      if (previous != null) {
        problems.add(token.toHex() + " collides with " + previous + " at palette index " + index);
      }
    }

    problems.addAll(rampCollisions("HEAT_RAMP", MapTheme.HEAT_RAMP));
    problems.addAll(rampCollisions("CATEGORICAL", MapTheme.CATEGORICAL));

    if (!problems.isEmpty()) {
      report.fail(
          "maps",
          "palette-distinct",
          ReactLanguage.raw("test.map.palette_fail", "Theme colors collapse on the map palette:")
              + " " + String.join("; ", problems)
      );
      return;
    }

    report.pass(
        "maps",
        "palette-distinct",
        ReactLanguage.raw("test.map.palette_pass", "Every theme color is an exact, distinct map palette entry.")
            + " (" + MapTheme.TOKENS.length + ")"
    );
  }

  private void checkClipContainment(TestReport report) {
    int size = ReactRenderer.CANVAS_SIZE;
    try {
      SyntheticCanvas clipped = renderToCanvas(new ClipProbeRenderer(), size, size, 1, PROBE_REGION);
      int escaped = inkEscaped(clipped.pixels(), size, PROBE_REGION);
      int inked = inkInside(clipped.pixels(), size, PROBE_REGION);

      if (escaped > 0 || clipped.outOfBounds() > 0) {
        report.fail(
            "maps",
            "clip-containment",
            ReactLanguage.raw("test.map.clip_fail", "Drawing escaped the active clip rect.")
                + " (" + escaped + " stray pixels, " + clipped.outOfBounds() + " off-canvas writes)"
        );
        return;
      }

      if (inked == 0) {
        report.fail(
            "maps",
            "clip-containment",
            ReactLanguage.raw("test.map.clip_blank", "The clip probe drew nothing inside its region.")
        );
        return;
      }

      SyntheticCanvas restored = renderToCanvas(new ClipRestoreProbeRenderer(), size, size, 1, null);
      if (restored.pixels()[0] == SyntheticCanvas.BLANK) {
        report.fail(
            "maps",
            "clip-containment",
            ReactLanguage.raw("test.map.clip_restore_fail", "popClip did not restore the previous clip rect.")
        );
        return;
      }

      report.pass(
          "maps",
          "clip-containment",
          ReactLanguage.raw("test.map.clip_pass", "Lines, text and fills stayed inside the pushed clip rect.")
              + " (" + inked + " pixels)"
      );
    } catch (Throwable e) {
      String reason = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage());
      report.skip(
          "maps",
          "clip-containment",
          ReactLanguage.raw(
              TestMessages.MAP_HEADLESS_UNAVAILABLE,
              MessageArgument.untrusted("reason", reason)
          )
      );
    }
  }

  private void checkTextBounds(TestReport report) {
    int size = ReactRenderer.CANVAS_SIZE;
    List<String> problems = new ArrayList<String>();
    try {
      for (int scale = 1; scale <= 3; scale++) {
        TextProbeRenderer probe = new TextProbeRenderer();
        renderToCanvas(probe, size, size, scale, null);
        problems.addAll(probe.problems());
      }
    } catch (Throwable e) {
      String reason = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage());
      report.skip(
          "maps",
          "text-bounds",
          ReactLanguage.raw(
              TestMessages.MAP_HEADLESS_UNAVAILABLE,
              MessageArgument.untrusted("reason", reason)
          )
      );
      return;
    }

    if (!problems.isEmpty()) {
      report.fail(
          "maps",
          "text-bounds",
          ReactLanguage.raw("test.map.text_bounds_fail", "Fitted text exceeded its width limit.")
              + " " + String.join("; ", problems)
      );
      return;
    }

    report.pass(
        "maps",
        "text-bounds",
        ReactLanguage.raw("test.map.text_bounds_pass", "Fitted text never exceeds its limit at any ui scale.")
    );
  }

  private void checkRendererContainment(TestReport report) {
    MapController controller = React.controller(MapController.class);
    if (controller == null) {
      report.skip("maps", "renderer-containment", ReactLanguage.raw(TestMessages.MAP_CONTROLLER_UNAVAILABLE));
      return;
    }

    Map<String, ReactRenderer> registry = controller.getRenderers();
    if (registry == null || registry.isEmpty()) {
      report.skip("maps", "renderer-containment", ReactLanguage.raw(TestMessages.MAP_REGISTRY_EMPTY));
      return;
    }

    int size = ReactRenderer.CANVAS_SIZE;
    int rendered = 0;
    int failed = 0;
    List<String> escapedIds = new ArrayList<String>();
    for (ReactRenderer renderer : registry.values()) {
      if (renderer == null) {
        continue;
      }

      SyntheticCanvas canvas;
      try {
        canvas = renderToCanvas(renderer, size, size, 1, PROBE_REGION);
      } catch (Throwable e) {
        failed++;
        continue;
      }

      rendered++;
      if (canvas.outOfBounds() > 0 || inkEscaped(canvas.pixels(), size, PROBE_REGION) > 0) {
        escapedIds.add(renderer.getId());
      }
    }

    if (!escapedIds.isEmpty()) {
      report.fail(
          "maps",
          "renderer-containment",
          ReactLanguage.raw("test.map.renderer_containment_fail", "Renderers drew outside their clip rect:")
              + " " + String.join(", ", escapedIds)
      );
      return;
    }

    if (rendered == 0) {
      report.skip(
          "maps",
          "renderer-containment",
          ReactLanguage.raw("test.map.renderer_containment_skip", "No renderer could be exercised headlessly.")
      );
      return;
    }

    report.pass(
        "maps",
        "renderer-containment",
        ReactLanguage.raw("test.map.renderer_containment_pass", "Every renderer stayed inside the pushed clip rect.")
            + " (" + rendered + " rendered, " + failed + " unavailable)"
    );
  }

  private int inkEscaped(int[] pixels, int size, Region region) {
    int escaped = 0;
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        if (pixels[(y * size) + x] != SyntheticCanvas.BLANK && !region.contains(x, y)) {
          escaped++;
        }
      }
    }
    return escaped;
  }

  private int inkInside(int[] pixels, int size, Region region) {
    int inked = 0;
    for (int y = region.y(); y < region.bottom() && y < size; y++) {
      for (int x = region.x(); x < region.right() && x < size; x++) {
        if (y >= 0 && x >= 0 && pixels[(y * size) + x] != SyntheticCanvas.BLANK) {
          inked++;
        }
      }
    }
    return inked;
  }

  private List<String> rampCollisions(String name, TinyColor[] ramp) {
    List<String> problems = new ArrayList<String>();
    Map<Integer, String> byIndex = new HashMap<Integer, String>();
    for (TinyColor color : ramp) {
      int index = MapColors.indexFor(color.toRGB()) & 0xFF;
      String previous = byIndex.put(index, color.toHex());
      if (previous != null) {
        problems.add(name + " " + color.toHex() + " collides with " + previous);
      }
    }
    return problems;
  }

  private SyntheticCanvas renderToCanvas(ReactRenderer renderer, int width, int height, int textScale, Region clip) {
    SyntheticCanvas canvas = new SyntheticCanvas(width, height);
    ReactRenderContext context = ReactRenderContext.builder()
        .canvas(canvas)
        .width(width)
        .height(height)
        .textScale(textScale)
        .build();
    if (clip != null) {
      context.pushClip(clip.x(), clip.y(), clip.width(), clip.height());
    }

    ReactRenderContext.push(context);
    try {
      renderer.render();
    } finally {
      context.flush();
      ReactRenderContext.pop();
    }
    return canvas;
  }

  private void checkFlushFidelity(TestReport report) {
    List<ReactRenderer> renderers = probeRenderers();
    MapController controller = React.controller(MapController.class);
    if (controller != null && controller.getRenderers() != null) {
      renderers.addAll(controller.getRenderers().values());
    }

    int size = ReactRenderer.CANVAS_SIZE;
    int[][] grids = {{1, 1}, {2, 2}, {3, 3}};
    int verified = 0;
    int unavailable = 0;
    List<String> problems = new ArrayList<String>();
    for (ReactRenderer renderer : renderers) {
      if (renderer == null) {
        continue;
      }

      MegamapGrid.MegamapCapability capability = renderer.megamapCapability();
      wall:
      for (int[] grid : grids) {
        for (int tileY = 0; tileY < grid[1]; tileY++) {
          for (int tileX = 0; tileX < grid[0]; tileX++) {
            MegamapGrid.MegamapTile tile = new MegamapGrid.MegamapTile(grid[0], grid[1], tileX, tileY);
            MegamapGrid.MegamapViewport viewport = MegamapGrid.viewportFor(tile, capability, 16, size);
            SyntheticCanvas canvas = new SyntheticCanvas(size, size);
            ReactRenderContext context = viewportContext(canvas, tile, viewport, null, null, false);

            boolean drew = false;
            ReactRenderContext.push(context);
            try {
              renderer.render();
              drew = true;
            } catch (Throwable e) {
              unavailable++;
            } finally {
              context.flush();
              ReactRenderContext.pop();
            }

            if (!drew) {
              break wall;
            }

            verified++;
            String mismatch = surfaceMismatch(context, canvas, size);
            if (mismatch != null) {
              problems.add(renderer.getId() + " " + grid[0] + "x" + grid[1] + " " + mismatch);
              break wall;
            }
            if (canvas.outOfBounds() > 0) {
              problems.add(renderer.getId() + " wrote " + canvas.outOfBounds() + " pixels off canvas");
              break wall;
            }
          }
        }
      }
    }

    if (!problems.isEmpty()) {
      report.fail(
          "maps",
          "flush-fidelity",
          ReactLanguage.raw("test.map.flush_fidelity_fail", "The buffered flush did not reproduce the composed surface:")
              + " " + String.join("; ", problems)
      );
      return;
    }

    if (verified == 0) {
      report.skip(
          "maps",
          "flush-fidelity",
          ReactLanguage.raw("test.map.renderer_containment_skip", "No renderer could be exercised headlessly.")
      );
      return;
    }

    report.pass(
        "maps",
        "flush-fidelity",
        ReactLanguage.raw("test.map.flush_fidelity_pass", "Every flushed pixel matches the composed surface at 1x1, 2x2 and 3x3.")
            + " (" + verified + " frames, " + unavailable + " unavailable)"
    );
  }

  private void checkFlushStability(TestReport report) {
    int size = ReactRenderer.CANVAS_SIZE;
    int[][] grids = {{1, 1}, {3, 3}};
    List<String> problems = new ArrayList<String>();
    try {
      for (ReactRenderer renderer : probeRenderers()) {
        for (int[] grid : grids) {
          MegamapGrid.MegamapCapability capability = renderer.megamapCapability();
          for (int tileY = 0; tileY < grid[1]; tileY++) {
            for (int tileX = 0; tileX < grid[0]; tileX++) {
              MegamapGrid.MegamapTile tile = new MegamapGrid.MegamapTile(grid[0], grid[1], tileX, tileY);
              MegamapGrid.MegamapViewport viewport = MegamapGrid.viewportFor(tile, capability, 16, size);
              byte[] surface = ReactRenderContext.newSurface();
              byte[] shadow = ReactRenderContext.newSurface();
              SyntheticCanvas canvas = new SyntheticCanvas(size, size);

              int first = renderFrame(renderer, canvas, tile, viewport, surface, shadow, true);
              int[] afterFirst = canvas.pixels().clone();
              int second = renderFrame(renderer, canvas, tile, viewport, surface, shadow, false);

              String label = renderer.getId() + " " + grid[0] + "x" + grid[1] + " tile " + tileX + "," + tileY;
              if (first <= 0 && viewport.coversAnything(size)) {
                problems.add(label + " drew nothing on its first frame");
              }
              if (second != 0) {
                problems.add(label + " rewrote " + second + " pixels on an unchanged frame");
              }
              if (!Arrays.equals(afterFirst, canvas.pixels())) {
                problems.add(label + " changed the canvas on an unchanged frame");
              }
            }
          }
        }
      }
    } catch (Throwable e) {
      String reason = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage());
      report.skip(
          "maps",
          "flush-stability",
          ReactLanguage.raw(
              TestMessages.MAP_HEADLESS_UNAVAILABLE,
              MessageArgument.untrusted("reason", reason)
          )
      );
      return;
    }

    if (!problems.isEmpty()) {
      report.fail(
          "maps",
          "flush-stability",
          ReactLanguage.raw("test.map.flush_stability_fail", "Repainting identical content still wrote to the canvas:")
              + " " + String.join("; ", problems)
      );
      return;
    }

    report.pass(
        "maps",
        "flush-stability",
        ReactLanguage.raw("test.map.flush_stability_pass", "Repainting identical content writes zero canvas pixels.")
    );
  }

  private int renderFrame(
      ReactRenderer renderer,
      SyntheticCanvas canvas,
      MegamapGrid.MegamapTile tile,
      MegamapGrid.MegamapViewport viewport,
      byte[] surface,
      byte[] shadow,
      boolean fullFlush
  ) {
    ReactRenderContext context = viewportContext(canvas, tile, viewport, surface, shadow, fullFlush);
    ReactRenderContext.push(context);
    try {
      renderer.render();
    } finally {
      ReactRenderContext.pop();
    }
    return context.flush();
  }

  private ReactRenderContext viewportContext(
      MapCanvas canvas,
      MegamapGrid.MegamapTile tile,
      MegamapGrid.MegamapViewport viewport,
      byte[] surface,
      byte[] shadow,
      boolean fullFlush
  ) {
    return ReactRenderContext.builder()
        .canvas(canvas)
        .surface(surface)
        .shadow(shadow)
        .fullFlush(fullFlush)
        .width(viewport.width())
        .height(viewport.height())
        .offsetX(viewport.offsetX())
        .offsetY(viewport.offsetY())
        .scaleX(viewport.scale())
        .scaleY(viewport.scale())
        .textScale(viewport.textScale())
        .gridWidth(tile.gridWidth())
        .gridHeight(tile.gridHeight())
        .build();
  }

  private String surfaceMismatch(ReactRenderContext context, SyntheticCanvas canvas, int size) {
    int[] pixels = canvas.pixels();
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        byte composed = context.surfacePixel(x, y);
        int shown = pixels[(y * size) + x];
        if (composed == ReactRenderContext.UNTOUCHED) {
          if (shown != SyntheticCanvas.BLANK && (byte) shown != ReactRenderContext.UNTOUCHED) {
            return "canvas holds " + shown + " at " + x + "," + y + " where nothing was composed";
          }
          continue;
        }

        if (shown == SyntheticCanvas.BLANK || (byte) shown != composed) {
          return "canvas holds " + shown + " at " + x + "," + y + " but the surface composed " + composed;
        }
      }
    }
    return null;
  }

  private List<ReactRenderer> probeRenderers() {
    List<ReactRenderer> probes = new ArrayList<ReactRenderer>();
    probes.add(new RendererUnknown());
    probes.add(new ClipProbeRenderer());
    probes.add(new SurfaceProbeRenderer());
    return probes;
  }

  private MegamapGrid.FrameCell cell(int mapId, UUID world, int blockX, int blockY) {
    return new MegamapGrid.FrameCell(mapId, world, BlockFace.SOUTH, blockX, blockY, 0, "react-test-megamap", true);
  }

  private static final class ClipProbeRenderer implements ReactRenderer {
    @Override
    public String getId() {
      return "clip-probe";
    }

    @Override
    public void render() {
      clear(MapTheme.SURFACE_3);
      fill(new Region(-500, -500, 4000, 4000), MapTheme.OK);
      line(-400, -400, 900, 900, MapTheme.BAD);
      line(900, -400, -400, 900, MapTheme.BAD);
      line(0, 0, width(), 0, MapTheme.WARN);
      line(0, height() - 1, width(), height() - 1, MapTheme.WARN);
      text(-40, -40, OVERFLOW_TEXT, MapTheme.TEXT_STRONG);
      text(2, 2, OVERFLOW_TEXT, MapTheme.TEXT_STRONG);
      text(width() + 10, height() + 10, OVERFLOW_TEXT, MapTheme.TEXT_STRONG);
      textNear(width(), height(), OVERFLOW_TEXT);
      set(-1, -1, MapTheme.WARN);
      set(width(), height(), MapTheme.WARN);
      setRgb(0, 0, MapTheme.BAD.toRGB());

      Region inner = clipRegion().inset(4);
      pushClip(inner);
      try {
        fill(new Region(-500, -500, 4000, 4000), MapTheme.INFO);
        textIn(inner, 1, 1, OVERFLOW_TEXT, MapTheme.TEXT);
      } finally {
        popClip();
      }

      panel(clipRegion(), null, MapTheme.LINE_STRONG);
      hSeparator(clipRegion(), clipRegion().height() / 2, MapTheme.LINE);
      dashHeader(OVERFLOW_TEXT, OVERFLOW_TEXT, MapTheme.INFO, MapTheme.TEXT);
    }
  }

  private static final class SurfaceProbeRenderer implements ReactRenderer {
    @Override
    public String getId() {
      return "surface-probe";
    }

    @Override
    public MegamapGrid.MegamapCapability megamapCapability() {
      return MegamapGrid.MegamapCapability.adaptive(4, 4);
    }

    @Override
    public void render() {
      int scale = uiScale();
      clear(MapTheme.SURFACE_0);
      dashHeader("SURFACE PROBE " + gridWidth() + "x" + gridHeight(), "42.5", MapTheme.INFO, MapTheme.TEXT);

      Region body = bodyRegion();
      panel(body, MapTheme.SURFACE_1, MapTheme.LINE);
      int rowHeight = MapTheme.rowHeight(scale);
      int rows = Math.min(32, megamapRowCapacity(rowHeight));
      for (int i = 0; i < rows; i++) {
        Region row = body.rowAt(i, rowHeight);
        if (!visible(row)) {
          continue;
        }

        panel(row.inset(1), MapTheme.SURFACE_2, MapTheme.LINE_STRONG);
        Region[] columns = row.inset(2).splitColumns(MapTheme.gutter(scale), 3, 2, 2);
        textIn(columns[0], 1, 1, "Row " + i + " label", MapTheme.TEXT);
        textRightIn(columns[1], 1, 1, String.valueOf(i * 137), MapTheme.TEXT_MUTED);
        textCenteredIn(columns[2], 1, "c" + i, MapTheme.categorical(i));
        hSeparator(row, rowHeight / 2, MapTheme.LINE);
        vSeparator(row, row.width() / 3, MapTheme.LINE);
        border(row.inset(3), MapTheme.heat(i / 32D), 1);

        Region bar = columns[1].inset(1);
        int filled = (bar.width() * ((i * 7) % 11)) / 10;
        fill(new Region(bar.x(), bar.y(), filled, bar.height()), MapTheme.severity(i / 32D));
        for (int x = 0; x < bar.width(); x += 3) {
          setRgb(bar.x() + x, bar.bottom() - 1, gradientRgb(x / (double) Math.max(1, bar.width()), MapTheme.HEAT_COLD, MapTheme.HEAT_HOT));
        }
      }

      line(0, 0, width(), height(), MapTheme.ACCENT_TEAL);
      line(width(), 0, 0, height(), MapTheme.ACCENT_PINK);
      panel(footerRegion(), MapTheme.SURFACE_1, MapTheme.LINE);
      textIn(footerRegion(), 2, 1, "detail " + megamapDetail() + " " + megamapColumns() + "/" + megamapRows(), MapTheme.TEXT_SOFT);
      drawMegamapNotice("probe " + gridWidth() + "x" + gridHeight());
    }
  }

  private static final class ClipRestoreProbeRenderer implements ReactRenderer {
    @Override
    public String getId() {
      return "clip-restore-probe";
    }

    @Override
    public void render() {
      pushClip(PROBE_REGION);
      popClip();
      fill(new Region(0, 0, 4, 4), MapTheme.OK);
    }
  }

  private static final class TextProbeRenderer implements ReactRenderer {
    private final List<String> problems = new ArrayList<String>();

    private List<String> problems() {
      return problems;
    }

    @Override
    public String getId() {
      return "text-probe";
    }

    @Override
    public void render() {
      int scale = uiScale();
      for (int limit = 1; limit <= 120; limit += 7) {
        String fitted = fitText(OVERFLOW_TEXT, limit);
        if (textWidth(fitted) > limit) {
          problems.add("scale " + scale + " limit " + limit + " produced width " + textWidth(fitted));
        }
      }

      if (textWidth("") != 0) {
        problems.add("scale " + scale + " reported a non-zero width for an empty string");
      }

      if (textWidth("a\nb") <= 0) {
        problems.add("scale " + scale + " failed to measure a multi-line string");
      }
    }
  }

  private static final class SyntheticCanvas implements MapCanvas {
    private static final int BLANK = Integer.MIN_VALUE;

    private final int width;
    private final int height;
    private final int[] pixels;
    private int outOfBounds;

    private SyntheticCanvas(int width, int height) {
      this.width = width;
      this.height = height;
      this.pixels = new int[width * height];
      Arrays.fill(this.pixels, BLANK);
    }

    private int[] pixels() {
      return pixels;
    }

    private int outOfBounds() {
      return outOfBounds;
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
      setPixel(x, y, color == null ? -1 : MapColors.indexFor(color.getRGB()));
    }

    @SuppressWarnings("removal")
    @Override
    public Color getPixelColor(int x, int y) {
      if (x < 0 || y < 0 || x >= width || y >= height) {
        return null;
      }

      int stored = pixels[(y * width) + x];
      return stored == BLANK ? null : MapPalette.getColor((byte) stored);
    }

    @Override
    public Color getBasePixelColor(int x, int y) {
      return Color.BLACK;
    }

    @Override
    public void setPixel(int x, int y, byte color) {
      if (x < 0 || y < 0 || x >= width || y >= height) {
        outOfBounds++;
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

      int stored = pixels[(y * width) + x];
      return stored == BLANK ? 0 : (byte) stored;
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
