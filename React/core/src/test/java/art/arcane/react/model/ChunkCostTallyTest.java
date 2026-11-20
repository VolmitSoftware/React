package art.arcane.react.model;

import art.arcane.react.platform.RuntimeStatsHost;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkCostTallyTest {
  @Test
  void captureAndDecay_sumsChunksAndWorlds_reportsTotalsAndMaxima() {
    Map<Long, SampledChunk> overworld = new ConcurrentHashMap<>();
    overworld.put(1L, chunk("overworld", 0, 0, "redstone", 4D));
    overworld.put(2L, chunk("overworld", 1, 0, "redstone", 6D));
    Map<Long, SampledChunk> nether = new ConcurrentHashMap<>();
    nether.put(3L, chunk("nether", 0, 0, "hopper", 8D));

    RuntimeStatsHost.CostShares shares = ChunkCostTally.captureAndDecay(List.of(overworld, nether));

    assertEquals(18D, shares.total(), 1.0E-9D);
    assertEquals(8D, shares.maxChunk(), 1.0E-9D);
    assertEquals(10D, shares.maxWorld(), 1.0E-9D);
  }

  @Test
  void captureAndDecay_halvesCounters_dropsEmptiedChunks() {
    Map<Long, SampledChunk> world = new ConcurrentHashMap<>();
    world.put(1L, chunk("world", 0, 0, "redstone", 8D));
    world.put(2L, chunk("world", 1, 0, "redstone", 0.015D));

    ChunkCostTally.captureAndDecay(List.of(world));

    assertEquals(4D, world.get(1L).get("redstone").get(), 1.0E-9D);
    assertFalse(world.containsKey(2L));
    RuntimeStatsHost.CostShares next = ChunkCostTally.captureAndDecay(List.of(world));
    assertEquals(4D, next.total(), 1.0E-9D);
  }

  @Test
  void captureAndDecay_keepsGaugesAtFullValue() {
    Map<Long, SampledChunk> world = new ConcurrentHashMap<>();
    SampledChunk chunk = new SampledChunk(UUID.randomUUID(), "world", 0, 0);
    chunk.gauge("entities").set(5D);
    world.put(1L, chunk);

    ChunkCostTally.captureAndDecay(List.of(world));
    RuntimeStatsHost.CostShares second = ChunkCostTally.captureAndDecay(List.of(world));

    assertEquals(5D, second.total(), 1.0E-9D);
    assertTrue(world.containsKey(1L));
  }

  @Test
  void captureAndDecay_noWorlds_isEmpty() {
    assertEquals(ChunkCostTally.EMPTY, ChunkCostTally.captureAndDecay(List.of()));
  }

  private static SampledChunk chunk(String world, int x, int z, String sampler, double value) {
    SampledChunk chunk = new SampledChunk(UUID.randomUUID(), world, x, z);
    chunk.get(sampler).set(value);
    return chunk;
  }
}
