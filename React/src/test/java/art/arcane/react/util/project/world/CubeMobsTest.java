package art.arcane.react.util.project.world;

import org.bukkit.entity.Entity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Runs against the 26.1.2 paper-api test classpath, where org.bukkit.entity.AbstractCubeMob does
 * not exist — exercises the Class.forName fallback path onto org.bukkit.entity.Slime. On a 26.2
 * classpath the probe resolves AbstractCubeMob instead; either way the observable behavior below
 * must hold.
 */
class CubeMobsTest {

  private static Stream<Arguments> cubeMobCandidates() {
    return Stream.of(
        Arguments.of(Slime.class, true),
        Arguments.of(MagmaCube.class, true),
        Arguments.of(Zombie.class, false),
        Arguments.of(null, false)
    );
  }

  @ParameterizedTest(name = "{0} -> cubeMob={1}")
  @MethodSource("cubeMobCandidates")
  void isCubeMobRecognisesOnlyCubeEntities(Class<? extends Entity> entityType, boolean expected) {
    Entity entity = entityType == null ? null : Mockito.mock(entityType);

    Assertions.assertEquals(expected, CubeMobs.isCubeMob(entity));
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
