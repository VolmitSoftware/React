package art.arcane.react.content.tweak;

import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.BlockHostStub;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.util.common.scheduling.J;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class TweakFastFluidsTest {
  private final UUID worldId = UUID.randomUUID();
  private MockedStatic<J> scheduling;
  private AtomicReference<Runnable> flush;
  private List<Runnable> chunkTasks;
  private BlockHostStub host;
  private ReactWorld world;
  private TweakFastFluids tweak;

  @BeforeEach
  void setUp() {
    scheduling = Mockito.mockStatic(J.class);
    flush = new AtomicReference<>();
    chunkTasks = new ArrayList<>();
    scheduling.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(1))).thenAnswer(invocation -> {
      flush.set(invocation.getArgument(0));
      return 3;
    });
    scheduling.when(() -> J.runChunk(Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Runnable.class)))
        .thenAnswer(invocation -> {
          chunkTasks.add(invocation.getArgument(3));
          return true;
        });
    host = new BlockHostStub();
    world = Mockito.mock(ReactWorld.class);
    Mockito.when(world.id()).thenReturn(worldId);
    Mockito.when(host.worlds.world(worldId)).thenReturn(Optional.of(world));
    Mockito.when(host.blocks.canTickFluids()).thenReturn(true);
    Mockito.when(host.blocks.minHeight(worldId)).thenReturn(-64);
    Mockito.when(host.blocks.maxHeight(worldId)).thenReturn(320);
    Mockito.when(host.chunks.isLoaded(Mockito.eq(worldId), Mockito.anyInt(), Mockito.anyInt())).thenReturn(true);
    Mockito.when(host.blocks.tickFluid(Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean(),
        Mockito.anyBoolean())).thenReturn(true);
    tweak = new TweakFastFluids();
    tweak.onActivate();
  }

  @AfterEach
  void tearDown() {
    tweak.onDeactivate();
    host.close();
    scheduling.close();
  }

  @Test
  void pendingPulsesAreCappedWhileExistingPositionsStillAccumulate() {
    FluidPulseQueue queue = new FluidPulseQueue();
    for (int index = 0; index < FluidPulseQueue.MAX_PENDING_PULSES + 500; index++) {
      queue.enqueue(worldId, index, 64, 0, 1);
    }

    Assertions.assertEquals(FluidPulseQueue.MAX_PENDING_PULSES, queue.size());
    Assertions.assertTrue(queue.enqueue(worldId, 0, 64, 0, 3));
    Assertions.assertFalse(queue.enqueue(worldId, -1, 64, 0, 3));
  }

  @Test
  void drainedPulsesAreBucketedByChunkAndReleaseTheirSlots() {
    FluidPulseQueue queue = new FluidPulseQueue();
    queue.enqueue(worldId, 1, 64, 1, 2);
    queue.enqueue(worldId, 2, 64, 1, 2);
    queue.enqueue(worldId, 15, 64, 15, 2);
    queue.enqueue(worldId, 16, 64, 1, 2);

    Map<FluidPulseQueue.FluidChunk, List<FluidPulseQueue.FluidBurst>> buckets = queue.drain(256, 16);

    Assertions.assertEquals(2, buckets.size());
    Assertions.assertEquals(3, buckets.get(new FluidPulseQueue.FluidChunk(worldId, 0, 0)).size());
    Assertions.assertEquals(0, queue.size());
  }

  @Test
  void foliaFlushSchedulesOneTaskPerChunk() {
    scheduling.when(J::isFoliaThreading).thenReturn(true);
    host.flows.accept(flow(1, 64, 1));
    host.flows.accept(flow(20, 64, 1));

    flush.get().run();

    Assertions.assertEquals(2, chunkTasks.size());
    Mockito.verify(host.blocks, Mockito.never()).tickFluid(Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(),
        Mockito.anyBoolean(), Mockito.anyBoolean());
    for (Runnable task : chunkTasks) {
      task.run();
    }
    Mockito.verify(host.blocks, Mockito.atLeastOnce()).tickFluid(worldId, 1, 64, 1, true, true);
  }

  @Test
  void serverThreadFlushTicksFluidsInline() {
    scheduling.when(J::isFoliaThreading).thenReturn(false);
    host.flows.accept(flow(1, 64, 1));

    flush.get().run();

    Assertions.assertTrue(chunkTasks.isEmpty());
    Mockito.verify(host.blocks, Mockito.atLeastOnce()).tickFluid(worldId, 1, 64, 1, true, true);
  }

  @Test
  void flowsOfOtherBlocksQueueNothing() {
    BlockHost.FluidFlowEvent event = Mockito.mock(BlockHost.FluidFlowEvent.class);
    Mockito.when(event.fromType()).thenReturn("STONE");
    Mockito.when(event.toType()).thenReturn("AIR");

    host.flows.accept(event);
    flush.get().run();

    Mockito.verify(event, Mockito.never()).from();
    Mockito.verify(host.blocks, Mockito.never()).tickFluid(Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(),
        Mockito.anyBoolean(), Mockito.anyBoolean());
  }

  @Test
  void drainingLevelChangeAlsoQueuesTheNeighbours() {
    BlockHost.FluidLevelEvent event = Mockito.mock(BlockHost.FluidLevelEvent.class);
    Mockito.when(event.location()).thenReturn(new GameLocation(worldId, 5, 64, 5));
    Mockito.when(event.type()).thenReturn("WATER");
    Mockito.when(event.level()).thenReturn(2);
    Mockito.when(event.newType()).thenReturn("WATER");
    Mockito.when(event.newLevel()).thenReturn(4);

    host.levels.accept(event);
    flush.get().run();

    Mockito.verify(host.blocks, Mockito.atLeastOnce()).tickFluid(worldId, 5, 63, 5, true, true);
    Mockito.verify(host.blocks, Mockito.atLeastOnce()).tickFluid(worldId, 6, 64, 5, true, true);
  }

  @Test
  void fluidFlagsDecideWhichFluidsAccelerate() throws Exception {
    TweakFastFluids untouched = new TweakFastFluids();
    Assertions.assertTrue(decideFluid(untouched, "WATER"));
    Assertions.assertTrue(decideFluid(untouched, "LAVA"));
    Assertions.assertFalse(decideFluid(untouched, "STONE"));

    TweakFastFluids noWater = new TweakFastFluids();
    setFlag(noWater, "accelerateWater", false);
    Assertions.assertFalse(decideFluid(noWater, "WATER"));
    Assertions.assertTrue(decideFluid(noWater, "LAVA"));

    TweakFastFluids noLava = new TweakFastFluids();
    setFlag(noLava, "accelerateLava", false);
    Assertions.assertTrue(decideFluid(noLava, "WATER"));
    Assertions.assertFalse(decideFluid(noLava, "LAVA"));
  }

  private BlockHost.FluidFlowEvent flow(int x, int y, int z) {
    BlockHost.FluidFlowEvent event = Mockito.mock(BlockHost.FluidFlowEvent.class);
    Mockito.when(event.from()).thenReturn(new GameLocation(worldId, x, y, z));
    Mockito.when(event.fromType()).thenReturn("WATER");
    Mockito.when(event.to()).thenReturn(new GameLocation(worldId, x, y - 1, z));
    Mockito.when(event.toType()).thenReturn("AIR");
    return event;
  }

  private static void setFlag(TweakFastFluids tweak, String name, boolean value) throws Exception {
    Field field = TweakFastFluids.class.getDeclaredField(name);
    field.setAccessible(true);
    field.setBoolean(tweak, value);
  }

  private static boolean decideFluid(TweakFastFluids tweak, String material) throws Exception {
    Method method = TweakFastFluids.class.getDeclaredMethod("isSupportedFluid", String.class);
    method.setAccessible(true);
    return (boolean) method.invoke(tweak, material);
  }
}
