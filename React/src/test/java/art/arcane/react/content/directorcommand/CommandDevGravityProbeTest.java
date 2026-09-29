package art.arcane.react.content.directorcommand;

import art.arcane.react.React;
import art.arcane.react.content.feature.FeatureLazyGravity;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.plugin.VolmitSender;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class CommandDevGravityProbeTest {
  @Test
  void gravityProbeTouchesTheWorldOnlyFromTheSpawnChunkOwner() throws ReflectiveOperationException {
    VolmitSender out = Mockito.mock(VolmitSender.class);
    World world = Mockito.mock(World.class);
    FallingBlock falling = Mockito.mock(FallingBlock.class);
    Block landing = Mockito.mock(Block.class);
    List<Runnable> immediate = new ArrayList<>();
    List<Runnable> delayed = new ArrayList<>();
    AtomicInteger completions = new AtomicInteger();

    Mockito.when(world.getSpawnLocation()).thenReturn(new Location(world, 37D, 64D, -20D));
    Mockito.when(world.getHighestBlockYAt(37, -20)).thenReturn(70);
    Mockito.when(world.getMaxHeight()).thenReturn(320);
    Mockito.when(world.spawnFallingBlock(Mockito.any(Location.class), Mockito.nullable(BlockData.class))).thenReturn(falling);
    Mockito.when(world.getBlockAt(37, 71, -20)).thenReturn(landing);
    Mockito.when(falling.isValid()).thenReturn(false);
    Mockito.when(landing.getType()).thenReturn(Material.SAND);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.feature(FeatureLazyGravity.class)).thenReturn(Mockito.mock(FeatureLazyGravity.class));
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
      scheduling.when(() -> J.runChunk(Mockito.eq(world), Mockito.eq(2), Mockito.eq(-2), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> immediate.add(invocation.getArgument(3)));
      scheduling.when(() -> J.runChunk(Mockito.eq(world), Mockito.eq(2), Mockito.eq(-2), Mockito.any(Runnable.class), Mockito.eq(100)))
          .thenAnswer(invocation -> delayed.add(invocation.getArgument(3)));

      Method probe = CommandDev.class.getDeclaredMethod("verifyLazyGravity", VolmitSender.class, Runnable.class);
      probe.setAccessible(true);
      probe.invoke(new CommandDev(), out, (Runnable) completions::incrementAndGet);

      Mockito.verify(world, Mockito.never()).getHighestBlockYAt(Mockito.anyInt(), Mockito.anyInt());
      Mockito.verify(world, Mockito.never()).spawnFallingBlock(Mockito.any(Location.class), Mockito.nullable(BlockData.class));
      Assertions.assertEquals(1, immediate.size());

      immediate.get(0).run();

      Mockito.verify(world).spawnFallingBlock(Mockito.any(Location.class), Mockito.nullable(BlockData.class));
      Assertions.assertEquals(1, delayed.size());
      Assertions.assertEquals(0, completions.get());
      scheduling.verify(() -> J.s(Mockito.any(Runnable.class), Mockito.anyInt()), Mockito.never());
      bukkit.verify(() -> Bukkit.getEntity(Mockito.any(UUID.class)), Mockito.never());

      delayed.get(0).run();

      Mockito.verify(landing).setType(Material.AIR);
      Assertions.assertEquals(1, completions.get());
    }
  }
}
