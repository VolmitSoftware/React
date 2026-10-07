package art.arcane.react.model;

import art.arcane.react.testutil.Fakes;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

class CostSnapshotTest {
  private static final List<String> KEYS = List.of("physics", "fluid", "redstone", "hopper", "chunks-loaded");

  @Test
  void snapshotMatchesHandComputedTotalsOnASeededServer() {
    SampledServer server = new SampledServer();
    SampledWorld alpha = server.getWorld(Fakes.world("alpha"));
    SampledWorld beta = server.getWorld(Fakes.world("beta"));
    alpha.getChunk(0, 0).get("physics").set(10D);
    alpha.getChunk(0, 0).get("fluid").set(5D);
    alpha.getChunk(1, 0).get("redstone").set(30D);
    beta.getChunk(5, 5).get("hopper").set(12D);
    beta.getChunk(6, 5).get("physics").set(20D);
    beta.getChunk(7, 5).gauge("entities").set(18D);

    CostSnapshot snapshot = CostSnapshot.capture(server);

    Assertions.assertEquals(95D, snapshot.total());
    Assertions.assertEquals(30D, snapshot.maxChunk());
    Assertions.assertEquals(50D, snapshot.maxWorld());
    Assertions.assertSame(alpha.getChunk(1, 0), snapshot.worstChunk());
  }

  @Test
  void snapshotMatchesThePerChunkScanOnARandomSeededServer() {
    Random random = new Random(0x5EEDL);
    SampledServer server = new SampledServer();
    for (int worldIndex = 0; worldIndex < 4; worldIndex++) {
      World world = Fakes.world("seeded" + worldIndex);
      SampledWorld sampledWorld = server.getWorld(world);
      int chunks = 1 + random.nextInt(60);
      for (int chunk = 0; chunk < chunks; chunk++) {
        SampledChunk sampledChunk = sampledWorld.getChunk(random.nextInt(64) - 32, random.nextInt(64) - 32);
        for (String key : KEYS) {
          if (random.nextBoolean()) {
            sampledChunk.get(key).addAndGet(random.nextDouble() * 500D);
          }
        }
      }
    }

    CostSnapshot snapshot = CostSnapshot.capture(server);

    double total = 0D;
    double maxChunk = 0D;
    double maxWorld = 0D;
    for (SampledWorld world : server.getWorlds().values()) {
      double worldScore = 0D;
      for (SampledChunk chunk : world.getChunks().values()) {
        double chunkScore = chunk.totalScore();
        worldScore += chunkScore;
        total += chunkScore;
        maxChunk = Math.max(maxChunk, chunkScore);
      }
      maxWorld = Math.max(maxWorld, worldScore);
    }
    Assertions.assertEquals(total, snapshot.total(), 1.0E-9D);
    Assertions.assertEquals(maxChunk, snapshot.maxChunk(), 1.0E-9D);
    Assertions.assertEquals(maxWorld, snapshot.maxWorld(), 1.0E-9D);
    Assertions.assertEquals(maxChunk, snapshot.worstChunk().totalScore(), 1.0E-9D);
  }

  @Test
  void equalTotalsRankTheChunkWithTheHigherSingleCounterAsWorst() {
    SampledServer server = new SampledServer();
    SampledWorld world = server.getWorld(Fakes.world("ties"));
    world.getChunk(0, 0).get("physics").set(15D);
    world.getChunk(0, 0).get("fluid").set(15D);
    world.getChunk(9, 9).get("redstone").set(30D);

    Assertions.assertSame(world.getChunk(9, 9), CostSnapshot.capture(server).worstChunk());
  }

  @Test
  void captureAndDecayKeepsPreDecayRankingAndGauges() {
    SampledServer server = new SampledServer();
    SampledWorld world = server.getWorld(Fakes.world("decay"));
    SampledChunk split = world.getChunk(0, 0);
    split.get("physics").set(15D);
    split.get("fluid").set(15D);
    SampledChunk worst = world.getChunk(1, 0);
    worst.get("redstone").set(30D);
    world.getChunk(2, 0).gauge("entities").set(12D);
    world.getChunk(3, 0).get("physics").set(0.015D);

    CostSnapshot snapshot = CostSnapshot.captureAndDecay(server);

    Assertions.assertEquals(72.015D, snapshot.total(), 1.0E-9D);
    Assertions.assertSame(worst, snapshot.worstChunk());
    Assertions.assertEquals(15D, worst.totalScore());
    Assertions.assertEquals(12D, world.getChunk(2, 0).totalScore());
    Assertions.assertTrue(world.optionalChunk(3, 0).isEmpty());
  }

  @Test
  void emptyServerCapturesTheEmptySnapshot() {
    CostSnapshot snapshot = CostSnapshot.capture(new SampledServer());

    Assertions.assertEquals(0D, snapshot.total());
    Assertions.assertEquals(0D, snapshot.maxChunk());
    Assertions.assertEquals(0D, snapshot.maxWorld());
    Assertions.assertNull(snapshot.worstChunk());
  }
}
