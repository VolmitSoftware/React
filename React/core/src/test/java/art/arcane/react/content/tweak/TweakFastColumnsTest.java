package art.arcane.react.content.tweak;

import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.BlockHostStub;
import art.arcane.react.platform.GameEntity;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class TweakFastColumnsTest {
  private final UUID worldId = UUID.randomUUID();
  private MockedStatic<J> scheduling;
  private List<Runnable> scheduled;
  private BlockHostStub host;
  private TweakFastColumns tweak;

  @BeforeEach
  void setUp() {
    scheduling = Mockito.mockStatic(J.class);
    scheduled = new ArrayList<>();
    scheduling.when(() -> J.runChunk(Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Runnable.class), Mockito.eq(2)))
        .thenAnswer(invocation -> {
          scheduled.add(invocation.getArgument(3));
          return true;
        });
    host = new BlockHostStub();
    Mockito.when(host.worlds.world(worldId)).thenReturn(Optional.of(Mockito.mock(ReactWorld.class)));
    Mockito.when(host.blocks.maxHeight(worldId)).thenReturn(320);
    tweak = new TweakFastColumns();
    tweak.onActivate();
  }

  @AfterEach
  void tearDown() {
    tweak.onDeactivate();
    host.close();
    scheduling.close();
  }

  @Test
  void physicsListensAtNormalPriorityLikeTheBukkitHandler() {
    Assertions.assertEquals(ListenerPriority.NORMAL, host.physicsPriority);
  }

  @Test
  void unrelatedPhysicsUpdatesNeverResolveALocationOrSchedule() {
    BlockHost.PhysicsEvent stone = physics("STONE", 3, 64, 3);

    host.physics.accept(stone);

    Mockito.verify(stone, Mockito.never()).location();
    Mockito.verify(stone, Mockito.times(1)).type();
    Assertions.assertTrue(scheduled.isEmpty());
  }

  @Test
  void repeatedUpdatesAtOnePositionScheduleOneCheckUntilItRuns() {
    BlockHost.PhysicsEvent kelp = physics("KELP_PLANT", 5, 40, -7);
    Mockito.when(host.blocks.blockType(worldId, 5, 40, -7)).thenReturn("KELP_PLANT");

    host.physics.accept(kelp);
    host.physics.accept(kelp);
    host.physics.accept(kelp);

    Assertions.assertEquals(1, scheduled.size());
    scheduled.get(0).run();
    host.physics.accept(kelp);

    Assertions.assertEquals(2, scheduled.size());
  }

  @Test
  void distinctColumnsScheduleIndependently() {
    host.physics.accept(physics("SUGAR_CANE", 0, 64, 0));
    host.physics.accept(physics("SUGAR_CANE", 1, 64, 0));
    host.physics.accept(physics("BAMBOO", 0, 65, 0));

    Assertions.assertEquals(3, scheduled.size());
  }

  @Test
  void brokenColumnCollapsesAndHandsTheDropsToThePlayer() {
    for (int y = 64; y <= 66; y++) {
      Mockito.when(host.blocks.blockType(worldId, 2, y, 2)).thenReturn("SUGAR_CANE");
    }
    Mockito.when(host.blocks.blockType(worldId, 2, 67, 2)).thenReturn("AIR");
    GameEntity player = Mockito.mock(GameEntity.class);
    BlockHost.BlockEvent broken = Mockito.mock(BlockHost.BlockEvent.class);
    Mockito.when(broken.type()).thenReturn("SUGAR_CANE");
    Mockito.when(broken.location()).thenReturn(new GameLocation(worldId, 2, 64, 2));
    Mockito.when(broken.player()).thenReturn(player);

    host.breaks.accept(broken);

    Mockito.verify(host.blocks).setBlock(worldId, 2, 64, 2, "AIR", false);
    Mockito.verify(host.blocks).setBlock(worldId, 2, 65, 2, "AIR", false);
    Mockito.verify(host.blocks).setBlock(worldId, 2, 66, 2, "AIR", true);
    Mockito.verify(host.blocks).giveItem(player, new GameLocation(worldId, 2.5, 64.5, 2.5), "SUGAR_CANE", 3);
  }

  @Test
  void emptiedKelpColumnRefillsWithWaterAndDropsKelp() {
    Mockito.when(host.blocks.blockType(worldId, 4, 30, 4)).thenReturn("WATER", "KELP_PLANT");
    Mockito.when(host.blocks.blockType(worldId, 4, 31, 4)).thenReturn("KELP_PLANT");
    Mockito.when(host.blocks.blockType(worldId, 4, 32, 4)).thenReturn("WATER");

    host.physics.accept(physics("KELP_PLANT", 4, 30, 4));
    scheduled.get(0).run();

    Mockito.verify(host.blocks).setBlock(worldId, 4, 30, 4, "WATER", false);
    Mockito.verify(host.blocks).setBlock(worldId, 4, 31, 4, "WATER", true);
    Mockito.verify(host.blocks).dropItem(new GameLocation(worldId, 4.5, 30, 4.5), "KELP", 2);
  }

  private BlockHost.PhysicsEvent physics(String type, int x, int y, int z) {
    BlockHost.PhysicsEvent event = Mockito.mock(BlockHost.PhysicsEvent.class);
    Mockito.when(event.type()).thenReturn(type);
    Mockito.when(event.location()).thenReturn(new GameLocation(worldId, x, y, z));
    return event;
  }
}
