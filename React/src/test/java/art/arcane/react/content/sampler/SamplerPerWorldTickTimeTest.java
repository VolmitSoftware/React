package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

class SamplerPerWorldTickTimeTest {
  @Test
  void worldShareNeverExceedsTheTickTimeItSplits() {
    World busy = world();
    World quiet = world();
    Sampler tickTime = Mockito.mock(Sampler.class);
    Mockito.when(tickTime.sample()).thenReturn(20D, 5D, 8D);
    SamplerPerWorldTickTime sampler = new SamplerPerWorldTickTime();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<WorldEntitySnapshots> snapshots = Mockito.mockStatic(WorldEntitySnapshots.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(tickTime);
      scheduling.when(J::isPrimaryThread).thenReturn(true);
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(busy, quiet));
      snapshots.when(() -> WorldEntitySnapshots.count(busy)).thenReturn(300);
      snapshots.when(() -> WorldEntitySnapshots.chunkCount(busy)).thenReturn(100);
      snapshots.when(() -> WorldEntitySnapshots.count(quiet)).thenReturn(60);
      snapshots.when(() -> WorldEntitySnapshots.chunkCount(quiet)).thenReturn(40);

      Assertions.assertEquals(16D, sampler.onSample(), 1e-9);
      Assertions.assertEquals(4D, sampler.onSample(), 1e-9);
      Assertions.assertEquals(4D, SamplerPerWorldTickTime.meanMsFor(busy), 1e-9);
      Assertions.assertEquals(1D, SamplerPerWorldTickTime.meanMsFor(quiet), 1e-9);
      Assertions.assertEquals(6.4D, sampler.onSample(), 1e-9);
    }
  }

  private static World world() {
    World world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    return world;
  }
}
