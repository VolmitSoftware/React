package art.arcane.react.api.rendering;

import org.bukkit.block.BlockFace;
import org.junit.Assert;
import org.junit.Test;

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
        Assert.assertTrue(tiles.isEmpty());
    }

    @Test
    public void southWallTwoByTwoAssignsTiles() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 65, 5),
                cell(2, BlockFace.SOUTH, 11, 65, 5),
                cell(3, BlockFace.SOUTH, 10, 64, 5),
                cell(4, BlockFace.SOUTH, 11, 64, 5)
        ));

        Assert.assertEquals(4, tiles.size());
        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 0), tiles.get(1));
        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 0), tiles.get(2));
        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 1), tiles.get(3));
        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 1), tiles.get(4));
    }

    @Test
    public void northWallHorizontalAxisIsMirrored() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.NORTH, 10, 64, 5),
                cell(2, BlockFace.NORTH, 11, 64, 5)
        ));

        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), tiles.get(1));
        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(2));
    }

    @Test
    public void eastWallRunsAlongZ() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.EAST, 7, 64, 20),
                cell(2, BlockFace.EAST, 7, 64, 21)
        ));

        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), tiles.get(1));
        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(2));
    }

    @Test
    public void westWallRunsAlongZUnmirrored() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.WEST, 7, 64, 20),
                cell(2, BlockFace.WEST, 7, 64, 21)
        ));

        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(1));
        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), tiles.get(2));
    }

    @Test
    public void floorGridUsesXAndZ() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.UP, 0, 70, 0),
                cell(2, BlockFace.UP, 1, 70, 0),
                cell(3, BlockFace.UP, 0, 70, 1),
                cell(4, BlockFace.UP, 1, 70, 1)
        ));

        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 0), tiles.get(1));
        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 0), tiles.get(2));
        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 1), tiles.get(3));
        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 1), tiles.get(4));
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

        Assert.assertEquals(6, tiles.size());
        Assert.assertEquals(new MegamapGrid.MegamapTile(3, 2, 0, 0), tiles.get(1));
        Assert.assertEquals(new MegamapGrid.MegamapTile(3, 2, 2, 0), tiles.get(3));
        Assert.assertEquals(new MegamapGrid.MegamapTile(3, 2, 1, 1), tiles.get(5));
    }

    @Test
    public void lShapeProducesNoTiles() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 65, 5),
                cell(2, BlockFace.SOUTH, 11, 65, 5),
                cell(3, BlockFace.SOUTH, 10, 64, 5)
        ));

        Assert.assertTrue(tiles.isEmpty());
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

        Assert.assertTrue(tiles.isEmpty());
    }

    @Test
    public void duplicateMapIdsInComponentProduceNoTiles() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5),
                cell(1, BlockFace.SOUTH, 11, 64, 5)
        ));

        Assert.assertTrue(tiles.isEmpty());
    }

    @Test
    public void differentRenderersDoNotMerge() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                new MegamapGrid.FrameCell(1, world, BlockFace.SOUTH, 10, 64, 5, "tps", true),
                new MegamapGrid.FrameCell(2, world, BlockFace.SOUTH, 11, 64, 5, "tps", true),
                new MegamapGrid.FrameCell(3, world, BlockFace.SOUTH, 12, 64, 5, "tick-time", true)
        ));

        Assert.assertEquals(2, tiles.size());
        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(1));
        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), tiles.get(2));
        Assert.assertNull(tiles.get(3));
    }

    @Test
    public void rendererIdGroupingIsCaseInsensitive() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                new MegamapGrid.FrameCell(1, world, BlockFace.SOUTH, 10, 64, 5, "TPS", true),
                new MegamapGrid.FrameCell(2, world, BlockFace.SOUTH, 11, 64, 5, "tps", true)
        ));

        Assert.assertEquals(2, tiles.size());
    }

    @Test
    public void differentPlanesDoNotMerge() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5),
                cell(2, BlockFace.SOUTH, 11, 64, 6)
        ));

        Assert.assertTrue(tiles.isEmpty());
    }

    @Test
    public void differentWorldsDoNotMerge() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                new MegamapGrid.FrameCell(1, UUID.randomUUID(), BlockFace.SOUTH, 10, 64, 5, "tps", true),
                new MegamapGrid.FrameCell(2, UUID.randomUUID(), BlockFace.SOUTH, 11, 64, 5, "tps", true)
        ));

        Assert.assertTrue(tiles.isEmpty());
    }

    @Test
    public void differentFacingsDoNotMerge() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5),
                cell(2, BlockFace.NORTH, 11, 64, 5)
        ));

        Assert.assertTrue(tiles.isEmpty());
    }

    @Test
    public void misalignedRotationIsExcluded() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5),
                cell(2, BlockFace.SOUTH, 11, 64, 5),
                new MegamapGrid.FrameCell(3, world, BlockFace.SOUTH, 12, 64, 5, "tps", false)
        ));

        Assert.assertEquals(2, tiles.size());
        Assert.assertNull(tiles.get(3));
    }

    @Test
    public void disjointPairsOnSameWallFormSeparateGrids() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 64, 5),
                cell(2, BlockFace.SOUTH, 11, 64, 5),
                cell(3, BlockFace.SOUTH, 20, 64, 5),
                cell(4, BlockFace.SOUTH, 21, 64, 5)
        ));

        Assert.assertEquals(4, tiles.size());
        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(1));
        Assert.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(3));
    }

    @Test
    public void verticalColumnFormsOneByTwo() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, BlockFace.SOUTH, 10, 65, 5),
                cell(2, BlockFace.SOUTH, 10, 64, 5)
        ));

        Assert.assertEquals(new MegamapGrid.MegamapTile(1, 2, 0, 0), tiles.get(1));
        Assert.assertEquals(new MegamapGrid.MegamapTile(1, 2, 0, 1), tiles.get(2));
    }

    @Test
    public void blankRendererIdProducesNoTiles() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                new MegamapGrid.FrameCell(1, world, BlockFace.SOUTH, 10, 64, 5, "", true),
                new MegamapGrid.FrameCell(2, world, BlockFace.SOUTH, 11, 64, 5, "", true)
        ));

        Assert.assertTrue(tiles.isEmpty());
    }
}
