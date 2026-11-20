package art.arcane.react.api.web.heatmap;

import art.arcane.react.core.controller.LoadedChunkCoordinate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeatmapGridScannerTest {
  private static final UUID WORLD_ID = UUID.randomUUID();
  private static final HeatmapWorldRef WORLD = new HeatmapWorldRef(WORLD_ID, "minecraft:overworld", "minecraft:overworld", 2, -1,
      0D, 0D, 60_000_000D);

  @Test
  void scan_withoutCenter_centersOnSpawnChunk_scoresLoadedChunksOnly() {
    List<int[]> requested = new ArrayList<>();
    HeatmapScan scan = new HeatmapGridScanner().scan(exporter(), WORLD, null, null, 8,
        (UUID worldId, int minX, int maxX, int minZ, int maxZ) -> {
          requested.add(new int[]{minX, maxX, minZ, maxZ});
          return List.of(new LoadedChunkCoordinate(2, -1), new LoadedChunkCoordinate(3, 0));
        });

    assertEquals(1, requested.size());
    assertEquals(2, (requested.get(0)[0] + requested.get(0)[1]) / 2);
    assertEquals(-1, Math.floorDiv(requested.get(0)[2] + requested.get(0)[3], 2));
    assertEquals(2, scan.cells().size());
    assertEquals(5D, scan.cells().get(0).score + scan.cells().get(1).score, 1.0E-9D);
    assertEquals(WORLD, scan.world());
  }

  @Test
  void scan_negativeAndNonFiniteScores_countAsZeroSamples() {
    ChunkGridExporter broken = new ChunkGridExporter() {
      @Override
      public String heatmapId() {
        return "broken";
      }

      @Override
      public String heatmapLabel() {
        return "Broken";
      }

      @Override
      public double scoreChunk(HeatmapWorldRef world, int chunkX, int chunkZ) {
        return chunkX == 0 ? Double.NaN : -4D;
      }
    };

    HeatmapScan scan = new HeatmapGridScanner().scan(broken, WORLD, 0, 0, 8,
        (UUID worldId, int minX, int maxX, int minZ, int maxZ) -> List.of(new LoadedChunkCoordinate(0, 0), new LoadedChunkCoordinate(1, 0)));

    double total = 0D;
    int samples = 0;
    for (HeatmapCellDto cell : scan.cells()) {
      total += cell.score;
      samples += cell.samples;
    }
    assertEquals(0D, total, 1.0E-9D);
    assertEquals(2, samples);
  }

  private static ChunkGridExporter exporter() {
    return new ChunkGridExporter() {
      @Override
      public String heatmapId() {
        return "test";
      }

      @Override
      public String heatmapLabel() {
        return "Test";
      }

      @Override
      public double scoreChunk(HeatmapWorldRef world, int chunkX, int chunkZ) {
        return chunkX;
      }
    };
  }
}
