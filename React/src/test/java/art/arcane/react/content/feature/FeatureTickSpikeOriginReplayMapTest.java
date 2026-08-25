package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.util.common.scheduling.Ticker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class FeatureTickSpikeOriginReplayMapTest {
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
  void paperCaptureQueriesOnlyTheIndexedRadiusWithoutLoadedChunkEnumeration() throws Exception {
    UUID worldId = UUID.randomUUID();
    SampledChunk worst = Mockito.mock(SampledChunk.class);
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(worst.getWorldId()).thenReturn(worldId);
    Mockito.when(worst.getWorldKey()).thenReturn("minecraft:test");
    Mockito.when(worst.getChunkX()).thenReturn(40);
    Mockito.when(worst.getChunkZ()).thenReturn(-20);
    Mockito.when(observer.absoluteWorst()).thenReturn(worst);
    Mockito.when(observer.loadedChunkCoordinatesInRadius(worldId, 40, -20, 3)).thenReturn(List.of(
        new ObserverController.LoadedChunkCoordinate(40, -20),
        new ObserverController.LoadedChunkCoordinate(41, -20),
        new ObserverController.LoadedChunkCoordinate(40, -18)
    ));
    Mockito.when(observer.sampledChunk(Mockito.eq("minecraft:test"), Mockito.anyInt(), Mockito.anyInt()))
        .thenReturn(Optional.empty());

    FeatureTickSpikeOriginReplayMap feature = new FeatureTickSpikeOriginReplayMap();
    feature.onActivate();
    Method capture = FeatureTickSpikeOriginReplayMap.class.getDeclaredMethod(
        "captureSpike",
        long.class,
        double.class
    );
    capture.setAccessible(true);
    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      capture.invoke(feature, 1_000L, 80D);
    }

    Mockito.verify(observer).loadedChunkCoordinatesInRadius(worldId, 40, -20, 3);
    Mockito.verify(observer, Mockito.times(3)).sampledChunk(
        Mockito.eq("minecraft:test"),
        Mockito.anyInt(),
        Mockito.anyInt()
    );
    HeatmapWorldRef worldRef = new HeatmapWorldRef(
        worldId,
        "minecraft:test",
        "test",
        0,
        0,
        0D,
        0D,
        60_000_000D
    );
    Assertions.assertTrue(feature.chunkScore(worldRef, 40, -20) > 0D);
    Assertions.assertEquals(0D, feature.chunkScore(worldRef, 44, -20));
  }
}
