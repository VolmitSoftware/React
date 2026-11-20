package art.arcane.react.api.rendering;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import art.arcane.react.platform.Facing;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public class MegamapGridTest {

    private static final UUID world = UUID.randomUUID();

    private static MegamapGrid.FrameCell cell(int mapId, Facing facing, int x, int y, int z) {
        return new MegamapGrid.FrameCell(mapId, world, facing, x, y, z, "tps", true);
    }

    @Test
    public void singleFrameProducesNoTiles() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(cell(1, Facing.SOUTH, 10, 64, 5)));
        Assertions.assertTrue(tiles.isEmpty());
    }

    @Test
    public void southWallTwoByTwoAssignsTiles() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, Facing.SOUTH, 10, 65, 5),
                cell(2, Facing.SOUTH, 11, 65, 5),
                cell(3, Facing.SOUTH, 10, 64, 5),
                cell(4, Facing.SOUTH, 11, 64, 5)
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
                cell(1, Facing.NORTH, 10, 64, 5),
                cell(2, Facing.NORTH, 11, 64, 5)
        ));

        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(2));
    }

    @Test
    public void eastWallRunsAlongZ() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, Facing.EAST, 7, 64, 20),
                cell(2, Facing.EAST, 7, 64, 21)
        ));

        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(2));
    }

    @Test
    public void westWallRunsAlongZUnmirrored() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, Facing.WEST, 7, 64, 20),
                cell(2, Facing.WEST, 7, 64, 21)
        ));

        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), tiles.get(2));
    }

    @Test
    public void floorGridUsesXAndZ() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, Facing.UP, 0, 70, 0),
                cell(2, Facing.UP, 1, 70, 0),
                cell(3, Facing.UP, 0, 70, 1),
                cell(4, Facing.UP, 1, 70, 1)
        ));

        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 0), tiles.get(2));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 0, 1), tiles.get(3));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 2, 1, 1), tiles.get(4));
    }

    @Test
    public void threeByTwoAssignsAllSix() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, Facing.SOUTH, 10, 65, 5),
                cell(2, Facing.SOUTH, 11, 65, 5),
                cell(3, Facing.SOUTH, 12, 65, 5),
                cell(4, Facing.SOUTH, 10, 64, 5),
                cell(5, Facing.SOUTH, 11, 64, 5),
                cell(6, Facing.SOUTH, 12, 64, 5)
        ));

        Assertions.assertEquals(6, tiles.size());
        Assertions.assertEquals(new MegamapGrid.MegamapTile(3, 2, 0, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(3, 2, 2, 0), tiles.get(3));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(3, 2, 1, 1), tiles.get(5));
    }

    private static Stream<Arguments> componentsWithoutASolvableGrid() {
        return Stream.of(
                Arguments.of("l shape", List.of(
                        cell(1, Facing.SOUTH, 10, 65, 5),
                        cell(2, Facing.SOUTH, 11, 65, 5),
                        cell(3, Facing.SOUTH, 10, 64, 5))),
                Arguments.of("duplicate map ids", List.of(
                        cell(1, Facing.SOUTH, 10, 64, 5),
                        cell(1, Facing.SOUTH, 11, 64, 5))),
                Arguments.of("different planes", List.of(
                        cell(1, Facing.SOUTH, 10, 64, 5),
                        cell(2, Facing.SOUTH, 11, 64, 6))),
                Arguments.of("different worlds", List.of(
                        new MegamapGrid.FrameCell(1, UUID.randomUUID(), Facing.SOUTH, 10, 64, 5, "tps", true),
                        new MegamapGrid.FrameCell(2, UUID.randomUUID(), Facing.SOUTH, 11, 64, 5, "tps", true))),
                Arguments.of("different facings", List.of(
                        cell(1, Facing.SOUTH, 10, 64, 5),
                        cell(2, Facing.NORTH, 11, 64, 5))),
                Arguments.of("blank renderer id", List.of(
                        new MegamapGrid.FrameCell(1, world, Facing.SOUTH, 10, 64, 5, "", true),
                        new MegamapGrid.FrameCell(2, world, Facing.SOUTH, 11, 64, 5, "", true)))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("componentsWithoutASolvableGrid")
    public void componentsWithoutASolvableGridProduceNoTiles(String label, List<MegamapGrid.FrameCell> cells) {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(cells);

        Assertions.assertTrue(tiles.isEmpty(), label);
    }

    @Test
    public void rectangleWithHoleProducesNoTiles() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, Facing.SOUTH, 10, 66, 5),
                cell(2, Facing.SOUTH, 11, 66, 5),
                cell(3, Facing.SOUTH, 12, 66, 5),
                cell(4, Facing.SOUTH, 10, 65, 5),
                cell(5, Facing.SOUTH, 12, 65, 5),
                cell(6, Facing.SOUTH, 10, 64, 5),
                cell(7, Facing.SOUTH, 11, 64, 5),
                cell(8, Facing.SOUTH, 12, 64, 5)
        ));

        Assertions.assertTrue(tiles.isEmpty());
    }

    @Test
    public void differentRenderersDoNotMerge() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                new MegamapGrid.FrameCell(1, world, Facing.SOUTH, 10, 64, 5, "tps", true),
                new MegamapGrid.FrameCell(2, world, Facing.SOUTH, 11, 64, 5, "tps", true),
                new MegamapGrid.FrameCell(3, world, Facing.SOUTH, 12, 64, 5, "tick-time", true)
        ));

        Assertions.assertEquals(2, tiles.size());
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 1, 0), tiles.get(2));
        Assertions.assertNull(tiles.get(3));
    }

    @Test
    public void rendererIdGroupingIsCaseInsensitive() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                new MegamapGrid.FrameCell(1, world, Facing.SOUTH, 10, 64, 5, "TPS", true),
                new MegamapGrid.FrameCell(2, world, Facing.SOUTH, 11, 64, 5, "tps", true)
        ));

        Assertions.assertEquals(2, tiles.size());
    }

    @Test
    public void misalignedRotationIsExcluded() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, Facing.SOUTH, 10, 64, 5),
                cell(2, Facing.SOUTH, 11, 64, 5),
                new MegamapGrid.FrameCell(3, world, Facing.SOUTH, 12, 64, 5, "tps", false)
        ));

        Assertions.assertEquals(2, tiles.size());
        Assertions.assertNull(tiles.get(3));
    }

    @Test
    public void disjointPairsOnSameWallFormSeparateGrids() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, Facing.SOUTH, 10, 64, 5),
                cell(2, Facing.SOUTH, 11, 64, 5),
                cell(3, Facing.SOUTH, 20, 64, 5),
                cell(4, Facing.SOUTH, 21, 64, 5)
        ));

        Assertions.assertEquals(4, tiles.size());
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(2, 1, 0, 0), tiles.get(3));
    }

    @Test
    public void verticalColumnFormsOneByTwo() {
        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(List.of(
                cell(1, Facing.SOUTH, 10, 65, 5),
                cell(2, Facing.SOUTH, 10, 64, 5)
        ));

        Assertions.assertEquals(new MegamapGrid.MegamapTile(1, 2, 0, 0), tiles.get(1));
        Assertions.assertEquals(new MegamapGrid.MegamapTile(1, 2, 0, 1), tiles.get(2));
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
                cells.add(new MegamapGrid.FrameCell(mapId, world, Facing.SOUTH, 10 + col, 64 - row, 5, "tps", true));
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
                cells.add(new MegamapGrid.FrameCell(id, world, Facing.SOUTH, 10 + col, 64 - row, 5, "tps", true));
                mapId++;
                linear++;
            }
        }

        Map<Integer, MegamapGrid.MegamapTile> tiles = MegamapGrid.solve(cells);
        Assertions.assertTrue(tiles.isEmpty());
    }
}
