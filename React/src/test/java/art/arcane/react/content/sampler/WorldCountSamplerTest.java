package art.arcane.react.content.sampler;

import art.arcane.react.util.project.world.WorldEntitySnapshots;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

class WorldCountSamplerTest {
  @Test
  void entityCountUsesWorldCountersWithoutPlayerFanout() {
    World first = Mockito.mock(World.class);
    World second = Mockito.mock(World.class);

    try (MockedStatic<WorldEntitySnapshots> snapshots = Mockito.mockStatic(WorldEntitySnapshots.class)) {
      snapshots.when(() -> WorldEntitySnapshots.count(first)).thenReturn(1200);
      snapshots.when(() -> WorldEntitySnapshots.count(second)).thenReturn(3400);

      Assertions.assertEquals(4600, SamplerEntities.countWorldEntities(List.of(first, second)));
    }
  }

  @Test
  void chunkCountUsesWorldCountersWithoutCoordinateMaterialization() {
    World first = Mockito.mock(World.class);
    World second = Mockito.mock(World.class);

    try (MockedStatic<WorldEntitySnapshots> snapshots = Mockito.mockStatic(WorldEntitySnapshots.class)) {
      snapshots.when(() -> WorldEntitySnapshots.chunkCount(first)).thenReturn(4096);
      snapshots.when(() -> WorldEntitySnapshots.chunkCount(second)).thenReturn(8192);

      Assertions.assertEquals(12_288, SamplerChunks.countWorldChunks(List.of(first, second)));
    }
  }

  @Test
  void countersClampOverflow() {
    World first = Mockito.mock(World.class);
    World second = Mockito.mock(World.class);

    try (MockedStatic<WorldEntitySnapshots> snapshots = Mockito.mockStatic(WorldEntitySnapshots.class)) {
      snapshots.when(() -> WorldEntitySnapshots.count(first)).thenReturn(Integer.MAX_VALUE);
      snapshots.when(() -> WorldEntitySnapshots.count(second)).thenReturn(1);
      snapshots.when(() -> WorldEntitySnapshots.chunkCount(first)).thenReturn(Integer.MAX_VALUE);
      snapshots.when(() -> WorldEntitySnapshots.chunkCount(second)).thenReturn(1);

      Assertions.assertEquals(Integer.MAX_VALUE, SamplerEntities.countWorldEntities(List.of(first, second)));
      Assertions.assertEquals(Integer.MAX_VALUE, SamplerChunks.countWorldChunks(List.of(first, second)));
    }
  }
}
