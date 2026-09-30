package art.arcane.react.content.tweak;

import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class TweakFastFallingBlocksBudgetTest {
  private MockedStatic<J> scheduling;
  private AtomicReference<Runnable> ticker;
  private List<Runnable> regionTasks;
  private World world;
  private BlockData sand;
  private TweakFastFallingBlocks tweak;

  @BeforeEach
  void setUp() throws ReflectiveOperationException {
    scheduling = Mockito.mockStatic(J.class);
    ticker = new AtomicReference<>();
    regionTasks = new ArrayList<>();
    scheduling.when(() -> J.sr(Mockito.any(Runnable.class), Mockito.eq(0))).thenAnswer(invocation -> {
      ticker.set(invocation.getArgument(0));
      return 7;
    });
    scheduling.when(() -> J.s(Mockito.any(Location.class), Mockito.any(Runnable.class), Mockito.eq(0)))
        .thenAnswer(invocation -> {
          regionTasks.add(invocation.getArgument(1));
          return null;
        });
    world = Mockito.mock(World.class);
    sand = Mockito.mock(BlockData.class);
    Mockito.when(world.getMaxHeight()).thenReturn(320);
    Block air = Mockito.mock(Block.class);
    Mockito.when(air.getBlockData()).thenReturn(Mockito.mock(BlockData.class));
    Mockito.when(world.getBlockAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(air);
    Block changed = Mockito.mock(Block.class);
    Mockito.when(changed.getBlockData()).thenReturn(Mockito.mock(BlockData.class));
    Mockito.when(world.getBlockAt(Mockito.any(Location.class))).thenReturn(changed);
    tweak = new TweakFastFallingBlocks();
    Field budget = TweakFastFallingBlocks.class.getDeclaredField("maxFallMS");
    budget.setAccessible(true);
    budget.setDouble(tweak, 10_000D);
    tweak.onActivate();
  }

  @AfterEach
  void tearDown() {
    tweak.onDeactivate();
    scheduling.close();
  }

  @Test
  void paperBlockWorkRunsInsideTheBudgetedLoop() {
    scheduling.when(J::isFoliaThreading).thenReturn(false);
    tweak.on(fallingBlockLanding(4, 70, 9));
    Mockito.clearInvocations(world);

    ticker.get().run();

    Mockito.verify(world).getBlockAt(Mockito.any(Location.class));
    Assertions.assertTrue(regionTasks.isEmpty());
  }

  @Test
  void foliaBlockWorkIsHandedToTheOwningRegion() {
    scheduling.when(J::isFoliaThreading).thenReturn(true);
    tweak.on(fallingBlockLanding(4, 70, 9));
    Mockito.clearInvocations(world);

    ticker.get().run();

    Assertions.assertEquals(1, regionTasks.size());
    Mockito.verify(world, Mockito.never()).getBlockAt(Mockito.any(Location.class));
    regionTasks.getFirst().run();
    Mockito.verify(world).getBlockAt(Mockito.any(Location.class));
  }

  @Test
  void queuedFallsDrainInArrivalOrder() {
    scheduling.when(J::isFoliaThreading).thenReturn(true);
    tweak.on(fallingBlockLanding(1, 70, 1));
    tweak.on(fallingBlockLanding(2, 70, 2));
    tweak.on(fallingBlockLanding(3, 70, 3));

    ticker.get().run();

    Assertions.assertEquals(3, regionTasks.size());
    List<Integer> order = new ArrayList<>();
    Mockito.when(world.getBlockAt(Mockito.any(Location.class))).thenAnswer(invocation -> {
      order.add(invocation.<Location>getArgument(0).getBlockX());
      Block changed = Mockito.mock(Block.class);
      Mockito.when(changed.getBlockData()).thenReturn(Mockito.mock(BlockData.class));
      return changed;
    });
    for (Runnable task : regionTasks) {
      task.run();
    }
    Assertions.assertEquals(List.of(1, 2, 3), order);
  }

  @Test
  void cancellationHappensBeforeMonitorListenersObserveTheChange() throws NoSuchMethodException {
    EventHandler handler = TweakFastFallingBlocks.class
        .getMethod("on", EntityChangeBlockEvent.class)
        .getAnnotation(EventHandler.class);

    Assertions.assertEquals(EventPriority.HIGHEST, handler.priority());
    Assertions.assertTrue(handler.ignoreCancelled());
  }

  private EntityChangeBlockEvent fallingBlockLanding(int x, int y, int z) {
    EntityChangeBlockEvent event = Mockito.mock(EntityChangeBlockEvent.class);
    FallingBlock falling = Mockito.mock(FallingBlock.class);
    Block block = Mockito.mock(Block.class);
    Location location = new Location(world, x, y, z);
    Block resolved = Mockito.mock(Block.class);
    Mockito.when(event.getEntity()).thenReturn(falling);
    Mockito.when(falling.getBlockData()).thenReturn(sand);
    Mockito.when(event.getBlock()).thenReturn(block);
    Mockito.when(block.getLocation()).thenReturn(location);
    Mockito.when(world.getBlockAt(location)).thenReturn(resolved);
    Mockito.when(resolved.getWorld()).thenReturn(world);
    Mockito.when(resolved.getX()).thenReturn(x);
    Mockito.when(resolved.getY()).thenReturn(y);
    Mockito.when(resolved.getZ()).thenReturn(z);
    Mockito.when(resolved.getLocation()).thenReturn(location);
    Mockito.when(resolved.getBlockData()).thenReturn(Mockito.mock(BlockData.class));
    return event;
  }
}
