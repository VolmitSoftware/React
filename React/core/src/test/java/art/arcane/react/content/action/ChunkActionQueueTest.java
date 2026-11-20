package art.arcane.react.content.action;

import art.arcane.react.model.SampledChunk;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class ChunkActionQueueTest {
  @AfterEach
  void unbind() {
    ReactPlatforms.unbind();
  }

  @Test
  void quarantineQueueRanksHotChunksAndNeighborsWithinTheWorldFilter() throws ReflectiveOperationException {
    bind(List.of(
        chunk("minecraft:overworld", 0, 0, 200D),
        chunk("minecraft:overworld", 10, 10, 95D),
        chunk("minecraft:overworld", 20, 20, 20D),
        chunk("minecraft:the_nether", 0, 0, 500D)
    ));
    ActionQuarantineHotChunks.Params params = ActionQuarantineHotChunks.Params.builder()
        .world("minecraft:overworld")
        .maxChunks(3)
        .build();

    Method prepare = ActionQuarantineHotChunks.class.getDeclaredMethod("prepare", ActionQuarantineHotChunks.Params.class);
    prepare.setAccessible(true);
    prepare.invoke(new ActionQuarantineHotChunks(), params);

    Assertions.assertEquals(3, params.getQueue().size());
    List<Object> queue = new ArrayList<>(params.getQueue());
    Assertions.assertEquals("ChunkRef[world=minecraft:overworld, x=0, z=0]", queue.get(0).toString());
    for (Object neighbor : queue.subList(1, 3)) {
      String text = neighbor.toString();
      Assertions.assertTrue(text.startsWith("ChunkRef[world=minecraft:overworld, x="), text);
      Assertions.assertTrue(text.matches(".*x=-?[01], z=-?[01]\\]"), text);
    }
  }

  @Test
  void prewarmQueueSpreadsDecayingWeightToNeighborsAndHonorsTheLimit() throws ReflectiveOperationException {
    bind(List.of(chunk("minecraft:overworld", 4, 4, 100D)));
    ActionPrewarmCriticalChunks.Params params = ActionPrewarmCriticalChunks.Params.builder()
        .includePlayerChunks(false)
        .neighborRadius(1)
        .maxChunks(5)
        .build();

    Method build = ActionPrewarmCriticalChunks.class.getDeclaredMethod("buildQueue", ActionPrewarmCriticalChunks.Params.class);
    build.setAccessible(true);
    List<?> queue = (List<?>) build.invoke(new ActionPrewarmCriticalChunks(), params);

    Assertions.assertEquals(5, queue.size());
    Assertions.assertEquals("ChunkRef[world=minecraft:overworld, x=4, z=4]", queue.get(0).toString());
    for (Object neighbor : queue.subList(1, 5)) {
      String text = neighbor.toString();
      Assertions.assertTrue(text.contains("x=4") || text.contains("z=4"), text);
    }
  }

  private static SampledChunk chunk(String worldKey, int x, int z, double score) {
    SampledChunk chunk = new SampledChunk(UUID.nameUUIDFromBytes(worldKey.getBytes()), worldKey, x, z);
    chunk.get("score").set(score);
    return chunk;
  }

  private static void bind(List<SampledChunk> sampled) {
    ChunkHost chunks = Mockito.mock(ChunkHost.class);
    Mockito.when(chunks.sampledChunks()).thenReturn(sampled);
    ReactPlatform platform = Mockito.mock(ReactPlatform.class);
    Mockito.when(platform.chunkHost()).thenReturn(Optional.of(chunks));
    ReactPlatforms.bind(platform);
  }
}
