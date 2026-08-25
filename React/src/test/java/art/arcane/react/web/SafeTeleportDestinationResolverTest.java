package art.arcane.react.api.web;

import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SafeTeleportDestinationResolverTest {

    @Test
    void resolvesTheExactColumnCenterAboveSafeSolidFooting() {
        World world = mock(World.class);
        Material safeMaterial = mock(Material.class);
        when(safeMaterial.isSolid()).thenReturn(true);
        Block footing = block(safeMaterial, 64, false, false);
        Block air = block(Material.AIR, 65, true, false);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getHighestBlockAt(8, -8, HeightMap.MOTION_BLOCKING_NO_LEAVES)).thenReturn(footing);
        when(world.getBlockAt(8, 65, -8)).thenReturn(air);
        when(world.getBlockAt(8, 66, -8)).thenReturn(air);

        Location destination = new SafeTeleportDestinationResolver().resolve(world, 8, -8);

        assertEquals(8.5D, destination.getX());
        assertEquals(65D, destination.getY());
        assertEquals(-7.5D, destination.getZ());
    }

    @Test
    void rejectsAChunkWithOnlyHazardousFooting() {
        World world = mock(World.class);
        Block magma = block(Material.MAGMA_BLOCK, 64, false, false);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getHighestBlockAt(anyInt(), anyInt(), eq(HeightMap.MOTION_BLOCKING_NO_LEAVES)))
            .thenReturn(magma);

        assertNull(new SafeTeleportDestinationResolver().resolve(world, 8, 8));
    }

    @Test
    void searchesBelowABedrockRoofForSafeFooting() {
        World world = mock(World.class);
        Material safeMaterial = mock(Material.class);
        Material openMaterial = mock(Material.class);
        when(safeMaterial.isSolid()).thenReturn(true);
        when(openMaterial.isSolid()).thenReturn(false);
        Block bedrock = block(Material.BEDROCK, 127, false, false);
        Block stone = block(safeMaterial, 64, false, false);
        Block air = block(openMaterial, 65, true, false);
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(256);
        when(world.getHighestBlockAt(8, 8, HeightMap.MOTION_BLOCKING_NO_LEAVES))
            .thenReturn(bedrock);
        when(world.getBlockAt(eq(8), anyInt(), eq(8))).thenAnswer(invocation -> {
            int y = invocation.getArgument(1, Integer.class);
            if (y == 64) {
                return stone;
            }
            if (y == 65 || y == 66) {
                return air;
            }
            return block(openMaterial, y, true, false);
        });

        Location destination = new SafeTeleportDestinationResolver().resolve(world, 8, 8);

        assertEquals(8.5D, destination.getX());
        assertEquals(65D, destination.getY());
        assertEquals(8.5D, destination.getZ());
    }

    @Test
    void worldBorderContainmentIsInclusiveAtMinimumAndExclusiveAtMaximum() {
        HeatmapWorldRef world = new HeatmapWorldRef(
            UUID.randomUUID(),
            "minecraft:overworld",
            "world",
            0,
            0,
            0D,
            0D,
            100D
        );

        assertTrue(BukkitPlayerBackend.contains(world, -50D, -50D));
        assertTrue(BukkitPlayerBackend.contains(world, 49.999D, 49.999D));
        assertFalse(BukkitPlayerBackend.contains(world, 50D, 0D));
        assertFalse(BukkitPlayerBackend.contains(world, 0D, 50D));
    }

    private static Block block(Material material, int y, boolean passable, boolean liquid) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.getY()).thenReturn(y);
        when(block.isPassable()).thenReturn(passable);
        when(block.isLiquid()).thenReturn(liquid);
        return block;
    }
}
