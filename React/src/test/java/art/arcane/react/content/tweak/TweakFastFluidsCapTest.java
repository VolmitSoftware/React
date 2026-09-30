package art.arcane.react.content.tweak;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.monitor.NativeWorldAccess;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockFromToEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class TweakFastFluidsCapTest {
  private MockedStatic<React> react;
  private MockedStatic<J> scheduling;
  private MockedStatic<Bukkit> bukkit;
  private MockedStatic<NativeAdapters> adapters;
  private AtomicReference<Runnable> flush;
  private List<Runnable> chunkTasks;
  private NativeWorldAccess access;
  private World world;
  private TweakFastFluids tweak;

  @BeforeEach
  void setUp() throws Throwable {
    react = Mockito.mockStatic(React.class);
    scheduling = Mockito.mockStatic(J.class);
    bukkit = Mockito.mockStatic(Bukkit.class);
    adapters = Mockito.mockStatic(NativeAdapters.class);
    flush = new AtomicReference<>();
    chunkTasks = new ArrayList<>();
    access = Mockito.mock(NativeWorldAccess.class);
    Mockito.when(access.tickFluid(Mockito.any(World.class), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(),
        Mockito.anyBoolean(), Mockito.anyBoolean())).thenReturn(true);
    adapters.when(() -> NativeAdapters.find(NativeWorldAccess.class)).thenReturn(Optional.of(access));
    scheduling.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(1))).thenAnswer(invocation -> {
      flush.set(invocation.getArgument(0));
      return 3;
    });
    scheduling.when(() -> J.runChunk(Mockito.any(World.class), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Runnable.class)))
        .thenAnswer(invocation -> {
          chunkTasks.add(invocation.getArgument(3));
          return true;
        });
    world = Mockito.mock(World.class);
    UUID worldId = UUID.randomUUID();
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.getMinHeight()).thenReturn(-64);
    Mockito.when(world.getMaxHeight()).thenReturn(320);
    Mockito.when(world.isChunkLoaded(Mockito.anyInt(), Mockito.anyInt())).thenReturn(true);
    bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
    tweak = new TweakFastFluids();
    tweak.onActivate();
  }

  @AfterEach
  void tearDown() {
    tweak.onDeactivate();
    adapters.close();
    bukkit.close();
    scheduling.close();
    react.close();
  }

  @Test
  void pendingPulsesAreCappedWhileExistingPositionsStillAccumulate() {
    FluidPulseQueue queue = new FluidPulseQueue();
    UUID worldId = UUID.randomUUID();
    for (int index = 0; index < FluidPulseQueue.MAX_PENDING_PULSES + 500; index++) {
      queue.enqueue(worldId, index, 64, 0, 1);
    }

    Assertions.assertEquals(FluidPulseQueue.MAX_PENDING_PULSES, queue.size());
    Assertions.assertTrue(queue.enqueue(worldId, 0, 64, 0, 3));
    Assertions.assertFalse(queue.enqueue(worldId, -1, 64, 0, 3));
    Assertions.assertEquals(FluidPulseQueue.MAX_PENDING_PULSES, queue.size());
  }

  @Test
  void drainedPulsesAreBucketedByChunkAndReleaseTheirSlots() {
    FluidPulseQueue queue = new FluidPulseQueue();
    UUID worldId = UUID.randomUUID();
    queue.enqueue(worldId, 1, 64, 1, 2);
    queue.enqueue(worldId, 2, 64, 1, 2);
    queue.enqueue(worldId, 15, 64, 15, 2);
    queue.enqueue(worldId, 16, 64, 1, 2);

    Map<FluidPulseQueue.FluidChunk, List<FluidPulseQueue.FluidBurst>> buckets = queue.drain(256, 16);

    Assertions.assertEquals(2, buckets.size());
    Assertions.assertEquals(3, buckets.get(new FluidPulseQueue.FluidChunk(worldId, 0, 0)).size());
    Assertions.assertEquals(1, buckets.get(new FluidPulseQueue.FluidChunk(worldId, 1, 0)).size());
    Assertions.assertEquals(0, queue.size());
  }

  @Test
  void foliaFlushSchedulesOneTaskPerChunk() throws Throwable {
    scheduling.when(J::isFoliaThreading).thenReturn(true);
    tweak.on(flow(1, 64, 1));
    tweak.on(flow(20, 64, 1));

    flush.get().run();

    Assertions.assertEquals(2, chunkTasks.size());
    Mockito.verify(access, Mockito.never()).tickFluid(Mockito.any(World.class), Mockito.anyInt(), Mockito.anyInt(),
        Mockito.anyInt(), Mockito.anyBoolean(), Mockito.anyBoolean());
    for (Runnable task : chunkTasks) {
      task.run();
    }
    Mockito.verify(access, Mockito.atLeastOnce()).tickFluid(Mockito.eq(world), Mockito.eq(1), Mockito.eq(64),
        Mockito.eq(1), Mockito.anyBoolean(), Mockito.anyBoolean());
  }

  @Test
  void paperFlushTicksFluidsInlineWithoutRegionTasks() throws Throwable {
    scheduling.when(J::isFoliaThreading).thenReturn(false);
    tweak.on(flow(1, 64, 1));

    flush.get().run();

    Assertions.assertTrue(chunkTasks.isEmpty());
    scheduling.verify(() -> J.s(Mockito.any(Location.class), Mockito.any(Runnable.class), Mockito.anyInt()), Mockito.never());
    Mockito.verify(access, Mockito.atLeastOnce()).tickFluid(Mockito.eq(world), Mockito.eq(1), Mockito.eq(64),
        Mockito.eq(1), Mockito.anyBoolean(), Mockito.anyBoolean());
  }

  private BlockFromToEvent flow(int x, int y, int z) {
    Block source = block(Material.WATER, x, y, z);
    Block target = block(Material.AIR, x, y - 1, z);
    BlockFromToEvent event = Mockito.mock(BlockFromToEvent.class);
    Mockito.when(event.getBlock()).thenReturn(source);
    Mockito.when(event.getToBlock()).thenReturn(target);
    return event;
  }

  private Block block(Material type, int x, int y, int z) {
    Block block = Mockito.mock(Block.class);
    Mockito.when(block.getType()).thenReturn(type);
    Mockito.when(block.getWorld()).thenReturn(world);
    Mockito.when(block.getX()).thenReturn(x);
    Mockito.when(block.getY()).thenReturn(y);
    Mockito.when(block.getZ()).thenReturn(z);
    return block;
  }
}
