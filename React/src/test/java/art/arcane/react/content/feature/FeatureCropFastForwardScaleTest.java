package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.Ticker;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class FeatureCropFastForwardScaleTest {
  private React previous;
  private UUID worldId;
  private World world;
  private ObserverController observer;
  private NearbyPlayerIndexController players;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getName()).thenReturn("React");
    Mockito.when(plugin.namespace()).thenReturn("react");
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
    worldId = UUID.randomUUID();
    world = Mockito.mock(World.class);
    Mockito.when(world.getUID()).thenReturn(worldId);
    Mockito.when(world.getSimulationDistance()).thenReturn(2);
    Mockito.when(world.getMinHeight()).thenReturn(0);
    Mockito.when(world.getMaxHeight()).thenReturn(320);
    observer = Mockito.mock(ObserverController.class);
    players = Mockito.mock(NearbyPlayerIndexController.class);
    Mockito.when(players.playerSnapshots()).thenReturn(List.of(new NearbyPlayerIndexController.PlayerViewSnapshot(
        UUID.randomUUID(), "player", worldId, 8D, 64D, 8D, 0D, false, false
    )));
    Mockito.when(players.hasNearbyPlayerInColumn(Mockito.eq(world), Mockito.anyDouble(), Mockito.anyDouble(), Mockito.eq(64D)))
        .thenAnswer(invocation -> {
          double dx = invocation.<Double>getArgument(1) - 8D;
          double dz = invocation.<Double>getArgument(2) - 8D;
          return dx * dx + dz * dz <= 64D * 64D;
        });
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void seedingReadsOneBoundedLoadedChunkWindowWithoutWorldAccess() {
    List<ObserverController.LoadedChunkTarget> indexed = new ArrayList<>(100_000);
    for (int index = 0; index < 100_000; index++) {
      indexed.add(new ObserverController.LoadedChunkTarget(worldId, index, -index));
    }
    Mockito.when(observer.nextLoadedChunkCoordinateBatch(128)).thenReturn(indexed.subList(0, 128));
    FeatureCropFastForward feature = new FeatureCropFastForward();
    feature.onActivate();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      stubControllers(react, bukkit);
      scheduling.when(J::isFoliaThreading).thenReturn(false);

      feature.onTick();

      Mockito.verify(observer).nextLoadedChunkCoordinateBatch(128);
      Mockito.verify(world, Mockito.never()).getLoadedChunks();
      Mockito.verify(world, Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt());
      Mockito.verify(world, Mockito.never()).isChunkLoaded(Mockito.anyInt(), Mockito.anyInt());
      scheduling.verify(() -> J.s(Mockito.any(Runnable.class)), Mockito.never());
    } finally {
      feature.onDeactivate();
    }
  }

  @Test
  void pendingChunksNearAPlayerDispatchOneJobEach() throws ReflectiveOperationException {
    FeatureCropFastForward feature = new FeatureCropFastForward();
    feature.onActivate();
    CropActivityLedger ledger = ledger(feature);
    pend(ledger, 0, 0);
    pend(ledger, 1, 0);
    pend(ledger, 50, 50);
    List<Runnable> jobs = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      stubControllers(react, bukkit);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        jobs.add(invocation.getArgument(0));
        return null;
      });

      feature.onTick();
      Assertions.assertEquals(2, jobs.size());
      feature.onTick();
      Assertions.assertEquals(2, jobs.size());
    } finally {
      feature.onDeactivate();
    }
    Assertions.assertEquals(0L, ledger.takePending(CropActivityLedger.pack(0, 0)));
    Assertions.assertTrue(ledger.takePending(CropActivityLedger.pack(50, 50)) > 0L);
  }

  @Test
  void fastForwardReadsBlockTypesFromTheChunkSnapshot() throws ReflectiveOperationException {
    FeatureCropFastForward feature = new FeatureCropFastForward();
    feature.onActivate();
    pend(ledger(feature), 0, 0);
    Chunk chunk = Mockito.mock(Chunk.class);
    ChunkSnapshot snapshot = Mockito.mock(ChunkSnapshot.class);
    Block wheat = Mockito.mock(Block.class);
    Ageable crop = Mockito.mock(Ageable.class);
    Mockito.when(world.isChunkLoaded(0, 0)).thenReturn(true);
    Mockito.when(world.getChunkAt(0, 0)).thenReturn(chunk);
    Mockito.when(chunk.getChunkSnapshot(true, false, false)).thenReturn(snapshot);
    Mockito.when(snapshot.getHighestBlockYAt(Mockito.anyInt(), Mockito.anyInt())).thenReturn(64);
    Mockito.when(snapshot.getBlockType(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(Material.STONE);
    Mockito.when(snapshot.getBlockType(3, 64, 3)).thenReturn(Material.WHEAT);
    Mockito.when(world.getBlockAt(3, 64, 3)).thenReturn(wheat);
    Mockito.when(wheat.getType()).thenReturn(Material.WHEAT);
    Mockito.when(wheat.getBlockData()).thenReturn(crop);
    Mockito.when(crop.getMaximumAge()).thenReturn(7);
    List<Runnable> jobs = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      stubControllers(react, bukkit);
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        jobs.add(invocation.getArgument(0));
        return null;
      });

      feature.onTick();
      Assertions.assertEquals(1, jobs.size());
      jobs.getFirst().run();
    } finally {
      feature.onDeactivate();
    }

    Mockito.verify(world, Mockito.times(1)).getBlockAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());
    Mockito.verify(crop).setAge(Mockito.intThat(age -> age > 0 && age <= 7));
    Mockito.verify(wheat).setBlockData(crop, false);
  }

  @Test
  void silencedPassesKeepStampingButDoNotDispatch() throws ReflectiveOperationException {
    FeatureCropFastForward feature = new FeatureCropFastForward();
    feature.onActivate();
    CropActivityLedger ledger = ledger(feature);
    pend(ledger, 0, 0);
    Sampler tickTime = Mockito.mock(Sampler.class);
    Mockito.when(tickTime.sample()).thenReturn(120D);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      stubControllers(react, bukkit);
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(tickTime);
      scheduling.when(J::isFoliaThreading).thenReturn(false);

      feature.onTick();

      scheduling.verify(() -> J.s(Mockito.any(Runnable.class)), Mockito.never());
    } finally {
      feature.onDeactivate();
    }
    Assertions.assertEquals(25, ledger.size());
  }

  @Test
  void ownerTaskRetiresWithoutWorldAccessAfterDeactivation() throws ReflectiveOperationException {
    FeatureCropFastForward feature = new FeatureCropFastForward();
    feature.onActivate();
    pend(ledger(feature), 0, 0);
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      stubControllers(react, bukkit);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runChunk(
          Mockito.eq(world),
          Mockito.eq(0),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(3));
        return true;
      });

      feature.onTick();
      Assertions.assertNotNull(ownerTask.get());
      Mockito.clearInvocations(world);
      feature.onDeactivate();
      ownerTask.get().run();

      Mockito.verifyNoInteractions(world);
    }
  }

  private void stubControllers(MockedStatic<React> react, MockedStatic<Bukkit> bukkit) {
    react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
    react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(players);
    bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
  }

  private static void pend(CropActivityLedger ledger, int chunkX, int chunkZ) {
    ledger.loaded(CropActivityLedger.pack(chunkX, chunkZ), 1_000L);
    ledger.stampInside(chunkX, chunkZ, 0, 1_205_000L, 1_201_000L, 200L, 24_000L);
  }

  @SuppressWarnings("unchecked")
  private CropActivityLedger ledger(FeatureCropFastForward feature) throws ReflectiveOperationException {
    Field field = FeatureCropFastForward.class.getDeclaredField("ledgers");
    field.setAccessible(true);
    Map<UUID, CropActivityLedger> ledgers = (Map<UUID, CropActivityLedger>) field.get(feature);
    return ledgers.computeIfAbsent(worldId, ignored -> new CropActivityLedger());
  }
}
