package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.model.PlayerSettings;
import art.arcane.react.model.ReactPlayer;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.Ticker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class PlayerControllerTest {
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
  void routesPlayerEventsToOnlyTheTrackedRuntime() {
    PlayerController controller = new PlayerController();
    UUID playerId = UUID.randomUUID();
    Player player = player(playerId);
    ReactPlayer reactPlayer = Mockito.mock(ReactPlayer.class);
    PlayerMoveEvent moveEvent = event(PlayerMoveEvent.class, player);
    PlayerToggleSneakEvent sneakEvent = event(PlayerToggleSneakEvent.class, player);
    PlayerItemHeldEvent heldEvent = event(PlayerItemHeldEvent.class, player);
    controller.getPlayers().put(playerId, reactPlayer);

    controller.on(moveEvent);
    controller.on(sneakEvent);
    controller.on(heldEvent);

    Mockito.verify(reactPlayer).handleMove(moveEvent);
    Mockito.verify(reactPlayer).handleToggleSneak(sneakEvent);
    Mockito.verify(reactPlayer).handleItemHeld(heldEvent);
  }

  @Test
  void ignoresPlayerEventsWithoutATrackedRuntime() {
    PlayerController controller = new PlayerController();
    Player player = player(UUID.randomUUID());

    controller.on(event(PlayerMoveEvent.class, player));
    controller.on(event(PlayerToggleSneakEvent.class, player));
    controller.on(event(PlayerItemHeldEvent.class, player));

    Assertions.assertTrue(controller.getPlayers().isEmpty());
  }

  @Test
  void quitCleansTrackedRuntimeAfterPermissionLoss() {
    PlayerController controller = new PlayerController();
    UUID playerId = UUID.randomUUID();
    Player player = player(playerId);
    ReactPlayer reactPlayer = Mockito.mock(ReactPlayer.class);
    Mockito.when(player.hasPermission("react.use")).thenReturn(false);
    controller.getPlayers().put(playerId, reactPlayer);

    controller.quit(player);

    Assertions.assertFalse(controller.getPlayers().containsKey(playerId));
    Mockito.verify(reactPlayer).onQuit();
    Mockito.verify(reactPlayer).unregister();
  }

  @Test
  void bulkStartupLoadsProfilesOffTheServerThread() {
    PlayerController controller = new PlayerController();
    UUID playerId = UUID.randomUUID();
    Player player = player(playerId);
    PlayerSettings settings = Mockito.mock(PlayerSettings.class);
    ArgumentCaptor<Runnable> ioTask = ArgumentCaptor.forClass(Runnable.class);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class);
         MockedStatic<PlayerSettings> profiles = Mockito.mockStatic(PlayerSettings.class)) {
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
      profiles.when(() -> PlayerSettings.get(playerId)).thenReturn(settings);

      controller.start();

      profiles.verify(() -> PlayerSettings.get(playerId), Mockito.never());
      scheduler.verify(() -> J.a(ioTask.capture()));
      ioTask.getValue().run();
      profiles.verify(() -> PlayerSettings.get(playerId));
      scheduler.verify(() -> J.runEntity(
          Mockito.same(player),
          Mockito.any(Runnable.class),
          Mockito.eq(0),
          Mockito.any(Runnable.class)
      ));
    }
  }

  @Test
  void rejectedHotReloadRestoreRetriesWithoutReloadingTheProfile() {
    PlayerController controller = Mockito.spy(new PlayerController());
    UUID playerId = UUID.randomUUID();
    Player player = player(playerId);
    PlayerSettings settings = Mockito.mock(PlayerSettings.class);
    ReactPlayer runtime = Mockito.mock(ReactPlayer.class);
    List<Runnable> asyncTasks = new ArrayList<>();
    AtomicInteger dispatches = new AtomicInteger();
    AtomicReference<Runnable> ownerOperation = new AtomicReference<>();
    Mockito.when(player.isOnline()).thenReturn(true);
    Mockito.when(player.hasPermission("react.use")).thenReturn(true);
    Mockito.doReturn(runtime).when(controller).createPlayer(player, settings);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<J> scheduler = Mockito.mockStatic(J.class);
         MockedStatic<PlayerSettings> profiles = Mockito.mockStatic(PlayerSettings.class)) {
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
      profiles.when(() -> PlayerSettings.get(playerId)).thenReturn(settings);
      scheduler.when(() -> J.a(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        asyncTasks.add(invocation.getArgument(0));
        return null;
      });
      scheduler.when(() -> J.runEntity(
              Mockito.same(player),
              Mockito.any(Runnable.class),
              Mockito.eq(0),
              Mockito.any(Runnable.class)))
          .thenAnswer(invocation -> {
            if (dispatches.incrementAndGet() == 1) {
              return false;
            }
            ownerOperation.set(invocation.getArgument(1));
            return true;
          });

      controller.start();
      Assertions.assertEquals(1, asyncTasks.size());
      asyncTasks.removeFirst().run();
      Assertions.assertEquals(1, asyncTasks.size());
      asyncTasks.removeFirst().run();

      Assertions.assertEquals(2, dispatches.get());
      Assertions.assertNotNull(ownerOperation.get());
      ownerOperation.get().run();
      Assertions.assertSame(runtime, controller.getPlayers().get(playerId));
      profiles.verify(() -> PlayerSettings.get(playerId), Mockito.times(1));
      Mockito.verify(controller).createPlayer(player, settings);
    }
  }

  @Test
  void asyncPreloginProfileIsReusedWithoutPlayerThreadDiskIo() {
    PlayerController controller = new PlayerController();
    UUID playerId = UUID.randomUUID();
    Player player = player(playerId);
    Mockito.when(player.hasPermission("react.use")).thenReturn(true);
    PlayerSettings settings = Mockito.mock(PlayerSettings.class);
    AsyncPlayerPreLoginEvent event = Mockito.mock(AsyncPlayerPreLoginEvent.class);
    Mockito.when(event.getUniqueId()).thenReturn(playerId);
    Mockito.when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.ALLOWED);
    List<List<?>> constructorArguments = new ArrayList<>();

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<PlayerSettings> profiles = Mockito.mockStatic(PlayerSettings.class);
         MockedConstruction<ReactPlayer> players = Mockito.mockConstruction(
             ReactPlayer.class,
             (mock, context) -> constructorArguments.add(context.arguments())
         )) {
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
      profiles.when(() -> PlayerSettings.get(playerId)).thenReturn(settings);

      controller.start();
      controller.on(event);
      ReactPlayer reactPlayer = controller.join(player);

      Assertions.assertNotNull(reactPlayer);
      profiles.verify(() -> PlayerSettings.get(playerId), Mockito.times(1));
      Assertions.assertEquals(1, players.constructed().size());
      Assertions.assertSame(player, constructorArguments.getFirst().get(0));
      Assertions.assertSame(settings, constructorArguments.getFirst().get(1));
    }
  }

  @Test
  void stopWaitsForConcurrentJoinAndDisposesTheInsertedRuntime() throws Exception {
    CountDownLatch constructed = new CountDownLatch(1);
    CountDownLatch releaseConstruction = new CountDownLatch(1);
    ReactPlayer runtime = Mockito.mock(ReactPlayer.class);
    PlayerController controller = new BlockingPlayerController(constructed, releaseConstruction, runtime);
    UUID playerId = UUID.randomUUID();
    Player player = player(playerId);
    Mockito.when(player.hasPermission("react.use")).thenReturn(true);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class);
         MockedStatic<PlayerSettings> profiles = Mockito.mockStatic(PlayerSettings.class)) {
      bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
      profiles.when(() -> PlayerSettings.flushPendingSaves(Mockito.anyLong())).thenReturn(true);
      controller.start();

      Future<ReactPlayer> joined = executor.submit(() -> controller.join(player));
      Assertions.assertTrue(constructed.await(2, TimeUnit.SECONDS));
      Future<?> stopped = executor.submit(controller::stop);
      Thread.sleep(50L);
      Assertions.assertFalse(stopped.isDone());

      releaseConstruction.countDown();
      ReactPlayer joinedRuntime = joined.get(2, TimeUnit.SECONDS);
      stopped.get(2, TimeUnit.SECONDS);

      Assertions.assertSame(runtime, joinedRuntime);
      Assertions.assertTrue(controller.getPlayers().isEmpty());
      Mockito.verify(runtime).onQuit();
      Mockito.verify(runtime).unregister();
    } finally {
      releaseConstruction.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void stopWaitsUntilConcurrentQuitFinishesDisposal() throws Exception {
    PlayerController controller = new PlayerController();
    UUID playerId = UUID.randomUUID();
    Player player = player(playerId);
    ReactPlayer runtime = Mockito.mock(ReactPlayer.class);
    CountDownLatch disposalStarted = new CountDownLatch(1);
    CountDownLatch releaseDisposal = new CountDownLatch(1);
    CountDownLatch stopStarted = new CountDownLatch(1);
    AtomicReference<Thread> stopThread = new AtomicReference<>();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    controller.getPlayers().put(playerId, runtime);
    Mockito.doAnswer(invocation -> {
      disposalStarted.countDown();
      if (!releaseDisposal.await(2, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting to release player disposal");
      }
      return null;
    }).when(runtime).onQuit();

    try {
      Future<?> quit = executor.submit(() -> controller.quit(player));
      Assertions.assertTrue(disposalStarted.await(2, TimeUnit.SECONDS));
      Future<?> stopped = executor.submit(() -> {
        stopThread.set(Thread.currentThread());
        stopStarted.countDown();
        controller.stop();
      });
      Assertions.assertTrue(stopStarted.await(2, TimeUnit.SECONDS));
      Assertions.assertTrue(awaitQueuedWriter(controller, stopThread.get(), stopped));
      Assertions.assertFalse(stopped.isDone());

      releaseDisposal.countDown();
      quit.get(2, TimeUnit.SECONDS);
      stopped.get(2, TimeUnit.SECONDS);

      Mockito.verify(runtime).onQuit();
      Mockito.verify(runtime).unregister();
    } finally {
      releaseDisposal.countDown();
      executor.shutdownNow();
      Assertions.assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
    }
  }

  private boolean awaitQueuedWriter(PlayerController controller, Thread writer, Future<?> stopped) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (!controller.getLifecycleLock().hasQueuedThread(writer) && !stopped.isDone()) {
      if (System.nanoTime() >= deadline) {
        return false;
      }
      Thread.onSpinWait();
    }
    return controller.getLifecycleLock().hasQueuedThread(writer);
  }

  private Player player(UUID playerId) {
    Player player = Mockito.mock(Player.class);
    Mockito.when(player.getUniqueId()).thenReturn(playerId);
    return player;
  }

  private <T> T event(Class<T> type, Player player) {
    T event = Mockito.mock(type);
    if (event instanceof PlayerMoveEvent moveEvent) {
      Mockito.when(moveEvent.getPlayer()).thenReturn(player);
    } else if (event instanceof PlayerToggleSneakEvent sneakEvent) {
      Mockito.when(sneakEvent.getPlayer()).thenReturn(player);
    } else if (event instanceof PlayerItemHeldEvent heldEvent) {
      Mockito.when(heldEvent.getPlayer()).thenReturn(player);
    }
    return event;
  }

  private static final class BlockingPlayerController extends PlayerController {
    private final CountDownLatch constructed;
    private final CountDownLatch releaseConstruction;
    private final ReactPlayer runtime;

    private BlockingPlayerController(
        CountDownLatch constructed,
        CountDownLatch releaseConstruction,
        ReactPlayer runtime
    ) {
      this.constructed = constructed;
      this.releaseConstruction = releaseConstruction;
      this.runtime = runtime;
    }

    @Override
    ReactPlayer createPlayer(Player player) {
      constructed.countDown();
      try {
        if (!releaseConstruction.await(2, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to release player construction");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(exception);
      }
      return runtime;
    }
  }
}
