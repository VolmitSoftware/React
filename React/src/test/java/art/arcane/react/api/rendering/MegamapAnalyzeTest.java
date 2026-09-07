package art.arcane.react.api.rendering;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

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

    @ParameterizedTest(name = "({0},{1},{2}) and ({3},{4},{5}) are not neighbours")
    @CsvSource({
            "10, 64, 5, 20, 64, 5",
            "10, 65, 5, 11, 64, 5",
            "10, 64, 5, 11, 64, 6"
    })
    public void rotatedCellsThatAreNotOrthogonalNeighboursAreNotDefected(int firstX,
                                                                        int firstY,
                                                                        int firstZ,
                                                                        int secondX,
                                                                        int secondY,
                                                                        int secondZ) {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                rotated(1, BlockFace.SOUTH, firstX, firstY, firstZ, "tps"),
                rotated(2, BlockFace.SOUTH, secondX, secondY, secondZ, "tps")
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

    @ParameterizedTest(name = "second wall at x={0},{1}")
    @CsvSource({
            "20, 21",
            "12, 13"
    })
    public void rendererWallsOnOnePlaneSolveIndependently(int thirdX, int fourthX) {
        MegamapGrid.MegamapSolution solution = MegamapGrid.analyze(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5, "tps"),
                cell(2, BlockFace.SOUTH, 11, 64, 5, "tps"),
                cell(3, BlockFace.SOUTH, thirdX, 64, 5, "tick-time"),
                cell(4, BlockFace.SOUTH, fourthX, 64, 5, "tick-time")
        ));

        Assertions.assertEquals(4, solution.tiles().size());
        Assertions.assertTrue(solution.defects().isEmpty());
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), solution.tileFor(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), solution.tileFor(2));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), solution.tileFor(3));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), solution.tileFor(4));
    }

    private static Stream<Arguments> facingProjections() {
        return Stream.of(
                Arguments.of(BlockFace.SOUTH,
                        new int[][]{{10, 65, 5}, {11, 65, 5}, {10, 64, 5}, {11, 64, 5}},
                        new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}),
                Arguments.of(BlockFace.NORTH,
                        new int[][]{{10, 65, 5}, {11, 65, 5}, {10, 64, 5}, {11, 64, 5}},
                        new int[][]{{1, 0}, {0, 0}, {1, 1}, {0, 1}}),
                Arguments.of(BlockFace.EAST,
                        new int[][]{{7, 65, 20}, {7, 65, 21}, {7, 64, 20}, {7, 64, 21}},
                        new int[][]{{1, 0}, {0, 0}, {1, 1}, {0, 1}}),
                Arguments.of(BlockFace.WEST,
                        new int[][]{{7, 65, 20}, {7, 65, 21}, {7, 64, 20}, {7, 64, 21}},
                        new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}),
                Arguments.of(BlockFace.UP,
                        new int[][]{{0, 70, 0}, {1, 70, 0}, {0, 70, 1}, {1, 70, 1}},
                        new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}),
                Arguments.of(BlockFace.DOWN,
                        new int[][]{{0, 70, 0}, {1, 70, 0}, {0, 70, 1}, {1, 70, 1}},
                        new int[][]{{0, 1}, {1, 1}, {0, 0}, {1, 0}})
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("facingProjections")
    public void facingProjectsCellsOntoTheTileGrid(BlockFace facing, int[][] positions, int[][] expected) {
        List<MegamapGrid.FrameCell> cells = new ArrayList<>();
        for (int index = 0; index < positions.length; index++) {
            cells.add(cell(index + 1, facing, positions[index][0], positions[index][1], positions[index][2]));
        }

        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.analyze(cells).tiles();

        for (int index = 0; index < expected.length; index++) {
            Assertions.assertEquals(
                    new MegamapGrid.MegamapTile(2, 2, expected[index][0], expected[index][1]),
                    tiles.get(index + 1));
        }
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
