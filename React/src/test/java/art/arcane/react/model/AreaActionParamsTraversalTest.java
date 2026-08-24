package art.arcane.react.model;

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.core.controller.ObserverController.LoadedChunkCursor;
import art.arcane.react.core.controller.ObserverController.LoadedChunkTarget;
import art.arcane.react.util.common.scheduling.Ticker;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaActionParamsTraversalTest {
  private React previous;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void hundredThousandExplicitCoordinatesDrainInBoundedConstantTimeWaves() {
    UUID worldId = UUID.randomUUID();
    List<LoadedChunkTarget> targets = new ArrayList<>(100_000);
    for (int index = 0; index < 100_000; index++) {
      targets.add(new LoadedChunkTarget(worldId, index, -index));
    }
    AreaActionParams area = AreaActionParams.builder().allChunks(false).build();
    area.setChunks(targets);

    assertEquals(100_000, area.estimatedTotalWork());
    int drained = 0;
    List<LoadedChunkTarget> wave;
    do {
      wave = area.popChunks(Integer.MAX_VALUE);
      assertTrue(wave.size() <= 256);
      drained += wave.size();
    } while (!wave.isEmpty());

    assertEquals(100_000, drained);
    assertNull(area.popChunk());
  }

  @Test
  void observerCursorStreamsHundredThousandCoordinatesWithoutChunkHandles() throws Exception {
    ObserverController observer = new ObserverController();
    UUID worldId = UUID.randomUUID();
    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
      observer.start();
    }

    Method indexCoordinate = ObserverController.class.getDeclaredMethod(
        "indexLoadedChunkCoordinate",
        UUID.class,
        int.class,
        int.class,
        boolean.class,
        nestedClass(observer, "InitialLoadedChunkSeed")
    );
    indexCoordinate.setAccessible(true);
    for (int index = 0; index < 100_000; index++) {
      indexCoordinate.invoke(observer, worldId, index, -index, true, null);
    }

    LoadedChunkCursor cursor = observer.openLoadedChunkCursor(worldId);
    assertEquals(100_000, cursor.estimatedTotal());
    Set<LoadedChunkTarget> seen = new HashSet<>(131_072);
    List<LoadedChunkTarget> wave;
    do {
      wave = cursor.next(Integer.MAX_VALUE);
      assertTrue(wave.size() <= 256);
      seen.addAll(wave);
    } while (!wave.isEmpty());

    assertEquals(100_000, seen.size());
    observer.stop();
  }

  private static Class<?> nestedClass(Object owner, String simpleName) {
    for (Class<?> type : owner.getClass().getDeclaredClasses()) {
      if (type.getSimpleName().equals(simpleName)) {
        return type;
      }
    }
    throw new IllegalStateException("Missing nested class " + simpleName);
  }
}
