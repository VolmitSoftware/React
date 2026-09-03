package art.arcane.react.util.project.world;

import art.arcane.react.util.project.world.DistanceSupport.DistanceType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DistanceSupportTest {
  @Test
  void currentPaperApiSupportsEveryWorldAndPlayerDistanceSetter() {
    for (DistanceType type : DistanceType.values()) {
      Assertions.assertTrue(DistanceSupport.supportsWorld(type));
      Assertions.assertTrue(DistanceSupport.supportsPlayer(type));
    }
  }

  @Test
  void worldDistanceValidationUsesExactPaperRanges() {
    Assertions.assertTrue(DistanceSupport.isValidWorldDistance(DistanceType.VIEW, 2));
    Assertions.assertTrue(DistanceSupport.isValidWorldDistance(DistanceType.SIMULATION, 32));
    Assertions.assertTrue(DistanceSupport.isValidWorldDistance(DistanceType.SEND, -1));
    Assertions.assertFalse(DistanceSupport.isValidWorldDistance(DistanceType.VIEW, -1));
    Assertions.assertFalse(DistanceSupport.isValidWorldDistance(DistanceType.SEND, 1));
    Assertions.assertFalse(DistanceSupport.isValidWorldDistance(DistanceType.VIEW, 33));
    Assertions.assertThrows(NullPointerException.class, () -> DistanceSupport.isValidWorldDistance(null, 8));
  }

  @Test
  void playerDistanceValidationAllowsInheritanceAndExactPaperRanges() {
    Assertions.assertTrue(DistanceSupport.isValidPlayerDistance(-1));
    Assertions.assertTrue(DistanceSupport.isValidPlayerDistance(2));
    Assertions.assertTrue(DistanceSupport.isValidPlayerDistance(32));
    Assertions.assertFalse(DistanceSupport.isValidPlayerDistance(1));
    Assertions.assertFalse(DistanceSupport.isValidPlayerDistance(33));
  }

  @Test
  void worldSettersMutateOnlyTheSelectedDistance() {
    World world = Mockito.mock(World.class);

    DistanceSupport.set(world, DistanceType.VIEW, 12);
    Mockito.verify(world).setViewDistance(12);
    Mockito.verify(world, Mockito.never()).setSimulationDistance(Mockito.anyInt());
    Mockito.verify(world, Mockito.never()).setSendViewDistance(Mockito.anyInt());

    Mockito.reset(world);
    DistanceSupport.set(world, DistanceType.SIMULATION, 8);
    Mockito.verify(world).setSimulationDistance(8);
    Mockito.verify(world, Mockito.never()).setViewDistance(Mockito.anyInt());
    Mockito.verify(world, Mockito.never()).setSendViewDistance(Mockito.anyInt());

    Mockito.reset(world);
    DistanceSupport.set(world, DistanceType.SEND, -1);
    Mockito.verify(world).setSendViewDistance(-1);
    Mockito.verify(world, Mockito.never()).setViewDistance(Mockito.anyInt());
    Mockito.verify(world, Mockito.never()).setSimulationDistance(Mockito.anyInt());
  }

  @Test
  void playerSettersMutateOnlyTheSelectedDistance() {
    Player player = Mockito.mock(Player.class);

    DistanceSupport.set(player, DistanceType.VIEW, 12);
    Mockito.verify(player).setViewDistance(12);
    Mockito.verify(player, Mockito.never()).setSimulationDistance(Mockito.anyInt());
    Mockito.verify(player, Mockito.never()).setSendViewDistance(Mockito.anyInt());

    Mockito.reset(player);
    DistanceSupport.set(player, DistanceType.SIMULATION, 8);
    Mockito.verify(player).setSimulationDistance(8);
    Mockito.verify(player, Mockito.never()).setViewDistance(Mockito.anyInt());
    Mockito.verify(player, Mockito.never()).setSendViewDistance(Mockito.anyInt());

    Mockito.reset(player);
    DistanceSupport.set(player, DistanceType.SEND, -1);
    Mockito.verify(player).setSendViewDistance(-1);
    Mockito.verify(player, Mockito.never()).setViewDistance(Mockito.anyInt());
    Mockito.verify(player, Mockito.never()).setSimulationDistance(Mockito.anyInt());
  }

  @Test
  void setRejectsNullTargetsAndTypes() {
    World world = Mockito.mock(World.class);
    Player player = Mockito.mock(Player.class);

    Assertions.assertThrows(NullPointerException.class, () -> DistanceSupport.set((World) null, DistanceType.VIEW, 8));
    Assertions.assertThrows(NullPointerException.class, () -> DistanceSupport.set((Player) null, DistanceType.VIEW, 8));
    Assertions.assertThrows(NullPointerException.class, () -> DistanceSupport.set(world, null, 8));
    Assertions.assertThrows(NullPointerException.class, () -> DistanceSupport.set(player, null, 8));
  }
}
