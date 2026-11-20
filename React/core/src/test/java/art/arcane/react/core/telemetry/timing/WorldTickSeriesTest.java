package art.arcane.react.core.telemetry.timing;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.platform.timing.WorldTickTimes;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTickSeriesTest {
  @Test
  void samplerIdsAreSafeForEveryDimensionKey() {
    assertEquals("world-tick-minecraft-the_nether", WorldTickSeries.samplerId("minecraft:the_nether"));
    assertEquals("world-tick-twilightforest-twilight_forest", WorldTickSeries.samplerId("TwilightForest:Twilight_Forest"));
    assertTrue(WorldTickSeries.isWorldTickId(WorldTickSeries.samplerId("a:b")));
  }

  @Test
  void registersEveryDimensionAndDropsUnloadedOnes() {
    SampleController samples = Mockito.mock(SampleController.class);
    List<Sampler> registered = new ArrayList<>();
    Mockito.when(samples.registerSampler(Mockito.any())).thenAnswer(invocation -> registered.add(invocation.getArgument(0)));
    WorldTickTimes times = new WorldTickTimes();
    WorldTickSeries series = new WorldTickSeries(times);

    List<WorldTickTimes.World> worlds = IntStream.range(0, 12)
        .mapToObj((int index) -> WorldTickTimes.World.measured(new UUID(0L, index), "pack:dim_" + index, 20, (index + 1) * 20_000_000L, 0L))
        .toList();
    times.publish(worlds);
    series.sync(samples);
    series.sync(samples);
    assertEquals(12, registered.size(), "one sampler per dimension, registered once");

    Sampler busiest = registered.stream().filter((Sampler sampler) -> sampler.getId().equals("world-tick-pack-dim_11")).findFirst().orElseThrow();
    busiest.start();
    assertEquals("pack:dim_11", busiest.getName());
    assertEquals(12D, ((WorldTickSampler) busiest).onSample(), 1.0E-9D);
    assertEquals(Sampler.Quality.MEASURED, busiest.metadata().quality());

    times.publish(worlds.subList(0, 11));
    series.sync(samples);
    Mockito.verify(samples).unregisterSampler("world-tick-pack-dim_11");
  }
}
