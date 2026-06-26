package art.arcane.react.api.rendering;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MegamapGridTest {

    private final UUID world = UUID.randomUUID();

    private MegamapGrid.FrameCell cell(int mapId, BlockFace facing, int x, int y, int z) {
        return new MegamapGrid.FrameCell(mapId, world, facing, x, y, z, "tps", true);
    }

    @Test
    public void singleFrameProducesNoTiles() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(cell(1, BlockFace.SOUTH, 10, 64, 5)));
        Assertions.assertTrue(tiles.isEmpty());
    }

    @Test
    public void southWallTwoByTwoAssignsTiles() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 65, 5),
                cell(2, BlockFace.SOUTH, 11, 65, 5),
                cell(3, BlockFace.SOUTH, 10, 64, 5),
                cell(4, BlockFace.SOUTH, 11, 64, 5)
        ));

        Assertions.assertEquals(4, tiles.size());
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 0), tiles.get(2));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 1), tiles.get(3));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 1), tiles.get(4));
    }

    @Test
    public void northWallHorizontalAxisIsMirrored() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.NORTH, 10, 64, 5),
                cell(2, BlockFace.NORTH, 11, 64, 5)
        ));

        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(2));
    }

    @Test
    public void eastWallRunsAlongZ() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.EAST, 7, 64, 20),
                cell(2, BlockFace.EAST, 7, 64, 21)
        ));

        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(2));
    }

    @Test
    public void westWallRunsAlongZUnmirrored() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.WEST, 7, 64, 20),
                cell(2, BlockFace.WEST, 7, 64, 21)
        ));

        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), tiles.get(2));
    }

    @Test
    public void floorGridUsesXAndZ() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.UP, 0, 70, 0),
                cell(2, BlockFace.UP, 1, 70, 0),
                cell(3, BlockFace.UP, 0, 70, 1),
                cell(4, BlockFace.UP, 1, 70, 1)
        ));

        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 0), tiles.get(2));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 1), tiles.get(3));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 1), tiles.get(4));
    }

    @Test
    public void threeByTwoAssignsAllSix() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 65, 5),
                cell(2, BlockFace.SOUTH, 11, 65, 5),
                cell(3, BlockFace.SOUTH, 12, 65, 5),
                cell(4, BlockFace.SOUTH, 10, 64, 5),
                cell(5, BlockFace.SOUTH, 11, 64, 5),
                cell(6, BlockFace.SOUTH, 12, 64, 5)
        ));

        Assertions.assertEquals(6, tiles.size());
        Assertions.assertEquals(new MegamapGrid.MegamapTile(3, 2, 0, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(3, 2, 2, 0), tiles.get(3));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(3, 2, 1, 1), tiles.get(5));
    }

    @Test
    public void lShapeProducesNoTiles() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 65, 5),
                cell(2, BlockFace.SOUTH, 11, 65, 5),
                cell(3, BlockFace.SOUTH, 10, 64, 5)
        ));

        Assertions.assertTrue(tiles.isEmpty());
    }

    @Test
    public void rectangleWithHoleProducesNoTiles() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 66, 5),
                cell(2, BlockFace.SOUTH, 11, 66, 5),
                cell(3, BlockFace.SOUTH, 12, 66, 5),
                cell(4, BlockFace.SOUTH, 10, 65, 5),
                cell(5, BlockFace.SOUTH, 12, 65, 5),
                cell(6, BlockFace.SOUTH, 10, 64, 5),
                cell(7, BlockFace.SOUTH, 11, 64, 5),
                cell(8, BlockFace.SOUTH, 12, 64, 5)
        ));

        Assertions.assertTrue(tiles.isEmpty());
    }

    @Test
    public void duplicateMapIdsInComponentProduceNoTiles() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5),
                cell(1, BlockFace.SOUTH, 11, 64, 5)
        ));

        Assertions.assertTrue(tiles.isEmpty());
    }

    @Test
    public void differentRenderersDoNotMerge() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                new MegamapGrid.FrameCell(1, world, BlockFace.SOUTH, 10, 64, 5, "tps", true),
                new MegamapGrid.FrameCell(2, world, BlockFace.SOUTH, 11, 64, 5, "tps", true),
                new MegamapGrid.FrameCell(3, world, BlockFace.SOUTH, 12, 64, 5, "tick-time", true)
        ));

        Assertions.assertEquals(2, tiles.size());
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), tiles.get(2));
        Assertions.assertNull(tiles.get(3));
    }

    @Test
    public void rendererIdGroupingIsCaseInsensitive() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                new MegamapGrid.FrameCell(1, world, BlockFace.SOUTH, 10, 64, 5, "TPS", true),
                new MegamapGrid.FrameCell(2, world, BlockFace.SOUTH, 11, 64, 5, "tps", true)
        ));

        Assertions.assertEquals(2, tiles.size());
    }

    @Test
    public void differentPlanesDoNotMerge() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5),
                cell(2, BlockFace.SOUTH, 11, 64, 6)
        ));

        Assertions.assertTrue(tiles.isEmpty());
    }

    @Test
    public void differentWorldsDoNotMerge() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                new MegamapGrid.FrameCell(1, UUID.randomUUID(), BlockFace.SOUTH, 10, 64, 5, "tps", true),
                new MegamapGrid.FrameCell(2, UUID.randomUUID(), BlockFace.SOUTH, 11, 64, 5, "tps", true)
        ));

        Assertions.assertTrue(tiles.isEmpty());
    }

    @Test
    public void differentFacingsDoNotMerge() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5),
                cell(2, BlockFace.NORTH, 11, 64, 5)
        ));

        Assertions.assertTrue(tiles.isEmpty());
    }

    @Test
    public void misalignedRotationIsExcluded() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5),
                cell(2, BlockFace.SOUTH, 11, 64, 5),
                new MegamapGrid.FrameCell(3, world, BlockFace.SOUTH, 12, 64, 5, "tps", false)
        ));

        Assertions.assertEquals(2, tiles.size());
        Assertions.assertNull(tiles.get(3));
    }

    @Test
    public void disjointPairsOnSameWallFormSeparateGrids() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5),
                cell(2, BlockFace.SOUTH, 11, 64, 5),
                cell(3, BlockFace.SOUTH, 20, 64, 5),
                cell(4, BlockFace.SOUTH, 21, 64, 5)
        ));

        Assertions.assertEquals(4, tiles.size());
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(3));
    }

    @Test
    public void verticalColumnFormsOneByTwo() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 65, 5),
                cell(2, BlockFace.SOUTH, 10, 64, 5)
        ));

        Assertions.assertEquals(new MegamapGrid.MegamapTile(1, 2, 0, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(1, 2, 0, 1), tiles.get(2));
    }

    @Test
    public void blankRendererIdProducesNoTiles() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                new MegamapGrid.FrameCell(1, world, BlockFace.SOUTH, 10, 64, 5, "", true),
                new MegamapGrid.FrameCell(2, world, BlockFace.SOUTH, 11, 64, 5, "", true)
        ));

        Assertions.assertTrue(tiles.isEmpty());
    }

    @Property
    public void solverRejectsGridsWithInteriorHole(
            @ForAll @IntRange(min = 3, max = 6) int width,
            @ForAll @IntRange(min = 3, max = 6) int height,
            @ForAll @IntRange(min = 1, max = 5) int holeCol,
            @ForAll @IntRange(min = 1, max = 5) int holeRow) {
        Assume.that(holeCol <= width - 2);
        Assume.that(holeRow <= height - 2);

        List<MegamapGrid.FrameCell> cells = new ArrayList<>();
        int mapId = 1;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (col == holeCol && row == holeRow) {
                    continue;
                }
                cells.add(new MegamapGrid.FrameCell(mapId, world, BlockFace.SOUTH, 10 + col, 64 - row, 5, "tps", true));
                mapId++;
            }
        }

        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(cells);
        Assertions.assertTrue(tiles.isEmpty());
    }

    @Property
    public void solverRejectsGridsWithDuplicateMapId(
            @ForAll @IntRange(min = 1, max = 4) int width,
            @ForAll @IntRange(min = 1, max = 4) int height,
            @ForAll @IntRange(min = 0, max = 15) int duplicateSeed) {
        int total = width * height;
        Assume.that(total >= 2);

        int duplicateIndex = duplicateSeed % total;
        int partnerIndex = (duplicateIndex + 1) % total;
        int sharedMapId = 5000;

        List<MegamapGrid.FrameCell> cells = new ArrayList<>();
        int mapId = 1;
        int linear = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int id = (linear == duplicateIndex || linear == partnerIndex) ? sharedMapId : mapId;
                cells.add(new MegamapGrid.FrameCell(id, world, BlockFace.SOUTH, 10 + col, 64 - row, 5, "tps", true));
                mapId++;
                linear++;
            }
        }

        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(cells);
        Assertions.assertTrue(tiles.isEmpty());
    }
}
