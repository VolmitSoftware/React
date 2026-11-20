package art.arcane.react.model;

import art.arcane.react.core.controller.LoadedChunkCoordinate;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.ReactWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaActionParamsTraversalTest {
  @AfterEach
  void unbind() {
    ReactPlatforms.unbind();
  }

  @Test
  void hundredThousandExplicitCoordinatesDrainInBoundedConstantTimeWaves() {
    UUID worldId = UUID.randomUUID();
    List<ChunkRef> targets = new ArrayList<>(100_000);
    for (int index = 0; index < 100_000; index++) {
      targets.add(new ChunkRef(worldId, index, -index));
    }
    AreaActionParams area = AreaActionParams.builder().allChunks(false).build();
    area.setChunks(targets);

    assertEquals(100_000, area.estimatedTotalWork());
    int drained = 0;
    List<ChunkRef> wave;
    do {
      wave = area.popChunks(Integer.MAX_VALUE);
      assertTrue(wave.size() <= 256);
      drained += wave.size();
    } while (!wave.isEmpty());

    assertEquals(100_000, drained);
    assertNull(area.popChunk());
  }

  @Test
  void allChunksWaitForTheLoadedIndexThenStreamTheSelectedWorld() {
    UUID worldId = UUID.randomUUID();
    ReactWorld world = Mockito.mock(ReactWorld.class);
    Mockito.when(world.id()).thenReturn(worldId);
    AtomicBoolean ready = new AtomicBoolean(false);
    ChunkHost chunks = Mockito.mock(ChunkHost.class);
    Mockito.when(chunks.isLoadedIndexReady()).thenAnswer(ignored -> ready.get());
    Mockito.when(chunks.world("test:world")).thenReturn(Optional.of(world));
    List<ChunkRef> loaded = List.of(new ChunkRef(worldId, 1, 2), new ChunkRef(worldId, 3, 4));
    Mockito.when(chunks.openLoadedCursor(worldId)).thenReturn(new ChunkHost.LoadedChunkCursor() {
      private boolean drained;

      @Override
      public List<ChunkRef> next(int maximum) {
        if (drained) {
          return List.of();
        }
        drained = true;
        return loaded;
      }

      @Override
      public int estimatedTotal() {
        return loaded.size();
      }
    });
    bind(chunks);

    AreaActionParams area = AreaActionParams.builder().build();
    area.setWorld("test:world");
    assertTrue(area.isSelectionPending());
    assertNull(area.popChunk());

    ready.set(true);
    assertFalse(area.isSelectionPending());
    assertEquals(2, area.estimatedTotalWork());
    assertEquals(loaded, area.popChunks(10));
    assertTrue(area.popChunks(10).isEmpty());
    Mockito.verify(chunks, Mockito.never()).openLoadedCursor();
  }

  @Test
  void radiusSelectionKeepsLoadedCoordinatesAndNamesTheWorld() {
    UUID worldId = UUID.randomUUID();
    ReactWorld world = Mockito.mock(ReactWorld.class);
    Mockito.when(world.id()).thenReturn(worldId);
    ChunkHost chunks = Mockito.mock(ChunkHost.class);
    Mockito.when(chunks.worldKey(world)).thenReturn("test:world");
    Mockito.when(chunks.loadedInRadius(worldId, 0, 0, 10)).thenReturn(List.of(
        new LoadedChunkCoordinate(2, -3)));
    bind(chunks);

    AreaActionParams area = AreaActionParams.builder().build();
    area.selectLoadedRadius(world, 0, 0, 40);

    assertEquals("test:world", area.getWorld());
    assertFalse(area.isAllChunks());
    assertEquals(List.of(new ChunkRef(worldId, 2, -3)), area.popChunks(5));
  }

  private static void bind(ChunkHost chunks) {
    ReactPlatform platform = Mockito.mock(ReactPlatform.class);
    Mockito.when(platform.chunkHost()).thenReturn(Optional.of(chunks));
    ReactPlatforms.bind(platform);
  }
}
