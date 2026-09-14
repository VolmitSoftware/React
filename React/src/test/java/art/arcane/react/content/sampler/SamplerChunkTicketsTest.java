package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.nms.NmsBridge;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class SamplerChunkTicketsTest {
  private MockedStatic<Bukkit> bukkit;
  private MockedStatic<J> scheduling;
  private MockedStatic<NmsBridges> bridges;
  private MockedStatic<React> logging;
  private NmsBridge bridge;
  private World firstWorld;
  private World secondWorld;
  private SamplerChunkTickets sampler;

  @BeforeEach
  void setUp() {
    bukkit = Mockito.mockStatic(Bukkit.class);
    scheduling = Mockito.mockStatic(J.class);
    bridges = Mockito.mockStatic(NmsBridges.class);
    logging = Mockito.mockStatic(React.class);
    scheduling.when(J::isPrimaryThread).thenReturn(true);
    scheduling.when(J::isFoliaThreading).thenReturn(true);
    bridge = Mockito.mock(NmsBridge.class);
    bridges.when(NmsBridges::get).thenReturn(bridge);
    firstWorld = Mockito.mock(World.class);
    secondWorld = Mockito.mock(World.class);
    bukkit.when(Bukkit::getWorlds).thenReturn(List.of(firstWorld, secondWorld));
    sampler = new SamplerChunkTickets();
    sampler.start();
  }

  @AfterEach
  void tearDown() {
    logging.close();
    bridges.close();
    scheduling.close();
    bukkit.close();
  }

  @Test
  void foliaCountsEveryWorldWithoutResolvingBukkitChunks() {
    Mockito.when(bridge.countPluginChunkTickets(firstWorld)).thenReturn(3L);
    Mockito.when(bridge.countPluginChunkTickets(secondWorld)).thenReturn(2L);

    Assertions.assertEquals(5D, sampler.onSample());
    Assertions.assertTrue(sampler.isSampleAvailable());
    Mockito.verifyNoInteractions(firstWorld, secondWorld);
    Mockito.verify(bridge).countPluginChunkTickets(firstWorld);
    Mockito.verify(bridge).countPluginChunkTickets(secondWorld);
  }

  @Test
  void noPluginTicketsIsAnAvailableZero() {
    Assertions.assertEquals(0D, sampler.onSample());
    Assertions.assertTrue(sampler.isSampleAvailable());
  }

  @Test
  void foliaWithoutNativeSupportNeverFallsBackToUnsafeWorldQuery() {
    bridges.when(NmsBridges::get).thenReturn(null);

    Assertions.assertEquals(0D, sampler.onSample());
    Assertions.assertFalse(sampler.isSampleAvailable());
    Mockito.verifyNoInteractions(firstWorld, secondWorld);
  }

  @Test
  void failedSnapshotRejectsPartialTotalsLogsOnceAndRecoversOnRestart() {
    RuntimeException failure = new IllegalStateException("snapshot failed");
    Mockito.when(bridge.countPluginChunkTickets(firstWorld)).thenReturn(3L);
    Mockito.when(bridge.countPluginChunkTickets(secondWorld)).thenThrow(failure);

    Assertions.assertEquals(0D, sampler.onSample());
    Assertions.assertFalse(sampler.isSampleAvailable());
    Assertions.assertEquals(0D, sampler.onSample());
    Mockito.verify(bridge).countPluginChunkTickets(secondWorld);
    logging.verify(() -> React.warn(
        "Could not sample plugin chunk tickets. The metric is unavailable until its sampler restarts.", failure));

    Mockito.doReturn(2L).when(bridge).countPluginChunkTickets(secondWorld);
    sampler.start();

    Assertions.assertEquals(5D, sampler.onSample());
    Assertions.assertTrue(sampler.isSampleAvailable());
  }

  @Test
  void missingNativeMethodMarksMetricUnavailableWithoutUnsafeFallback() {
    Mockito.when(bridge.countPluginChunkTickets(firstWorld)).thenThrow(new NoSuchMethodError("ticket snapshot"));

    Assertions.assertEquals(0D, sampler.onSample());
    Assertions.assertFalse(sampler.isSampleAvailable());
    Mockito.verifyNoInteractions(firstWorld, secondWorld);
  }

  @Test
  void paperKeepsPerPluginMembershipCounting() {
    scheduling.when(J::isFoliaThreading).thenReturn(false);
    Plugin firstPlugin = Mockito.mock(Plugin.class);
    Plugin secondPlugin = Mockito.mock(Plugin.class);
    Chunk sharedChunk = Mockito.mock(Chunk.class);
    Chunk otherChunk = Mockito.mock(Chunk.class);
    Mockito.when(firstWorld.getPluginChunkTickets()).thenReturn(Map.of(
        firstPlugin, List.of(sharedChunk, otherChunk), secondPlugin, List.of(sharedChunk)));
    Mockito.when(secondWorld.getPluginChunkTickets()).thenReturn(Map.of(firstPlugin, List.of(otherChunk)));

    Assertions.assertEquals(4D, sampler.onSample());
    Assertions.assertTrue(sampler.isSampleAvailable());
    Mockito.verifyNoInteractions(bridge);
    bridges.verifyNoInteractions();
  }

  @Test
  void backgroundRequestsShareOneQueuedRefreshAndReturnTheCachedTotal() {
    scheduling.when(J::isPrimaryThread).thenReturn(false);
    Mockito.when(bridge.countPluginChunkTickets(firstWorld)).thenReturn(4L);
    AtomicReference<Runnable> pending = new AtomicReference<>();
    scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
      pending.set(invocation.getArgument(0));
      return null;
    });

    Assertions.assertEquals(0D, sampler.onSample());
    Assertions.assertEquals(0D, sampler.onSample());
    Mockito.verifyNoInteractions(bridge);
    scheduling.verify(() -> J.s(Mockito.any(Runnable.class)));
    pending.get().run();

    Assertions.assertEquals(4D, sampler.onSample());
  }
}
