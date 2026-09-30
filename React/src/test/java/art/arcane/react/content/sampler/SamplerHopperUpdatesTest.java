package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.testutil.FakeInventories;
import art.arcane.react.testutil.Fakes;
import com.google.common.util.concurrent.AtomicDouble;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SamplerHopperUpdatesTest {
  @BeforeAll
  static void initializeInventoryTypes() {
    FakeInventories.initializeInventoryTypes();
  }

  @Test
  void eachHopperMoveCountsOnceOnTheHopperChunkWithoutSnapshots() {
    World world = Fakes.world("world");
    Inventory chest = FakeInventories.chest(world, 0, 65, 0);
    Inventory hopper = FakeInventories.hopperBlock(world, 0, 64, 0);
    Inventory nextHopper = FakeInventories.hopperBlock(world, 17, 64, 0);
    Block hopperBlock = FakeInventories.blockOf(hopper);
    Block nextHopperBlock = FakeInventories.blockOf(nextHopper);
    SamplerHopperUpdates sampler = Mockito.spy(new SamplerHopperUpdates());
    Map<Block, AtomicDouble> counters = new HashMap<>();
    ObserverController observer = observer(counters);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      sampler.on(FakeInventories.move(chest, hopper));
      sampler.on(FakeInventories.move(hopper, nextHopper));
      sampler.on(FakeInventories.move(hopper, nextHopper));
    }

    Mockito.verify(chest, Mockito.never()).getHolder();
    Mockito.verify(hopper, Mockito.never()).getHolder();
    Mockito.verify(nextHopper, Mockito.never()).getHolder();
    Mockito.verify(sampler, Mockito.times(3)).increment();
    assertEquals(3D, counters.get(hopperBlock).get());
    assertNull(counters.get(nextHopperBlock));
  }

  @Test
  void chestAndMinecartMovesAreNotHopperMoves() {
    World world = Fakes.world("world");
    Inventory chest = FakeInventories.chest(world, 0, 64, 0);
    Inventory otherChest = FakeInventories.chest(world, 1, 64, 0);
    Inventory minecart = FakeInventories.hopperMinecart(world, 0.5D, 65D, 0.5D);
    SamplerHopperUpdates sampler = Mockito.spy(new SamplerHopperUpdates());
    ObserverController observer = observer(new HashMap<>());

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      sampler.on(FakeInventories.move(chest, otherChest));
      sampler.on(FakeInventories.move(chest, minecart));
      sampler.on(FakeInventories.move(minecart, otherChest));
    }

    Mockito.verify(sampler, Mockito.never()).increment();
  }

  @Test
  void blockUpdatesNextToHoppersAreNotCountedAsTransfers() {
    for (Method method : SamplerHopperUpdates.class.getDeclaredMethods()) {
      if (method.isAnnotationPresent(EventHandler.class)) {
        assertNotEquals(BlockPhysicsEvent.class, method.getParameterTypes()[0], method.toString());
      }
    }
  }

  private static ObserverController observer(Map<Block, AtomicDouble> counters) {
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.get(Mockito.any(Block.class), Mockito.any(Sampler.class)))
        .thenAnswer(invocation -> counters.computeIfAbsent(invocation.getArgument(0), ignored -> new AtomicDouble()));
    Mockito.when(observer.get(Mockito.any(Chunk.class), Mockito.any(Sampler.class))).thenReturn(new AtomicDouble());
    return observer;
  }
}
