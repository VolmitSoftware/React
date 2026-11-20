package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.platform.ReactPlatform;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.platform.TickStatsHost;
import art.arcane.react.platform.timing.WorldTickTimes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SamplerPerWorldTickTimeMeasuredTest {
  private static final UUID OVERWORLD = new UUID(0L, 1L);
  private static final UUID NETHER = new UUID(0L, 2L);

  @AfterEach
  void unbind() {
    ReactPlatforms.unbind();
  }

  @Test
  void measuredTimesFeedTheBudgetSeriesAndReportMeasured() {
    WorldTickTimes times = new WorldTickTimes();
    times.publish(List.of(
        WorldTickTimes.World.measured(OVERWORLD, "minecraft:overworld", 20, 30_000_000L, 2_000_000L),
        WorldTickTimes.World.measured(NETHER, "minecraft:the_nether", 20, 90_000_000L, 9_000_000L)));
    TickStatsHost ticks = Mockito.mock(TickStatsHost.class);
    Mockito.when(ticks.worldTickTimes()).thenReturn(Optional.of(times));
    ReactPlatform platform = Mockito.mock(ReactPlatform.class);
    Mockito.when(platform.tickStatsHost()).thenReturn(Optional.of(ticks));
    ReactPlatforms.bind(platform);

    SamplerPerWorldTickTime sampler = new SamplerPerWorldTickTime();
    assertEquals(4.5D, sampler.onSample(), 1.0E-9D);
    assertEquals(1.5D, SamplerPerWorldTickTime.meanMsFor(OVERWORLD), 1.0E-9D);
    assertEquals(4.5D, SamplerPerWorldTickTime.meanMsFor(NETHER), 1.0E-9D);
    assertEquals(Sampler.Quality.MEASURED, sampler.metadata().quality());
    assertEquals("measured", SamplerPerWorldTickTime.measurementMode());

    times.publish(List.of(WorldTickTimes.World.measured(OVERWORLD, "minecraft:overworld", 20, 30_000_000L, 2_000_000L)));
    sampler.onSample();
    assertEquals(0D, SamplerPerWorldTickTime.meanMsFor(NETHER), "an unloaded dimension leaves the budget");
    sampler.stop();
  }
}
