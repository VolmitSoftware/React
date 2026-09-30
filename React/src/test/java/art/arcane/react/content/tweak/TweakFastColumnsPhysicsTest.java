package art.arcane.react.content.tweak;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class TweakFastColumnsPhysicsTest {
  private MockedStatic<React> react;
  private MockedStatic<J> scheduling;
  private List<Runnable> scheduled;
  private TweakFastColumns tweak;
  private World world;

  @BeforeEach
  void setUp() {
    react = Mockito.mockStatic(React.class);
    scheduling = Mockito.mockStatic(J.class);
    scheduled = new ArrayList<>();
    scheduling.when(() -> J.s(Mockito.any(Location.class), Mockito.any(Runnable.class), Mockito.eq(2)))
        .thenAnswer(invocation -> {
          scheduled.add(invocation.getArgument(1));
          return null;
        });
    world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(UUID.randomUUID());
    tweak = new TweakFastColumns();
    tweak.onActivate();
  }

  @AfterEach
  void tearDown() {
    tweak.onDeactivate();
    scheduling.close();
    react.close();
  }

  @Test
  void unrelatedPhysicsUpdatesNeverAllocateALocationOrSchedule() {
    Block stone = block(Material.STONE, 3, 64, 3);

    tweak.on(physics(stone));

    Mockito.verify(stone, Mockito.never()).getLocation();
    Mockito.verify(stone, Mockito.times(1)).getType();
    Assertions.assertTrue(scheduled.isEmpty());
  }

  @Test
  void repeatedUpdatesAtOnePositionScheduleOneCheckUntilItRuns() {
    Block kelp = block(Material.KELP_PLANT, 5, 40, -7);
    Block below = block(Material.KELP_PLANT, 5, 40, -7);
    Mockito.when(world.getBlockAt(Mockito.any(Location.class))).thenReturn(below);

    tweak.on(physics(kelp));
    tweak.on(physics(kelp));
    tweak.on(physics(kelp));

    Assertions.assertEquals(1, scheduled.size());
    scheduled.getFirst().run();
    tweak.on(physics(kelp));

    Assertions.assertEquals(2, scheduled.size());
  }

  @Test
  void distinctColumnsScheduleIndependently() {
    tweak.on(physics(block(Material.SUGAR_CANE, 0, 64, 0)));
    tweak.on(physics(block(Material.SUGAR_CANE, 1, 64, 0)));
    tweak.on(physics(block(Material.BAMBOO, 0, 65, 0)));

    Assertions.assertEquals(3, scheduled.size());
  }

  private Block block(Material type, int x, int y, int z) {
    Block block = Mockito.mock(Block.class);
    Mockito.when(block.getType()).thenReturn(type);
    Mockito.when(block.getWorld()).thenReturn(world);
    Mockito.when(block.getX()).thenReturn(x);
    Mockito.when(block.getY()).thenReturn(y);
    Mockito.when(block.getZ()).thenReturn(z);
    Mockito.when(block.getLocation()).thenAnswer(invocation -> new Location(world, x, y, z));
    return block;
  }

  private BlockPhysicsEvent physics(Block block) {
    BlockPhysicsEvent event = Mockito.mock(BlockPhysicsEvent.class);
    Mockito.when(event.getBlock()).thenReturn(block);
    return event;
  }
}
