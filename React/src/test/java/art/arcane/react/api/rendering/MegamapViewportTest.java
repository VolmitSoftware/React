package art.arcane.react.api.rendering;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MegamapViewportTest {

    private static final int CANVAS = 128;
    private static final int BUDGET = 16;

    private MegamapGrid.MegamapViewport viewport(
            int gridWidth,
            int gridHeight,
            int tileX,
            int tileY,
            MegamapGrid.MegamapCapability capability,
            int tileBudget
    ) {
        return MegamapGrid.viewportFor(
                new MegamapGrid.MegamapTile(gridWidth, gridHeight, tileX, tileY),
                capability,
                tileBudget,
                CANVAS
        );
    }

    @Test
    public void adaptiveTwoByTwoExpandsLogicalCanvasAndDoublesTextScale() {
        Assertions.assertEquals(
                new MegamapGrid.MegamapViewport(256, 256, 0, 0, 1, 2, 0, 0, 256, true, false),
                viewport(2, 2, 0, 0, MegamapGrid.MegamapCapability.adaptive(4, 4), BUDGET));
        Assertions.assertEquals(
                new MegamapGrid.MegamapViewport(256, 256, 128, 0, 1, 2, 0, 0, 256, true, false),
                viewport(2, 2, 1, 0, MegamapGrid.MegamapCapability.adaptive(4, 4), BUDGET));
        Assertions.assertEquals(
                new MegamapGrid.MegamapViewport(256, 256, 128, 128, 1, 2, 0, 0, 256, true, false),
                viewport(2, 2, 1, 1, MegamapGrid.MegamapCapability.adaptive(4, 4), BUDGET));
    }

    @Test
    public void adaptiveTallStripKeepsTextScaleAtOne() {
        MegamapGrid.MegamapViewport view = viewport(1, 4, 0, 2, MegamapGrid.MegamapCapability.adaptive(4, 4), BUDGET);

        Assertions.assertEquals(new MegamapGrid.MegamapViewport(128, 512, 0, 256, 1, 1, 0, 0, 512, true, false), view);
        Assertions.assertTrue(view.adaptive());
        Assertions.assertTrue(view.coversAnything(CANVAS));
    }

    @Test
    public void adaptiveViewportAlwaysCoversAnything() {
        MegamapGrid.MegamapViewport view = viewport(3, 3, 2, 2, MegamapGrid.MegamapCapability.adaptive(4, 4), BUDGET);

        Assertions.assertTrue(view.coversAnything(CANVAS));
        Assertions.assertTrue(view.fullyCovers(CANVAS));
        Assertions.assertEquals(3, view.textScale());
    }

    @Test
    public void magnifyWideStripCentersContentOnTheMiddleTile() {
        MegamapGrid.MegamapViewport left = viewport(3, 1, 0, 0, MegamapGrid.MegamapCapability.magnify(), BUDGET);
        MegamapGrid.MegamapViewport middle = viewport(3, 1, 1, 0, MegamapGrid.MegamapCapability.magnify(), BUDGET);
        MegamapGrid.MegamapViewport right = viewport(3, 1, 2, 0, MegamapGrid.MegamapCapability.magnify(), BUDGET);

        Assertions.assertEquals(new MegamapGrid.MegamapViewport(128, 128, -128, 0, 1, 1, 128, 0, 128, false, false), left);
        Assertions.assertEquals(new MegamapGrid.MegamapViewport(128, 128, 0, 0, 1, 1, 0, 0, 128, false, false), middle);
        Assertions.assertEquals(new MegamapGrid.MegamapViewport(128, 128, 128, 0, 1, 1, -128, 0, 128, false, false), right);

        Assertions.assertFalse(left.coversAnything(CANVAS));
        Assertions.assertTrue(middle.coversAnything(CANVAS));
        Assertions.assertTrue(middle.fullyCovers(CANVAS));
        Assertions.assertFalse(right.coversAnything(CANVAS));
    }

    @Test
    public void magnifyTallStripCentersContentOnTheMiddleTile() {
        MegamapGrid.MegamapViewport top = viewport(1, 3, 0, 0, MegamapGrid.MegamapCapability.magnify(), BUDGET);
        MegamapGrid.MegamapViewport middle = viewport(1, 3, 0, 1, MegamapGrid.MegamapCapability.magnify(), BUDGET);
        MegamapGrid.MegamapViewport bottom = viewport(1, 3, 0, 2, MegamapGrid.MegamapCapability.magnify(), BUDGET);

        Assertions.assertEquals(new MegamapGrid.MegamapViewport(128, 128, 0, -128, 1, 1, 0, 128, 128, false, false), top);
        Assertions.assertEquals(new MegamapGrid.MegamapViewport(128, 128, 0, 0, 1, 1, 0, 0, 128, false, false), middle);
        Assertions.assertEquals(new MegamapGrid.MegamapViewport(128, 128, 0, 128, 1, 1, 0, -128, 128, false, false), bottom);

        Assertions.assertFalse(top.coversAnything(CANVAS));
        Assertions.assertTrue(middle.fullyCovers(CANVAS));
        Assertions.assertFalse(bottom.coversAnything(CANVAS));
    }

    @Test
    public void magnifySquareGridScalesContentToTheWholeGrid() {
        MegamapGrid.MegamapViewport view = viewport(2, 2, 0, 1, MegamapGrid.MegamapCapability.magnify(), BUDGET);

        Assertions.assertEquals(new MegamapGrid.MegamapViewport(128, 128, 0, 128, 2, 1, 0, -128, 256, false, false), view);
        Assertions.assertTrue(view.coversAnything(CANVAS));
        Assertions.assertTrue(view.fullyCovers(CANVAS));
    }

    @Test
    public void nullCapabilityFallsBackToMagnify() {
        MegamapGrid.MegamapViewport view = viewport(2, 2, 0, 0, null, BUDGET);

        Assertions.assertEquals(new MegamapGrid.MegamapViewport(128, 128, 0, 0, 2, 1, 0, 0, 256, false, false), view);
    }

    @Test
    public void gridsOverTileBudgetForceNonAdaptiveCappedViewports() {
        MegamapGrid.MegamapViewport view = viewport(3, 3, 1, 1, MegamapGrid.MegamapCapability.adaptive(4, 4), 4);

        Assertions.assertEquals(new MegamapGrid.MegamapViewport(128, 128, 128, 128, 3, 1, -128, -128, 384, false, true), view);
        Assertions.assertFalse(view.adaptive());
        Assertions.assertTrue(view.capped());
        Assertions.assertTrue(view.fullyCovers(CANVAS));
    }

    @Test
    public void tileBudgetIsClampedToAtLeastOne() {
        MegamapGrid.MegamapViewport view = viewport(2, 2, 0, 0, MegamapGrid.MegamapCapability.adaptive(4, 4), 0);

        Assertions.assertEquals(new MegamapGrid.MegamapViewport(128, 128, 0, 0, 2, 1, 0, 0, 256, false, true), view);
        Assertions.assertTrue(view.capped());
    }

    @Test
    public void gridsWithinTileBudgetAreNotCapped() {
        MegamapGrid.MegamapViewport view = viewport(2, 2, 0, 0, MegamapGrid.MegamapCapability.adaptive(4, 4), 4);

        Assertions.assertTrue(view.adaptive());
        Assertions.assertFalse(view.capped());
    }

    @Test
    public void degenerateTileDimensionsClampToOne() {
        Assertions.assertEquals(1, new MegamapGrid.MegamapTile(0, 0, 0, 0).tiles());
        Assertions.assertEquals(5, new MegamapGrid.MegamapTile(-1, 5, 0, 0).tiles());
        Assertions.assertEquals(6, new MegamapGrid.MegamapTile(3, 2, 0, 0).tiles());
        Assertions.assertEquals(
                new MegamapGrid.MegamapViewport(128, 128, 0, 0, 1, 1, 0, 0, 128, false, false),
                viewport(0, 0, 0, 0, MegamapGrid.MegamapCapability.magnify(), BUDGET));
    }

    @Test
    public void detailForGridCrossesEveryTileCountBoundary() {
        Assertions.assertEquals(MegamapGrid.MegamapDetail.COMPACT, MegamapGrid.MegamapDetail.forGrid(1, 1));
        Assertions.assertEquals(MegamapGrid.MegamapDetail.EXPANDED, MegamapGrid.MegamapDetail.forGrid(2, 1));
        Assertions.assertEquals(MegamapGrid.MegamapDetail.EXPANDED, MegamapGrid.MegamapDetail.forGrid(1, 3));
        Assertions.assertEquals(MegamapGrid.MegamapDetail.RICH, MegamapGrid.MegamapDetail.forGrid(2, 2));
        Assertions.assertEquals(MegamapGrid.MegamapDetail.RICH, MegamapGrid.MegamapDetail.forGrid(1, 4));
        Assertions.assertEquals(MegamapGrid.MegamapDetail.RICH, MegamapGrid.MegamapDetail.forGrid(4, 2));
        Assertions.assertEquals(MegamapGrid.MegamapDetail.MAXIMAL, MegamapGrid.MegamapDetail.forGrid(3, 3));
        Assertions.assertEquals(MegamapGrid.MegamapDetail.MAXIMAL, MegamapGrid.MegamapDetail.forGrid(5, 2));
    }

    @Test
    public void detailForGridClampsNonPositiveDimensions() {
        Assertions.assertEquals(MegamapGrid.MegamapDetail.COMPACT, MegamapGrid.MegamapDetail.forGrid(0, 0));
        Assertions.assertEquals(MegamapGrid.MegamapDetail.RICH, MegamapGrid.MegamapDetail.forGrid(-3, 5));
    }

    @Test
    public void detailAtLeastFollowsDeclarationOrderAndAcceptsNull() {
        Assertions.assertTrue(MegamapGrid.MegamapDetail.COMPACT.atLeast(null));
        Assertions.assertTrue(MegamapGrid.MegamapDetail.MAXIMAL.atLeast(null));
        Assertions.assertTrue(MegamapGrid.MegamapDetail.RICH.atLeast(MegamapGrid.MegamapDetail.RICH));
        Assertions.assertTrue(MegamapGrid.MegamapDetail.RICH.atLeast(MegamapGrid.MegamapDetail.EXPANDED));
        Assertions.assertTrue(MegamapGrid.MegamapDetail.MAXIMAL.atLeast(MegamapGrid.MegamapDetail.COMPACT));
        Assertions.assertFalse(MegamapGrid.MegamapDetail.EXPANDED.atLeast(MegamapGrid.MegamapDetail.RICH));
        Assertions.assertFalse(MegamapGrid.MegamapDetail.COMPACT.atLeast(MegamapGrid.MegamapDetail.EXPANDED));
    }

    @Test
    public void magnifyCapabilityNeverAdapts() {
        MegamapGrid.MegamapCapability capability = MegamapGrid.MegamapCapability.magnify();

        Assertions.assertFalse(capability.adaptive());
        Assertions.assertFalse(capability.adaptsTo(1, 1));
        Assertions.assertFalse(capability.adaptsTo(2, 2));
        Assertions.assertEquals(MegamapGrid.MegamapDetail.COMPACT, capability.detailFor(2, 2));
    }

    @Test
    public void adaptiveCapabilityAcceptsOnlyGridsWithinItsBounds() {
        MegamapGrid.MegamapCapability capability = MegamapGrid.MegamapCapability.adaptive(3, 2);

        Assertions.assertTrue(capability.adaptive());
        Assertions.assertTrue(capability.adaptsTo(1, 1));
        Assertions.assertTrue(capability.adaptsTo(3, 2));
        Assertions.assertFalse(capability.adaptsTo(4, 2));
        Assertions.assertFalse(capability.adaptsTo(3, 3));
        Assertions.assertFalse(capability.adaptsTo(0, 1));
    }

    @Test
    public void minimumGridCapabilityRejectsGridsBelowItsFloor() {
        MegamapGrid.MegamapCapability capability = MegamapGrid.MegamapCapability.adaptive(2, 1, 4, 4);

        Assertions.assertFalse(capability.adaptsTo(1, 1));
        Assertions.assertTrue(capability.adaptsTo(2, 1));
        Assertions.assertTrue(capability.adaptsTo(4, 4));
        Assertions.assertFalse(capability.adaptsTo(5, 4));
        Assertions.assertFalse(capability.adaptsTo(2, 0));
        Assertions.assertEquals(MegamapGrid.MegamapDetail.COMPACT, capability.detailFor(1, 1));
        Assertions.assertEquals(MegamapGrid.MegamapDetail.RICH, capability.detailFor(2, 2));
    }

    @Test
    public void minimumGridCapabilityFallsBackToMagnifyViewport() {
        MegamapGrid.MegamapViewport view = viewport(1, 1, 0, 0, MegamapGrid.MegamapCapability.adaptive(2, 1, 4, 4), BUDGET);

        Assertions.assertFalse(view.adaptive());
        Assertions.assertFalse(view.capped());
        Assertions.assertEquals(new MegamapGrid.MegamapViewport(128, 128, 0, 0, 1, 1, 0, 0, 128, false, false), view);
    }

    @Test
    public void contentColumnsAndRowsClampToCapabilityBounds() {
        MegamapGrid.MegamapCapability capability = MegamapGrid.MegamapCapability.adaptive(3, 2);

        Assertions.assertEquals(1, capability.contentColumns(1));
        Assertions.assertEquals(3, capability.contentColumns(3));
        Assertions.assertEquals(3, capability.contentColumns(9));
        Assertions.assertEquals(1, capability.contentColumns(0));
        Assertions.assertEquals(1, capability.contentColumns(-2));
        Assertions.assertEquals(1, capability.contentRows(1));
        Assertions.assertEquals(2, capability.contentRows(2));
        Assertions.assertEquals(2, capability.contentRows(7));
        Assertions.assertEquals(1, capability.contentRows(0));
    }

    @Test
    public void capabilityBoundsAreNormalizedOnConstruction() {
        MegamapGrid.MegamapCapability capability =
                new MegamapGrid.MegamapCapability(MegamapGrid.MegamapMode.ADAPTIVE, 0, -3, -9, 1);

        Assertions.assertEquals(1, capability.minGridWidth());
        Assertions.assertEquals(1, capability.minGridHeight());
        Assertions.assertEquals(1, capability.maxGridWidth());
        Assertions.assertEquals(1, capability.maxGridHeight());
        Assertions.assertTrue(capability.adaptsTo(1, 1));
        Assertions.assertFalse(capability.adaptsTo(2, 1));
    }

    @Test
    public void capabilityRejectsNullMode() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new MegamapGrid.MegamapCapability(null, 1, 1, 2, 2));
    }

    @Test
    public void adaptiveWallAcceptsWideRectanglesUpToItsSpan() {
        MegamapGrid.MegamapCapability wall = MegamapGrid.MegamapCapability.adaptiveWall();
        Assertions.assertTrue(wall.adaptsTo(5, 3));
        Assertions.assertTrue(wall.adaptsTo(
                MegamapGrid.MegamapCapability.MAX_ADAPTIVE_SPAN,
                MegamapGrid.MegamapCapability.MAX_ADAPTIVE_SPAN));
        Assertions.assertFalse(wall.adaptsTo(MegamapGrid.MegamapCapability.MAX_ADAPTIVE_SPAN + 1, 1));
        Assertions.assertFalse(wall.adaptsTo(1, MegamapGrid.MegamapCapability.MAX_ADAPTIVE_SPAN + 1));
    }

    @Test
    public void adaptiveWallFiveByThreeUsesTheFullLogicalCanvasWithoutLetterbox() {
        MegamapGrid.MegamapCapability wall = MegamapGrid.MegamapCapability.adaptiveWall();
        Assertions.assertEquals(
                new MegamapGrid.MegamapViewport(640, 384, 0, 0, 1, 3, 0, 0, 640, true, false),
                viewport(5, 3, 0, 0, wall, 32));
        Assertions.assertEquals(
                new MegamapGrid.MegamapViewport(640, 384, 512, 256, 1, 3, 0, 0, 640, true, false),
                viewport(5, 3, 4, 2, wall, 32));

        for (int tileY = 0; tileY < 3; tileY++) {
            for (int tileX = 0; tileX < 5; tileX++) {
                MegamapGrid.MegamapViewport tile = viewport(5, 3, tileX, tileY, wall, 32);
                Assertions.assertTrue(tile.adaptive());
                Assertions.assertFalse(tile.capped());
                Assertions.assertTrue(tile.coversAnything(CANVAS));
            }
        }
    }
}
