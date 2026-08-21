package art.arcane.react.api.web.heatmap;

import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public class BukkitHeatmapChunkSampler implements HeatmapChunkSampler {

    @Override
    public HeatmapScan scan(ChunkGridExporter exporter, String requestedWorld, Integer centerX, Integer centerZ, int radius) {
        World world;
        if (requestedWorld != null && !requestedWorld.isBlank()) {
            try {
                world = WorldIdentity.resolve(requestedWorld).orElse(null);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        } else {
            List<World> worlds = Bukkit.getWorlds();
            world = worlds.isEmpty() ? null : worlds.get(0);
        }
        if (world == null) {
            return null;
        }
        int centerChunkX = centerX != null ? centerX : (world.getSpawnLocation().getBlockX() >> 4);
        int centerChunkZ = centerZ != null ? centerZ : (world.getSpawnLocation().getBlockZ() >> 4);
        Location anchor = new Location(world, (centerChunkX << 4) + 8, 64, (centerChunkZ << 4) + 8);
        List<HeatmapCellDto> cells = J.runRegionResult(anchor, () -> {
            List<HeatmapCellDto> result = new ArrayList<>();
            for (Chunk c : world.getLoadedChunks()) {
                int dx = c.getX() - centerChunkX;
                int dz = c.getZ() - centerChunkZ;
                if ((dx * dx) + (dz * dz) > radius * radius) {
                    continue;
                }
                double s = exporter.scoreChunk(c);
                if (s > 0D) {
                    result.add(new HeatmapCellDto(c.getX(), c.getZ(), s));
                }
            }
            return result;
        }, List.of());
        return new HeatmapScan(WorldIdentity.serialize(world), centerChunkX, centerChunkZ, cells);
    }
}
