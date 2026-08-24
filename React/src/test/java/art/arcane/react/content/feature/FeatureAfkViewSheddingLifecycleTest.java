package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class FeatureAfkViewSheddingLifecycleTest {
  @Test
  void overlappingClaimsRestoreTheExactOriginalDistance() {
    Player player = Mockito.mock(Player.class);
    FeatureAfkViewShedding.ViewDistanceState state =
        new FeatureAfkViewShedding.ViewDistanceState(12, player);

    Assertions.assertTrue(state.claim(FeatureAfkViewShedding.ClaimKind.PRESSURE, 1L, 8));
    Assertions.assertTrue(state.claim(FeatureAfkViewShedding.ClaimKind.IDLE, 1L, 4));
    Assertions.assertEquals(4, state.desiredDistance());

    Assertions.assertTrue(state.release(FeatureAfkViewShedding.ClaimKind.PRESSURE, 1L));
    Assertions.assertEquals(4, state.desiredDistance());
    Assertions.assertTrue(state.release(FeatureAfkViewShedding.ClaimKind.IDLE, 1L));
    Assertions.assertEquals(12, state.desiredDistance());
    Assertions.assertTrue(state.isUnclaimed());
  }

  @Test
  void staleCleanupCannotReleaseANewerClaim() {
    Player player = Mockito.mock(Player.class);
    FeatureAfkViewShedding.ViewDistanceState state =
        new FeatureAfkViewShedding.ViewDistanceState(12, player);

    Assertions.assertTrue(state.claim(FeatureAfkViewShedding.ClaimKind.IDLE, 1L, 4));
    Assertions.assertTrue(state.releaseGeneration(1L));
    Assertions.assertTrue(state.claim(FeatureAfkViewShedding.ClaimKind.IDLE, 2L, 5));

    Assertions.assertFalse(state.releaseGeneration(1L));
    Assertions.assertEquals(5, state.desiredDistance());
    Assertions.assertFalse(state.isUnclaimed());
  }

  @Test
  void delayedIdleClaimStopsAfterPlayerActivity() throws ReflectiveOperationException {
    FeatureAfkViewShedding feature = new FeatureAfkViewShedding();
    EntityController controller = Mockito.mock(EntityController.class);
    Sampler sampler = Mockito.mock(Sampler.class);
    Player player = Mockito.mock(Player.class);
    PlayerMoveEvent moveEvent = Mockito.mock(PlayerMoveEvent.class);
    UUID playerId = UUID.randomUUID();
    List<Runnable> queued = new ArrayList<>();
    Mockito.when(controller.getFoliaPlayers()).thenReturn(new Player[]{player});
    Mockito.when(sampler.sample()).thenReturn(0D);
    Mockito.when(player.getUniqueId()).thenReturn(playerId);
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(player.getSendViewDistance()).thenReturn(12);
    Mockito.when(moveEvent.getPlayer()).thenReturn(player);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      scheduling.when(() -> J.runEntity(Mockito.same(player), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            queued.add(invocation.getArgument(1));
            return true;
          });

      feature.onActivate();
      markIdle(feature, playerId);
      feature.onTick();
      Assertions.assertEquals(1, queued.size());

      feature.on(moveEvent);
      queued.removeFirst().run();

      Mockito.verify(player, Mockito.never()).setSendViewDistance(Mockito.anyInt());
    }
  }

  @Test
  void queuedActivityRestoreRemainsTrackedThroughDeactivation() throws Exception {
    FeatureAfkViewShedding feature = new FeatureAfkViewShedding();
    EntityController controller = Mockito.mock(EntityController.class);
    Sampler sampler = Mockito.mock(Sampler.class);
    Player player = Mockito.mock(Player.class);
    PlayerMoveEvent moveEvent = Mockito.mock(PlayerMoveEvent.class);
    UUID playerId = UUID.randomUUID();
    AtomicInteger distance = new AtomicInteger(12);
    List<Runnable> queued = new ArrayList<>();
    CountDownLatch restoreScheduled = new CountDownLatch(1);
    AtomicReference<Runnable> restore = new AtomicReference<>();
    AtomicReference<Throwable> completionFailure = new AtomicReference<>();
    Mockito.when(controller.getFoliaPlayers()).thenReturn(new Player[]{player});
    Mockito.when(sampler.sample()).thenReturn(0D);
    Mockito.when(player.getUniqueId()).thenReturn(playerId);
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(player.getSendViewDistance()).thenAnswer(invocation -> distance.get());
    Mockito.when(moveEvent.getPlayer()).thenReturn(player);
    Mockito.doAnswer(invocation -> {
      distance.set(invocation.getArgument(0));
      return null;
    }).when(player).setSendViewDistance(Mockito.anyInt());

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      scheduling.when(() -> J.runEntity(Mockito.same(player), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            queued.add(invocation.getArgument(1));
            return true;
          });
      scheduling.when(() -> J.runEntity(
          Mockito.same(player),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        restore.set(invocation.getArgument(1));
        restoreScheduled.countDown();
        return true;
      });

      feature.onActivate();
      markIdle(feature, playerId);
      feature.onTick();
      Assertions.assertEquals(1, queued.size());
      queued.removeFirst().run();
      Assertions.assertEquals(4, distance.get());
      feature.on(moveEvent);
      Assertions.assertEquals(4, distance.get());

      Thread completion = new Thread(() -> {
        try {
          if (!restoreScheduled.await(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("AFK restore was not scheduled");
          }
          TimeUnit.MILLISECONDS.sleep(100L);
          restore.get().run();
        } catch (Throwable failure) {
          completionFailure.set(failure);
        }
      });
      completion.start();
      long started = System.nanoTime();
      feature.onDeactivate();
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      completion.join(2_000L);

      Assertions.assertFalse(completion.isAlive());
      Assertions.assertNull(completionFailure.get());
      Assertions.assertEquals(12, distance.get());
      Assertions.assertTrue(elapsedMillis >= 75L);
      scheduling.verify(() -> J.runEntity(
          Mockito.same(player),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      ), Mockito.times(1));
    }
  }

  @Test
  void retirementFollowedByRejectedSubmissionCompletesExactlyOnce() throws ReflectiveOperationException {
    FeatureAfkViewShedding feature = new FeatureAfkViewShedding();
    EntityController controller = Mockito.mock(EntityController.class);
    Sampler sampler = Mockito.mock(Sampler.class);
    Player player = Mockito.mock(Player.class);
    UUID playerId = UUID.randomUUID();
    AtomicInteger distance = new AtomicInteger(12);
    Mockito.when(controller.getFoliaPlayers()).thenReturn(new Player[]{player});
    Mockito.when(sampler.sample()).thenReturn(0D);
    Mockito.when(player.getUniqueId()).thenReturn(playerId);
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(player.getSendViewDistance()).thenAnswer(invocation -> distance.get());
    Mockito.doAnswer(invocation -> {
      distance.set(invocation.getArgument(0));
      return null;
    }).when(player).setSendViewDistance(Mockito.anyInt());

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(EntityController.class)).thenReturn(controller);
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(sampler);
      scheduling.when(() -> J.runEntity(Mockito.same(player), Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            Runnable operation = invocation.getArgument(1);
            operation.run();
            return true;
          });
      scheduling.when(() -> J.runEntity(
          Mockito.same(player),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        Runnable retired = invocation.getArgument(3);
        retired.run();
        return false;
      });

      feature.onActivate();
      markIdle(feature, playerId);
      feature.onTick();
      Assertions.assertEquals(4, distance.get());

      Assertions.assertDoesNotThrow(feature::onDeactivate);
    }
  }

  @SuppressWarnings("unchecked")
  private static void markIdle(FeatureAfkViewShedding feature, UUID playerId) throws ReflectiveOperationException {
    Field activityField = FeatureAfkViewShedding.class.getDeclaredField("lastActivityMs");
    activityField.setAccessible(true);
    Map<UUID, Long> activity = (Map<UUID, Long>) activityField.get(feature);
    activity.put(playerId, 0L);
  }
}
