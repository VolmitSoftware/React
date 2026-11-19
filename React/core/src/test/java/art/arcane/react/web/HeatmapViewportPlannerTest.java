package art.arcane.react.web;

import art.arcane.react.api.web.heatmap.HeatmapViewportPlanner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HeatmapViewportPlannerTest {

    private final HeatmapViewportPlanner planner = new HeatmapViewportPlanner();

    @Test
    void radius_sixteen_retains_one_chunk_cells() {
        HeatmapViewportPlanner.HeatmapViewportPlan plan = planner.plan(4, -3, 16);

        assertEquals(1, plan.cellSizeChunks());
        assertEquals(-12, plan.originChunkX());
        assertEquals(-19, plan.originChunkZ());
        assertEquals(33, plan.width());
        assertEquals(33, plan.height());
    }

    @Test
    void radius_seventeen_uses_power_of_two_aggregation_and_floor_alignment() {
        HeatmapViewportPlanner.HeatmapViewportPlan plan = planner.plan(0, 0, 17);

        assertEquals(2, plan.cellSizeChunks());
        assertEquals(-18, plan.originChunkX());
        assertEquals(-18, plan.originChunkZ());
        assertEquals(18, plan.width());
        assertEquals(18, plan.height());
        assertEquals(-18, plan.scanMinimumChunkX());
        assertEquals(17, plan.scanMaximumChunkX());
    }

    @Test
    void maximum_radius_stays_within_the_axis_budget() {
        HeatmapViewportPlanner.HeatmapViewportPlan plan = planner.plan(
            0,
            0,
            HeatmapViewportPlanner.MAX_RADIUS
        );

        assertEquals(131_072, plan.cellSizeChunks());
        assertTrue(plan.width() <= HeatmapViewportPlanner.MAX_CELLS_PER_AXIS);
        assertTrue(plan.height() <= HeatmapViewportPlanner.MAX_CELLS_PER_AXIS);
    }

    @Test
    void invalid_radius_and_overflowing_viewports_are_rejected() {
        assertThrows(IllegalArgumentException.class, () -> planner.plan(0, 0, 0));
        assertThrows(
            IllegalArgumentException.class,
            () -> planner.plan(0, 0, HeatmapViewportPlanner.MAX_RADIUS + 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> planner.plan(Integer.MAX_VALUE, 0, HeatmapViewportPlanner.MAX_RADIUS)
        );
    }
}
