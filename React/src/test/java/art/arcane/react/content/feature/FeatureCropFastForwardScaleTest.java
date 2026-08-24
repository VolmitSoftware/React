package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.Ticker;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class FeatureCropFastForwardScaleTest {
  private React previous;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getName()).thenReturn("React");
    Mockito.when(plugin.namespace()).thenReturn("react");
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void hundredThousandLoadedCoordinatesDispatchOnlyOneBoundedWindow() {
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    ObserverController observer = Mockito.mock(ObserverController.class);
    List<ObserverController.LoadedChunkTarget> indexed = new ArrayList<>(100_000);
    for (int index = 0; index < 100_000; index++) {
      indexed.add(new ObserverController.LoadedChunkTarget(worldId, index, -index));
    }
    Mockito.when(observer.nextLoadedChunkCoordinateBatch(128)).thenReturn(indexed.subList(0, 128));
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.isChunkLoaded(Mockito.anyInt(), Mockito.anyInt())).thenReturn(true);
    Mockito.when(world.getMinHeight()).thenReturn(-64);
    Mockito.when(world.getMaxHeight()).thenReturn(320);

    FeatureCropFastForward feature = new FeatureCropFastForward();
    feature.onActivate();
    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      react.when(() -> React.hasNearbyPlayer(Mockito.any(), Mockito.anyDouble())).thenReturn(false);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);

      feature.scanForWakes();

      Mockito.verify(observer).nextLoadedChunkCoordinateBatch(128);
      Mockito.verify(world, Mockito.times(128)).isChunkLoaded(Mockito.anyInt(), Mockito.anyInt());
      Mockito.verify(world, Mockito.never()).getLoadedChunks();
      Mockito.verify(world, Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt());
    } finally {
      feature.onDeactivate();
    }
  }

  @Test
  void ownerTaskRetiresWithoutWorldAccessAfterDeactivation() {
    UUID worldId = UUID.randomUUID();
    World world = Mockito.mock(World.class);
    ObserverController observer = Mockito.mock(ObserverController.class);
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    Mockito.when(observer.nextLoadedChunkCoordinateBatch(128)).thenReturn(List.of(
        new ObserverController.LoadedChunkTarget(worldId, 4, -6)
    ));

    FeatureCropFastForward feature = new FeatureCropFastForward();
    feature.onActivate();
    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runChunk(
          Mockito.eq(world),
          Mockito.eq(4),
          Mockito.eq(-6),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(3));
        return true;
      });
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);

      feature.scanForWakes();
      Assertions.assertNotNull(ownerTask.get());
      Mockito.clearInvocations(world);
      feature.onDeactivate();
      ownerTask.get().run();

      Mockito.verifyNoInteractions(world);
    }
  }
}
