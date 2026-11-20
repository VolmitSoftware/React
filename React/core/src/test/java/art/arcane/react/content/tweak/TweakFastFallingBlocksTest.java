package art.arcane.react.content.tweak;

import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.BlockHostStub;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.util.common.scheduling.J;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class TweakFastFallingBlocksTest {
  private final UUID worldId = UUID.randomUUID();
  private final Object sand = new Object();
  private MockedStatic<J> scheduling;
  private AtomicReference<Runnable> ticker;
  private List<Runnable> regionTasks;
  private BlockHostStub host;
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
    scheduling.when(() -> J.runChunk(Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Runnable.class), Mockito.eq(0)))
        .thenAnswer(invocation -> {
          regionTasks.add(invocation.getArgument(3));
          return true;
        });
    host = new BlockHostStub();
    Mockito.when(host.worlds.world(worldId)).thenReturn(Optional.of(Mockito.mock(ReactWorld.class)));
    tweak = new TweakFastFallingBlocks();
    Field budget = TweakFastFallingBlocks.class.getDeclaredField("maxFallMS");
    budget.setAccessible(true);
    budget.setDouble(tweak, 10_000D);
    tweak.onActivate();
  }

  @AfterEach
  void tearDown() {
    tweak.onDeactivate();
    host.close();
    scheduling.close();
  }

  @Test
  void cancellationHappensBeforeMonitorListenersObserveTheChange() {
    Assertions.assertEquals(ListenerPriority.HIGHEST, host.fallingPriority);
    BlockHost.FallingBlockEvent event = fall(1, 70, 1);

    host.falling.accept(event);

    Mockito.verify(event).cancel();
  }

  @Test
  void serverThreadBlockWorkRunsInsideTheBudgetedLoop() {
    scheduling.when(J::isFoliaThreading).thenReturn(false);
    Mockito.when(host.blocks.matchingAbove(new GameLocation(worldId, 4, 70, 9), sand)).thenReturn(2);
    host.falling.accept(fall(4, 70, 9));

    ticker.get().run();

    Mockito.verify(host.blocks).dropColumn(new GameLocation(worldId, 4, 70, 9), sand, 2);
    Assertions.assertTrue(regionTasks.isEmpty());
  }

  @Test
  void foliaBlockWorkIsHandedToTheOwningRegion() {
    scheduling.when(J::isFoliaThreading).thenReturn(true);
    host.falling.accept(fall(4, 70, 9));

    ticker.get().run();

    Assertions.assertEquals(1, regionTasks.size());
    Mockito.verify(host.blocks, Mockito.never()).dropColumn(Mockito.any(), Mockito.any(), Mockito.anyInt());
    regionTasks.get(0).run();
    Mockito.verify(host.blocks).dropColumn(new GameLocation(worldId, 4, 70, 9), sand, 0);
  }

  @Test
  void queuedFallsDrainInArrivalOrderAndDeduplicate() {
    scheduling.when(J::isFoliaThreading).thenReturn(false);
    List<Integer> order = new ArrayList<>();
    Mockito.doAnswer(invocation -> {
      order.add(invocation.<GameLocation>getArgument(0).blockX());
      return null;
    }).when(host.blocks).dropColumn(Mockito.any(), Mockito.any(), Mockito.anyInt());
    host.falling.accept(fall(1, 70, 1));
    host.falling.accept(fall(2, 70, 2));
    host.falling.accept(fall(2, 70, 2));
    host.falling.accept(fall(3, 70, 3));

    ticker.get().run();

    Assertions.assertEquals(List.of(1, 2, 3), order);
  }

  private BlockHost.FallingBlockEvent fall(int x, int y, int z) {
    BlockHost.FallingBlockEvent event = Mockito.mock(BlockHost.FallingBlockEvent.class);
    Mockito.when(event.block()).thenReturn(new GameLocation(worldId, x, y, z));
    Mockito.when(event.blockState()).thenReturn(sand);
    return event;
  }
}
