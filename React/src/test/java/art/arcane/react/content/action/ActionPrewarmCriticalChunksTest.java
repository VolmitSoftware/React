package art.arcane.react.content.action;

import art.arcane.react.React;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.content.sampler.SamplerHopperUpdates;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.core.controller.NearbyPlayerIndexController.PlayerViewSnapshot;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledServer;
import art.arcane.react.model.SampledWorld;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.scheduling.WorldChunks;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

class ActionPrewarmCriticalChunksTest {
  @Test
  void waitsForAsyncLoadAndOwnerFinishingBeforeCompleting() {
    try (Fixture fixture = new Fixture(1)) {
      fixture.step();
      Assertions.assertFalse(fixture.ticket.isDone());
      fixture.owner();
      Assertions.assertEquals(1, fixture.loads.size());
      fixture.loaded.set(true);
      fixture.loads.getFirst().complete(fixture.chunk);
      Mockito.verifyNoInteractions(fixture.chunk);
      Assertions.assertEquals(1, fixture.tasks.size());
      fixture.step();
      Assertions.assertFalse(fixture.ticket.isDone());
      fixture.owner();
      fixture.step();
      Assertions.assertTrue(fixture.ticket.isDone());
      Assertions.assertFalse(fixture.ticket.isFailed());
      Assertions.assertEquals(1, fixture.ticket.getCount());
      Assertions.assertEquals(1, fixture.ticket.getParams().getChunksLoaded());
      Mockito.verify(fixture.chunk).getChunkSnapshot(false, false, false);
      Mockito.verify(fixture.chunk).getEntities();
      Mockito.verify(fixture.world, Mockito.never()).loadChunk(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());
      Mockito.verify(fixture.world, Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt());
    }
  }

  @Test
  void existingChunksWarmDirectlyWithoutIssuingAsyncLoads() {
    try (Fixture fixture = new Fixture(1)) {
      fixture.loaded.set(true);
      fixture.ticket.getParams().setTouchChunkSnapshot(false);
      fixture.step();
      fixture.owner();
      fixture.step();
      Assertions.assertTrue(fixture.ticket.isDone());
      Assertions.assertEquals(1, fixture.ticket.getCount());
      Assertions.assertEquals(0, fixture.ticket.getParams().getChunksLoaded());
      Assertions.assertTrue(fixture.loads.isEmpty());
      Mockito.verify(fixture.chunk).getEntities();
      Mockito.verify(fixture.chunk, Mockito.never()).getChunkSnapshot(Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean());
    }
  }

  @Test
  void missingChunksStayUngeneratedWhenGenerationIsDisabled() {
    try (Fixture fixture = new Fixture(1)) {
      fixture.ticket.getParams().setGenerateMissingChunks(false);
      fixture.step();
      fixture.owner();
      fixture.chunks.verify(() -> WorldChunks.load(React.instance, fixture.world, 0, 0, false));
      fixture.loads.getFirst().complete(null);
      fixture.step();
      Assertions.assertTrue(fixture.ticket.isDone());
      Assertions.assertFalse(fixture.ticket.isFailed());
      Assertions.assertEquals(0, fixture.ticket.getCount());
      Mockito.verifyNoInteractions(fixture.chunk);
    }
  }

  @Test
  void limitsPendingLoadsEvenAtExtremeActionSpeed() {
    try (Fixture fixture = new Fixture(100)) {
      Mockito.when(fixture.controller.getActionSpeedMultiplier()).thenReturn(Integer.MAX_VALUE);
      for (int cycle = 0; cycle < 5; cycle++) {
        fixture.step();
        while (!fixture.tasks.isEmpty()) fixture.owner();
      }
      Assertions.assertEquals(32, fixture.loads.size());
      Assertions.assertEquals(32, fixture.ticket.getParams().getInFlightChunks().get());
      fixture.ticket.fail(new IllegalStateException("Cancelled"));
      Assertions.assertTrue(fixture.loads.stream().allMatch(CompletableFuture::isCancelled));
      Assertions.assertEquals(0, fixture.ticket.getParams().getInFlightChunks().get());
      fixture.step();
      Assertions.assertEquals(32, fixture.loads.size());
    }
  }

  @Test
  void cancellationBeforeOwnerDispatchPreventsWorldAccess() {
    try (Fixture fixture = new Fixture(1)) {
      fixture.step();
      fixture.ticket.fail(new IllegalStateException("Cancelled"));
      fixture.owner();
      Assertions.assertTrue(fixture.loads.isEmpty());
      Assertions.assertEquals(0, fixture.ticket.getParams().getInFlightChunks().get());
      Mockito.verify(fixture.world, Mockito.never()).isChunkLoaded(Mockito.anyInt(), Mockito.anyInt());
    }
  }

