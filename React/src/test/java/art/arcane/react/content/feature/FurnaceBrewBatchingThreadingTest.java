package art.arcane.react.content.feature;

import art.arcane.react.util.common.scheduling.J;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;

class FurnaceBrewBatchingThreadingTest {

  @Test
  void chunkMeasurementAlwaysUsesOwningChunkScheduler() throws Exception {
    FeatureFurnaceBrewBatching feature = new FeatureFurnaceBrewBatching();
    World world = Mockito.mock(World.class);
    Method dispatch = FeatureFurnaceBrewBatching.class.getDeclaredMethod(
        "dispatchChunkMeasurement",
        World.class,
        int.class,
        int.class,
        List.class
    );
    dispatch.setAccessible(true);

    try (MockedStatic<J> scheduler = Mockito.mockStatic(J.class)) {
      dispatch.invoke(feature, world, 11, -7, List.of());

      scheduler.verify(() -> J.runChunk(
          Mockito.same(world),
          Mockito.eq(11),
          Mockito.eq(-7),
          Mockito.any(Runnable.class)
      ));
    }
  }
}
