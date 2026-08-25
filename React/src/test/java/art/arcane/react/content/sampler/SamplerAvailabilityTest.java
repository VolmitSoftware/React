package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.ReactCachedSampler;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

class SamplerAvailabilityTest {
  @Test
  void unsupportedWorldMetricsBecomeUnavailableAndResetOnStart() {
    World blockEntitiesWorld = Mockito.mock(World.class);
    World tickingBlockEntitiesWorld = Mockito.mock(World.class);
    World forceLoadedWorld = Mockito.mock(World.class);
    World chunkTicketsWorld = Mockito.mock(World.class);
    Mockito.when(blockEntitiesWorld.getTileEntityCount()).thenThrow(UnsupportedOperationException.class);
    Mockito.when(tickingBlockEntitiesWorld.getTickableTileEntityCount()).thenThrow(UnsupportedOperationException.class);
    Mockito.when(forceLoadedWorld.getForceLoadedChunks()).thenThrow(UnsupportedOperationException.class);
    Mockito.when(chunkTicketsWorld.getPluginChunkTickets()).thenThrow(UnsupportedOperationException.class);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(J::isPrimaryThread).thenReturn(true);
      assertUnavailableAfterFailure(new SamplerBlockEntities(), blockEntitiesWorld, bukkit);
      assertUnavailableAfterFailure(new SamplerBlockEntitiesTicking(), tickingBlockEntitiesWorld, bukkit);
      assertUnavailableAfterFailure(new SamplerChunksForceLoaded(), forceLoadedWorld, bukkit);
      assertUnavailableAfterFailure(new SamplerChunkTickets(), chunkTicketsWorld, bukkit);
    }
  }

  @Test
  void unsupportedBukkitSchedulerMetricBecomesUnavailableAndResetsOnStart() {
    BukkitScheduler scheduler = Mockito.mock(BukkitScheduler.class);
    Mockito.when(scheduler.getPendingTasks()).thenThrow(UnsupportedOperationException.class);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
      SamplerBukkitPendingTasks sampler = new SamplerBukkitPendingTasks();
      sampler.start();

      Assertions.assertTrue(sampler.isSampleAvailable());
      Assertions.assertEquals(0D, sampler.onSample());
      Assertions.assertFalse(sampler.isSampleAvailable());

      sampler.start();
      Assertions.assertTrue(sampler.isSampleAvailable());
    }
  }

  private static void assertUnavailableAfterFailure(
      ReactCachedSampler sampler,
      World world,
      MockedStatic<Bukkit> bukkit
  ) {
    bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
    sampler.start();

    Assertions.assertTrue(sampler.isSampleAvailable());
    Assertions.assertEquals(0D, sampler.onSample());
    Assertions.assertFalse(sampler.isSampleAvailable());

    sampler.start();
    Assertions.assertTrue(sampler.isSampleAvailable());
  }
}
