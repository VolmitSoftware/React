package art.arcane.react.model;

import com.google.common.util.concurrent.AtomicDouble;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class SampledChunkDecayTest {
  @Test
  void chunkChargedOnceReadsUnderOnePercentAfterEightDecayPasses() {
    SampledChunk chunk = chunk();
    chunk.get("physics").addAndGet(100D);

    for (int pass = 0; pass < 8; pass++) {
      chunk.decay();
    }

    Assertions.assertTrue(value(chunk, "physics") < 1D);
  }

  @Test
  void eachDecayPassHalvesEveryCounterOnce() {
    SampledChunk chunk = chunk();
    chunk.get("physics").set(64D);
    chunk.get("fluid").set(8D);

    chunk.decay();

    Assertions.assertEquals(32D, value(chunk, "physics"));
    Assertions.assertEquals(4D, value(chunk, "fluid"));
  }

  @Test
  void gaugeOnAStaticFiftyMobChunkStaysFiftyAcrossTenPasses() {
    SampledChunk chunk = chunk();
    chunk.gauge("entities").set(50D);

    for (int pass = 0; pass < 10; pass++) {
      chunk.decay();
    }

    Assertions.assertEquals(50D, value(chunk, "entities"));
  }

  @Test
  void gaugeKeepsItsCounterInstanceWhenItReachesZero() {
    SampledChunk chunk = chunk();
    AtomicDouble gauge = chunk.gauge("entities");
    gauge.set(0D);

    chunk.decay();

    Assertions.assertSame(gauge, chunk.gauge("entities"));
    Assertions.assertFalse(chunk.isEmpty());
  }

  @Test
  void decayPrunesCountersUnderOneHundredth() {
    SampledChunk chunk = chunk();
    chunk.get("fluid").set(0.015D);
    chunk.get("physics").set(10D);

    chunk.decay();

    Assertions.assertTrue(chunk.optional("fluid").isEmpty());
    Assertions.assertEquals(5D, value(chunk, "physics"));
    Assertions.assertFalse(chunk.isEmpty());
  }

  @Test
  void worldDecayDropsChunksWithNoRemainingCounters() {
    SampledWorld world = new SampledWorld(UUID.randomUUID(), "test:decay");
    world.getChunk(1, 1).get("physics").set(0.015D);
    world.getChunk(2, 2).gauge("entities").set(0D);
    world.getChunk(3, 3).get("physics").set(8D);

    world.decay();

    Assertions.assertTrue(world.optionalChunk(1, 1).isEmpty());
    Assertions.assertTrue(world.optionalChunk(2, 2).isPresent());
    Assertions.assertEquals(4D, value(world.optionalChunk(3, 3).orElseThrow(), "physics"));
  }

  private static SampledChunk chunk() {
    return new SampledChunk(UUID.randomUUID(), "test:decay", 0, 0);
  }

  private static double value(SampledChunk chunk, String key) {
    return chunk.optional(key).map(AtomicDouble::get).orElse(0D);
  }
}