  @Test
  void cancellationAfterLoadCompletionPreventsQueuedWarmup() {
    try (Fixture fixture = new Fixture(1)) {
      fixture.step();
      fixture.owner();
      fixture.loaded.set(true);
      fixture.loads.getFirst().complete(fixture.chunk);
      fixture.ticket.fail(new IllegalStateException("Cancelled"));
      fixture.owner();
      Mockito.verifyNoInteractions(fixture.chunk);
      Assertions.assertEquals(0, fixture.ticket.getParams().getInFlightChunks().get());
      Assertions.assertEquals(1, fixture.ticket.getParams().getChunksProcessedAtomic().get());
    }
  }

  @Test
  void cancellationDuringWarmupDoesNotPublishSuccessAfterTerminalFailure() {
    try (Fixture fixture = new Fixture(1)) {
      fixture.step();
      fixture.owner();
      fixture.loaded.set(true);
      Mockito.when(fixture.chunk.getEntities()).thenAnswer(ignored -> {
        fixture.ticket.fail(new IllegalStateException("Cancelled while warming"));
        return null;
      });
      fixture.loads.getFirst().complete(fixture.chunk);
      fixture.owner();
      fixture.step();
      Assertions.assertTrue(fixture.ticket.isFailed());
      Assertions.assertEquals(0, fixture.ticket.getParams().getChunksWarmedAtomic().get());
      Assertions.assertEquals(0, fixture.ticket.getParams().getChunksLoadedAtomic().get());
      Assertions.assertEquals(0, fixture.ticket.getCount());
      Assertions.assertEquals(0, fixture.ticket.getParams().getInFlightChunks().get());
    }
  }

  @Test
  void unloadedChunkIsNeverSynchronouslyReloadedByDelayedContinuation() {
    try (Fixture fixture = new Fixture(1)) {
      fixture.step();
      fixture.owner();
      fixture.loads.getFirst().complete(fixture.chunk);
      fixture.owner();
      fixture.step();
      Assertions.assertTrue(fixture.ticket.isDone());
      Assertions.assertEquals(0, fixture.ticket.getCount());
      Mockito.verifyNoInteractions(fixture.chunk);
      Mockito.verify(fixture.world, Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt());
      Mockito.verify(fixture.world, Mockito.never()).loadChunk(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());
    }
  }

  @Test
  void loadFailureFailsTicketAndCancelsOtherPendingLoads() {
    try (Fixture fixture = new Fixture(2)) {
      fixture.step();
      fixture.owner();
      fixture.step();
      fixture.owner();
      IllegalStateException failure = new IllegalStateException("Disk read failed");
      fixture.loads.getFirst().completeExceptionally(failure);
      Assertions.assertTrue(fixture.ticket.isFailed());
      Assertions.assertSame(failure, fixture.ticket.getFailure());
      Assertions.assertTrue(fixture.loads.get(1).isCancelled());
      Assertions.assertEquals(0, fixture.ticket.getParams().getInFlightChunks().get());
      fixture.react.verify(() -> React.reportError(Mockito.contains("Chunk prewarm failed"), Mockito.same(failure)));
    }
  }

  @Test
  void rejectedOwnerSchedulingFailsWithoutLeakingAnInflightSlot() {
    try (Fixture fixture = new Fixture(1)) {
      fixture.acceptTasks.set(false);
      fixture.step();
      Assertions.assertTrue(fixture.ticket.isFailed());
      Assertions.assertEquals(0, fixture.ticket.getParams().getInFlightChunks().get());
      Assertions.assertTrue(fixture.loads.isEmpty());
    }
  }

  @Test
  void rejectedCompletionSchedulingFailsWithoutTouchingChunk() {
    try (Fixture fixture = new Fixture(1)) {
      fixture.step();
      fixture.owner();
      fixture.acceptTasks.set(false);
      fixture.loads.getFirst().complete(fixture.chunk);
      Assertions.assertTrue(fixture.ticket.isFailed());
      Assertions.assertEquals(0, fixture.ticket.getParams().getInFlightChunks().get());
      Mockito.verifyNoInteractions(fixture.chunk);
    }
  }

