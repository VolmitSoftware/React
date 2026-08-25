package art.arcane.react.api.web;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.EnumSet;
import java.util.Set;

public final class SafeTeleportDestinationResolver {

    private static final int CHUNK_SIZE = 16;
    private static final Set<Material> HAZARDOUS_FOOTING = EnumSet.of(
        Material.CACTUS,
        Material.CAMPFIRE,
        Material.FIRE,
        Material.MAGMA_BLOCK,
        Material.POWDER_SNOW,
        Material.SOUL_CAMPFIRE,
        Material.SOUL_FIRE,
        Material.SWEET_BERRY_BUSH,
        Material.WITHER_ROSE,
        Material.BEDROCK
    );

    public Location resolve(World world, int targetBlockX, int targetBlockZ) {
        int chunkX = Math.floorDiv(targetBlockX, CHUNK_SIZE);
        int chunkZ = Math.floorDiv(targetBlockZ, CHUNK_SIZE);
        int minimumBlockX = chunkX * CHUNK_SIZE;
        int minimumBlockZ = chunkZ * CHUNK_SIZE;
        for (int radius = 0; radius < CHUNK_SIZE; radius++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (radius > 0 && Math.abs(offsetX) != radius && Math.abs(offsetZ) != radius) {
                        continue;
                    }
                    int blockX = targetBlockX + offsetX;
                    int blockZ = targetBlockZ + offsetZ;
                    if (blockX < minimumBlockX
                        || blockX >= minimumBlockX + CHUNK_SIZE
                        || blockZ < minimumBlockZ
                        || blockZ >= minimumBlockZ + CHUNK_SIZE) {
                        continue;
                    }
                    Location destination = resolveColumn(world, blockX, blockZ);
                    if (destination != null) {
                        return destination;
                    }
                }
            }
        }
        return null;
    }

    private Location resolveColumn(World world, int blockX, int blockZ) {
        Block footing = world.getHighestBlockAt(
            blockX,
            blockZ,
            HeightMap.MOTION_BLOCKING_NO_LEAVES
        );
        Location destination = resolveFooting(world, footing, blockX, blockZ);
        if (destination != null || footing.getType() != Material.BEDROCK) {
            return destination;
        }
        for (int footingY = footing.getY() - 1; footingY >= world.getMinHeight(); footingY--) {
            destination = resolveFooting(
                world,
                world.getBlockAt(blockX, footingY, blockZ),
                blockX,
                blockZ
            );
            if (destination != null) {
                return destination;
            }
        }
        return null;
    }

    private Location resolveFooting(
        World world,
        Block footing,
        int blockX,
        int blockZ
    ) {
        int feetY = footing.getY() + 1;
        if (feetY < world.getMinHeight() || feetY + 1 >= world.getMaxHeight()) {
            return null;
        }
        Material footingType = footing.getType();
        if (HAZARDOUS_FOOTING.contains(footingType) || !footingType.isSolid()) {
            return null;
        }
        Block feet = world.getBlockAt(blockX, feetY, blockZ);
        Block head = world.getBlockAt(blockX, feetY + 1, blockZ);
        if (!feet.isPassable() || feet.isLiquid() || !head.isPassable() || head.isLiquid()) {
            return null;
        }
        return new Location(world, blockX + 0.5D, feetY, blockZ + 0.5D);
    }
}
