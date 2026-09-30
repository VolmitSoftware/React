package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.model.CostSnapshot;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.testutil.Fakes;
import art.arcane.react.util.common.scheduling.Ticker;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ObserverControllerCostDecayTest {
  private React previous;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void observerTicksDecayAnIdleChunkBelowOnePercentAfterEightPasses() {
    World world = Fakes.world("cadence");
    Chunk chunk = Fakes.chunk(world, 2, 2);
    Sampler physics = sampler("physics", false);
    ObserverController controller = new ObserverController();
    controller.get(chunk, physics).addAndGet(100D);

    for (int pass = 0; pass < 8; pass++) {
      controller.onTick();
    }

    Assertions.assertTrue(controller.sample(chunk, physics).orElse(0D) < 1D);
  }

  @Test
  void gaugeSamplerCounterStaysFiftyAcrossTenObserverTicks() {
    World world = Fakes.world("gauge");
    Chunk chunk = Fakes.chunk(world, -3, 7);
    Sampler entities = sampler("entities", true);
    Sampler physics = sampler("physics", false);
    ObserverController controller = new ObserverController();
    controller.get(chunk, entities).set(50D);
    controller.get(chunk, physics).addAndGet(1D);

    for (int pass = 0; pass < 10; pass++) {
      controller.onTick();
      controller.get(chunk, physics).addAndGet(1D);
    }

    Assertions.assertEquals(50D, controller.sample(world, -3, 7, entities).orElse(0D));
  }

  @Test
  void gaugeWrittenThroughWorldCoordinatesStaysPresentAcrossTenObserverTicks() {
    World world = Fakes.world("gauge-coordinates");
    Sampler entities = sampler("entities", true);
    ObserverController controller = new ObserverController();
    AtomicDouble counter = controller.get(world, 6, -2, entities);
    counter.set(50D);

    for (int pass = 0; pass < 10; pass++) {
      controller.onTick();
    }

    Assertions.assertEquals(50D, controller.sample(world, 6, -2, entities).orElse(0D));
    Assertions.assertSame(counter, controller.get(world, 6, -2, entities));
  }

  @Test
  void observerTickPublishesTheCostSnapshotUsedForTheWorstChunk() {
    World world = Fakes.world("snapshot");
    Chunk hot = Fakes.chunk(world, 4, 4);
    Chunk warm = Fakes.chunk(world, 5, 4);
    Sampler physics = sampler("physics", false);
    ObserverController controller = new ObserverController();
    controller.get(hot, physics).addAndGet(40D);
    controller.get(warm, physics).addAndGet(10D);

    Assertions.assertEquals(0D, controller.costSnapshot().total());
    Assertions.assertNull(controller.absoluteWorst());

    controller.onTick();

    CostSnapshot snapshot = controller.costSnapshot();
    Assertions.assertEquals(50D, snapshot.total());
    Assertions.assertEquals(40D, snapshot.maxChunk());
    Assertions.assertEquals(50D, snapshot.maxWorld());
    SampledChunk worst = controller.absoluteWorst();
    Assertions.assertEquals(4, worst.getChunkX());
    Assertions.assertEquals(4, worst.getChunkZ());
  }

  @Test
  void anIdleChunkStopsBeingTheWorstOnceAnActiveChunkOvertakesItsDecayedScore() {
    World world = Fakes.world("overtake");
    Chunk idle = Fakes.chunk(world, 0, 0);
    Chunk active = Fakes.chunk(world, 9, 9);
    Sampler physics = sampler("physics", false);
    ObserverController controller = new ObserverController();
    controller.get(idle, physics).addAndGet(1_000D);

    for (int pass = 0; pass < 8; pass++) {
      controller.get(active, physics).addAndGet(20D);
      controller.onTick();
    }

    SampledChunk worst = controller.absoluteWorst();
    Assertions.assertEquals(9, worst.getChunkX());
    Assertions.assertEquals(9, worst.getChunkZ());
  }

  @Test
  void stopClearsThePublishedCostSnapshot() {
    World world = Fakes.world("stop");
    Sampler physics = sampler("physics", false);
    ObserverController controller = new ObserverController();
    controller.get(Fakes.chunk(world, 1, 1), physics).addAndGet(10D);
    controller.onTick();

    controller.stop();

    Assertions.assertSame(CostSnapshot.EMPTY, controller.costSnapshot());
  }

  private static Sampler sampler(String id, boolean gauge) {
    Sampler sampler = Mockito.mock(Sampler.class);
    Mockito.when(sampler.getId()).thenReturn(id);
    Mockito.when(sampler.isChunkGauge()).thenReturn(gauge);
    return sampler;
  }
}