  @Test
  void usesSeededPlayerSnapshotsOnFoliaWithoutReadingPlayerState() {
    try (Fixture fixture = new Fixture(0);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      UUID playerId = UUID.randomUUID();
      UUID worldId = UUID.randomUUID();
      Mockito.when(fixture.players.playerSnapshots()).thenReturn(List.of(
          new PlayerViewSnapshot(playerId, "Example", worldId, -0.5D, 64D, -16.1D, 0D, false, false)));
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(fixture.world);
      fixture.identity.when(() -> WorldIdentity.serialize(fixture.world)).thenReturn("test:world");
      fixture.scheduling.when(J::isFoliaThreading).thenReturn(true);
      fixture.ticket.getParams().setIncludePlayerChunks(true).setPlayerChunkRadius(0);
      fixture.step();
      Assertions.assertFalse(fixture.ticket.getParams().isPrepared());
      Assertions.assertTrue(fixture.tasks.isEmpty());
      Mockito.when(fixture.players.isInitialSeedReady()).thenReturn(true);
      fixture.step();
      fixture.owner();
      fixture.chunks.verify(() -> WorldChunks.load(React.instance, fixture.world, -1, -2, true));
      bukkit.verify(Bukkit::getOnlinePlayers, Mockito.never());
    }
  }

  private static final class Fixture implements AutoCloseable {
    private final ActionPrewarmCriticalChunks action = new ActionPrewarmCriticalChunks();
    private final World world = Mockito.mock(World.class);
    private final Chunk chunk = Mockito.mock(Chunk.class);
    private final ActionController controller = Mockito.mock(ActionController.class);
    private final NearbyPlayerIndexController players = Mockito.mock(NearbyPlayerIndexController.class);
    private final MockedStatic<React> react = Mockito.mockStatic(React.class);
    private final MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
    private final MockedStatic<WorldIdentity> identity = Mockito.mockStatic(WorldIdentity.class);
    private final MockedStatic<WorldChunks> chunks = Mockito.mockStatic(WorldChunks.class);
    private final AtomicBoolean owned = new AtomicBoolean();
    private final AtomicBoolean loaded = new AtomicBoolean();
    private final AtomicBoolean acceptTasks = new AtomicBoolean(true);
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<CompletableFuture<Chunk>> loads = new ArrayList<>();
    private final ActionTicket<ActionPrewarmCriticalChunks.Params> ticket;

    private Fixture(int targets) {
      ObserverController observer = Mockito.mock(ObserverController.class);
      SampledServer sampled = new SampledServer();
      SampledWorld sampledWorld = new SampledWorld(UUID.randomUUID(), "test:world");
      for (int index = 0; index < targets; index++) {
        sampledWorld.getChunk(index, 0).get(SamplerHopperUpdates.ID).set(10000D);
      }
      sampled.getWorlds().put(sampledWorld.getWorldId(), sampledWorld);
      Mockito.when(observer.getSampled()).thenReturn(sampled);
      Mockito.when(controller.getActionSpeedMultiplier()).thenReturn(8);
      react.when(() -> React.controller(ActionController.class)).thenReturn(controller);
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      react.when(() -> React.controller(NearbyPlayerIndexController.class)).thenReturn(players);
      identity.when(() -> WorldIdentity.resolve("test:world")).thenReturn(Optional.of(world));
      Mockito.when(world.isChunkLoaded(Mockito.anyInt(), Mockito.anyInt())).thenAnswer(ignored -> {
        Assertions.assertTrue(owned.get(), "Chunk state requires region ownership");
        return loaded.get();
      });
      Mockito.when(world.getChunkAt(Mockito.anyInt(), Mockito.anyInt())).thenReturn(chunk);
      scheduling.when(() -> J.isOwnedByCurrentRegion(Mockito.any(Location.class))).thenAnswer(ignored -> owned.get());
      scheduling.when(() -> J.runChunk(Mockito.same(world), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            if (!acceptTasks.get()) return false;
            tasks.add(invocation.getArgument(3));
            return true;
          });
      chunks.when(() -> WorldChunks.load(Mockito.same(React.instance), Mockito.same(world), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean()))
          .thenAnswer(ignored -> {
            CompletableFuture<Chunk> loading = new CompletableFuture<>();
            loads.add(loading);
            return loading;
          });
      ticket = action.create(ActionPrewarmCriticalChunks.Params.builder()
          .includePlayerChunks(false).neighborRadius(0).maxChunks(Math.max(1, targets)).build());
      ticket.start();
    }

    private void step() {
      action.workOn(ticket);
    }

    private void owner() {
      owned.set(true);
      try {
        tasks.removeFirst().run();
      } finally {
        owned.set(false);
      }
    }

    @Override
    public void close() {
      chunks.close();
      identity.close();
      scheduling.close();
      react.close();
    }
  }
}
