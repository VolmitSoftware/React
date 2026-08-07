package art.arcane.react.util.project.world;

import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs against the 26.1.2 paper-api test classpath, where org.bukkit.entity.AbstractCubeMob does
 * not exist — exercises the Class.forName fallback path onto org.bukkit.entity.Slime. On a 26.2
 * classpath the probe resolves AbstractCubeMob instead; either way the observable behavior below
 * must hold.
 */
class CubeMobsTest {

  @Test
  void slimeIsCubeMob() {
    Assertions.assertTrue(CubeMobs.isCubeMob(Mockito.mock(Slime.class)));
  }

  @Test
  void magmaCubeIsCubeMob() {
    Assertions.assertTrue(CubeMobs.isCubeMob(Mockito.mock(MagmaCube.class)));
  }

  @Test
  void nonCubeEntityIsNotCubeMob() {
    Assertions.assertFalse(CubeMobs.isCubeMob(Mockito.mock(Zombie.class)));
  }

  @Test
  void nullEntityIsNotCubeMob() {
    Assertions.assertFalse(CubeMobs.isCubeMob(null));
  }

  @Test
  void getSizeReadsEntitySize() {
    Slime slime = Mockito.mock(Slime.class);
    Mockito.when(slime.getSize()).thenReturn(3);

    Assertions.assertEquals(3, CubeMobs.getSize(slime));
  }

  @Test
  void setSizeThenGetSizeRoundTrips() {
    AtomicInteger size = new AtomicInteger(1);
    MagmaCube cube = Mockito.mock(MagmaCube.class);
    Mockito.when(cube.getSize()).thenAnswer(invocation -> size.get());
    Mockito.doAnswer(invocation -> {
      size.set(invocation.getArgument(0));
      return null;
    }).when(cube).setSize(Mockito.anyInt());

    CubeMobs.setSize(cube, 4);

    Assertions.assertEquals(4, CubeMobs.getSize(cube));
  }
}
