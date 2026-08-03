package art.arcane.react.api.rendering;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MegamapAnalyzeTest {

    private final UUID world = UUID.randomUUID();

    private MegamapGrid.FrameCell cell(int mapId, BlockFace facing, int x, int y, int z) {
        return new MegamapGrid.FrameCell(mapId, world, facing, x, y, z, "tps", true);
    }

    private MegamapGrid.FrameCell cell(int mapId, BlockFace facing, int x, int y, int z, String rendererId) {
        return new MegamapGrid.FrameCell(mapId, world, facing, x, y, z, rendererId, true);
    }

    private MegamapGrid.FrameCell rotated(int mapId, BlockFace facing, int x, int y, int z, String rendererId) {
        return new MegamapGrid.FrameCell(mapId, world, facing, x, y, z, rendererId, false);
    }

    private void assertDefect(MegamapGrid.MegamapSolution solution, int mapId, MegamapGrid.DefectReason reason) {
        MegamapGrid.MegamapDefect defect = solution.defectFor(mapId);
        Assertions.assertNotNull(defect);
        Assertions.assertEquals(mapId, defect.mapId());
        Assertions.assertEquals(reason, defect.reason());
    }

    @Test
    public void nullCellsProduceEmptySolution() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(null);

        Assertions.assertTrue(solution.isEmpty());
    }

    @Test
    public void emptyCellsProduceEmptySolution() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of());

        Assertions.assertTrue(solution.isEmpty());
    }

    @Test
    public void singleFrameProducesNoTileAndNoDefect() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(cell(1, BlockFace.SOUTH, 10, 64, 5)));

        Assertions.assertTrue(solution.isEmpty());
        Assertions.assertNull(solution.tileFor(1));
        Assertions.assertNull(solution.defectFor(1));
    }

    @Test
    public void lShapedComponentDefectsEveryMemberAsHole() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.SOUTH, 10, 65, 5),
                cell(2, BlockFace.SOUTH, 11, 65, 5),
                cell(3, BlockFace.SOUTH, 10, 64, 5)
        ));

        Assertions.assertTrue(solution.tiles().isEmpty());
        Assertions.assertEquals(3, solution.defects().size());
        assertDefect(solution, 1, MegamapGrid.DefectReason.HOLE);
        assertDefect(solution, 2, MegamapGrid.DefectReason.HOLE);
        assertDefect(solution, 3, MegamapGrid.DefectReason.HOLE);
    }

    @Test
    public void duplicateMapIdDefectsWholeComponentIncludingDistinctMember() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5),
                cell(1, BlockFace.SOUTH, 11, 64, 5),
                cell(2, BlockFace.SOUTH, 12, 64, 5)
        ));

        Assertions.assertTrue(solution.tiles().isEmpty());
        Assertions.assertEquals(2, solution.defects().size());
        assertDefect(solution, 1, MegamapGrid.DefectReason.DUPLICATE_MAP_ID);
        assertDefect(solution, 2, MegamapGrid.DefectReason.DUPLICATE_MAP_ID);
    }

    @Test
    public void adjacentSingletonsOfDifferentRenderersBothDefectAsMixedRenderer() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5, "tps"),
                cell(2, BlockFace.SOUTH, 11, 64, 5, "tick-time")
        ));

        Assertions.assertTrue(solution.tiles().isEmpty());
        Assertions.assertEquals(2, solution.defects().size());
        assertDefect(solution, 1, MegamapGrid.DefectReason.MIXED_RENDERER);
        assertDefect(solution, 2, MegamapGrid.DefectReason.MIXED_RENDERER);
    }

    @Test
    public void mixedRendererOnlyDefectsCellsOutsideTilesAndDefects() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5, "tps"),
                cell(2, BlockFace.SOUTH, 11, 64, 5, "tps"),
                cell(3, BlockFace.SOUTH, 12, 64, 5, "tick-time")
        ));

        Assertions.assertEquals(2, solution.tiles().size());
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), solution.tileFor(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), solution.tileFor(2));
        Assertions.assertEquals(1, solution.defects().size());
        assertDefect(solution, 3, MegamapGrid.DefectReason.MIXED_RENDERER);
    }

    @Test
    public void rotatedCellAdjacentToAlignedCellIsDefected() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5),
                cell(2, BlockFace.SOUTH, 11, 64, 5),
                rotated(3, BlockFace.SOUTH, 12, 64, 5, "tps")
        ));

        Assertions.assertEquals(2, solution.tiles().size());
        Assertions.assertNull(solution.tileFor(3));
        Assertions.assertEquals(1, solution.defects().size());
        assertDefect(solution, 3, MegamapGrid.DefectReason.ROTATED);
    }

    @Test
    public void adjacentRotatedCellsDefectEachOther() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                rotated(1, BlockFace.SOUTH, 10, 64, 5, "tps"),
                rotated(2, BlockFace.SOUTH, 11, 64, 5, "tps")
        ));

        Assertions.assertTrue(solution.tiles().isEmpty());
        Assertions.assertEquals(2, solution.defects().size());
        assertDefect(solution, 1, MegamapGrid.DefectReason.ROTATED);
        assertDefect(solution, 2, MegamapGrid.DefectReason.ROTATED);
    }

    @Test
    public void verticallyAdjacentRotatedCellsDefectEachOther() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                rotated(1, BlockFace.SOUTH, 10, 65, 5, "tps"),
                rotated(2, BlockFace.SOUTH, 10, 64, 5, "tps")
        ));

        Assertions.assertEquals(2, solution.defects().size());
        assertDefect(solution, 1, MegamapGrid.DefectReason.ROTATED);
        assertDefect(solution, 2, MegamapGrid.DefectReason.ROTATED);
    }

    @Test
    public void adjacentRotatedCellsDefectRegardlessOfRendererId() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                rotated(1, BlockFace.SOUTH, 10, 64, 5, "tps"),
                rotated(2, BlockFace.SOUTH, 11, 64, 5, "tick-time")
        ));

        Assertions.assertEquals(2, solution.defects().size());
        assertDefect(solution, 1, MegamapGrid.DefectReason.ROTATED);
        assertDefect(solution, 2, MegamapGrid.DefectReason.ROTATED);
    }

    @Test
    public void isolatedRotatedCellsAreNotDefected() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                rotated(1, BlockFace.SOUTH, 10, 64, 5, "tps"),
                rotated(2, BlockFace.SOUTH, 20, 64, 5, "tps")
        ));

        Assertions.assertTrue(solution.isEmpty());
    }

    @Test
    public void diagonallyAdjacentRotatedCellsAreNotDefected() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                rotated(1, BlockFace.SOUTH, 10, 65, 5, "tps"),
                rotated(2, BlockFace.SOUTH, 11, 64, 5, "tps")
        ));

        Assertions.assertTrue(solution.isEmpty());
    }

    @Test
    public void rotatedCellsOnDifferentPlanesAreNotDefected() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                rotated(1, BlockFace.SOUTH, 10, 64, 5, "tps"),
                rotated(2, BlockFace.SOUTH, 11, 64, 6, "tps")
        ));

        Assertions.assertTrue(solution.isEmpty());
    }

    @Test
    public void rotationDefectWinsOverMixedRendererForRotatedCells() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5, "tps"),
                cell(2, BlockFace.SOUTH, 11, 64, 5, "tps"),
                rotated(3, BlockFace.SOUTH, 12, 64, 5, "tick-time")
        ));

        Assertions.assertEquals(1, solution.defects().size());
        assertDefect(solution, 3, MegamapGrid.DefectReason.ROTATED);
    }

    @Test
    public void disjointRendererWallsOnOnePlaneSolveIndependently() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5, "tps"),
                cell(2, BlockFace.SOUTH, 11, 64, 5, "tps"),
                cell(3, BlockFace.SOUTH, 20, 64, 5, "tick-time"),
                cell(4, BlockFace.SOUTH, 21, 64, 5, "tick-time")
        ));

        Assertions.assertEquals(4, solution.tiles().size());
        Assertions.assertTrue(solution.defects().isEmpty());
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), solution.tileFor(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), solution.tileFor(2));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), solution.tileFor(3));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), solution.tileFor(4));
    }

    @Test
    public void touchingRendererWallsOnOnePlaneSolveIndependently() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5, "tps"),
                cell(2, BlockFace.SOUTH, 11, 64, 5, "tps"),
                cell(3, BlockFace.SOUTH, 12, 64, 5, "tick-time"),
                cell(4, BlockFace.SOUTH, 13, 64, 5, "tick-time")
        ));

        Assertions.assertEquals(4, solution.tiles().size());
        Assertions.assertTrue(solution.defects().isEmpty());
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), solution.tileFor(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), solution.tileFor(2));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), solution.tileFor(3));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), solution.tileFor(4));
    }

    @Test
    public void southFacingMapsXAcrossAndYDown() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.SOUTH, 10, 65, 5),
                cell(2, BlockFace.SOUTH, 11, 65, 5),
                cell(3, BlockFace.SOUTH, 10, 64, 5),
                cell(4, BlockFace.SOUTH, 11, 64, 5)
        )).tiles();

        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 0), tiles.get(2));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 1), tiles.get(3));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 1), tiles.get(4));
    }

    @Test
    public void northFacingMirrorsXAndMapsYDown() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.NORTH, 10, 65, 5),
                cell(2, BlockFace.NORTH, 11, 65, 5),
                cell(3, BlockFace.NORTH, 10, 64, 5),
                cell(4, BlockFace.NORTH, 11, 64, 5)
        )).tiles();

        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 0), tiles.get(2));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 1), tiles.get(3));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 1), tiles.get(4));
    }

    @Test
    public void eastFacingMirrorsZAndMapsYDown() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.EAST, 7, 65, 20),
                cell(2, BlockFace.EAST, 7, 65, 21),
                cell(3, BlockFace.EAST, 7, 64, 20),
                cell(4, BlockFace.EAST, 7, 64, 21)
        )).tiles();

        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 0), tiles.get(2));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 1), tiles.get(3));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 1), tiles.get(4));
    }

    @Test
    public void westFacingMapsZAcrossAndYDown() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.WEST, 7, 65, 20),
                cell(2, BlockFace.WEST, 7, 65, 21),
                cell(3, BlockFace.WEST, 7, 64, 20),
                cell(4, BlockFace.WEST, 7, 64, 21)
        )).tiles();

        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 0), tiles.get(2));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 1), tiles.get(3));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 1), tiles.get(4));
    }

    @Test
    public void upFacingMapsXAcrossAndZDown() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.UP, 0, 70, 0),
                cell(2, BlockFace.UP, 1, 70, 0),
                cell(3, BlockFace.UP, 0, 70, 1),
                cell(4, BlockFace.UP, 1, 70, 1)
        )).tiles();

        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 0), tiles.get(2));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 1), tiles.get(3));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 1), tiles.get(4));
    }

    @Test
    public void downFacingMapsXAcrossAndMirrorsZ() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.DOWN, 0, 70, 0),
                cell(2, BlockFace.DOWN, 1, 70, 0),
                cell(3, BlockFace.DOWN, 0, 70, 1),
                cell(4, BlockFace.DOWN, 1, 70, 1)
        )).tiles();

        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 1), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 1), tiles.get(2));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 0), tiles.get(3));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 0), tiles.get(4));
    }

    @Test
    public void differentWorldsNeverMerge() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                new MegamapGrid.FrameCell(1, UUID.randomUUID(), BlockFace.SOUTH, 10, 64, 5, "tps", true),
                new MegamapGrid.FrameCell(2, UUID.randomUUID(), BlockFace.SOUTH, 11, 64, 5, "tps", true)
        ));

        Assertions.assertTrue(solution.isEmpty());
    }

    @Test
    public void differentFacingsNeverMerge() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5),
                cell(2, BlockFace.NORTH, 11, 64, 5)
        ));

        Assertions.assertTrue(solution.isEmpty());
    }

    @Test
    public void blankRendererCellsAreIgnoredEntirely() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5, ""),
                cell(2, BlockFace.SOUTH, 11, 64, 5, "   ")
        ));

        Assertions.assertTrue(solution.isEmpty());
    }

    @Test
    public void rendererIdWhitespaceIsTrimmedForGrouping() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5, " TPS "),
                cell(2, BlockFace.SOUTH, 11, 64, 5, "tps")
        ));

        Assertions.assertEquals(2, solution.tiles().size());
        Assertions.assertTrue(solution.defects().isEmpty());
    }

    @Test
    public void unsupportedFacingsAreIgnored() {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.NORTH_EAST, 10, 64, 5),
                cell(2, BlockFace.NORTH_EAST, 11, 64, 5)
        ));

        Assertions.assertTrue(solution.isEmpty());
    }

    @Test
    public void nullEntriesAndNullWorldsAreIgnored() {
        List<MegamapGrid.FrameCell> cells = new ArrayList<>();
        cells.add(null);
        cells.add(new MegamapGrid.FrameCell(9, null, BlockFace.SOUTH, 50, 64, 5, "tps", true));
        cells.add(cell(1, BlockFace.SOUTH, 10, 64, 5));
        cells.add(cell(2, BlockFace.SOUTH, 11, 64, 5));

        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(cells);

        Assertions.assertEquals(2, solution.tiles().size());
        Assertions.assertTrue(solution.defects().isEmpty());
        Assertions.assertNull(solution.tileFor(9));
    }
}
