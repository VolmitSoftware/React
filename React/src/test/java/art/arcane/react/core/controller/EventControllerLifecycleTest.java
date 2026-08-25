package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.event.NaughtyRegisteredListener;
import art.arcane.react.api.event.layer.ServerTickEvent;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayDeque;
import java.util.Deque;

class EventControllerLifecycleTest {
  private React previous;
  private React plugin;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    Mockito.when(plugin.isEnabled()).thenReturn(true);
    Mockito.when(plugin.getName()).thenReturn("MeasuredPlugin");
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void foliaMutationsAreScheduledAndEachWindowIsDrainedOnce() throws EventException {
    HandlerFixture fixture = new HandlerFixture(plugin);
    try (SchedulerHarness scheduler = new SchedulerHarness(plugin, true, false)) {
      EventController controller = new EventController();
      controller.start();

      Assertions.assertFalse(fixture.registered() instanceof NaughtyRegisteredListener);
      scheduler.runFirst();
      controller.markSamplerActivity();
      controller.onTick();
      Assertions.assertFalse(fixture.registered() instanceof NaughtyRegisteredListener);

      scheduler.runFirst();
      NaughtyRegisteredListener measured = Assertions.assertInstanceOf(
          NaughtyRegisteredListener.class,
          fixture.registered()
      );
      for (int call = 0; call < 5; call++) {
        measured.callEvent(Mockito.mock(Event.class));
        controller.on(new ServerTickEvent());
      }

      controller.markSamplerActivity();
      controller.onTick();
      scheduler.runFirst();
      Assertions.assertEquals(5, controller.getCalls());
      Assertions.assertEquals(1D, controller.getCallsPerTick());
      Assertions.assertEquals(5, controller.snapshotPluginEventCalls().get("MeasuredPlugin"));

      controller.markSamplerActivity();
      controller.onTick();
      scheduler.runFirst();
      Assertions.assertEquals(0, controller.getCalls());
      Assertions.assertEquals(0D, controller.getCallsPerTick());
      Assertions.assertEquals(0, controller.snapshotPluginEventCalls().get("MeasuredPlugin"));

      controller.stop();
      scheduler.runAll();
      Assertions.assertFalse(fixture.registered() instanceof NaughtyRegisteredListener);
    } finally {
      fixture.close();
    }
  }

  @Test
  void eventCallsAreNormalizedByObservedServerTicks() {
    Assertions.assertEquals(2.5D, EventController.averageCallsPerTick(250L, 100L));
    Assertions.assertEquals(0D, EventController.averageCallsPerTick(250L, 0L));
    Assertions.assertEquals(0D, EventController.averageCallsPerTick(0L, 100L));
  }

  @Test
  void queuedInstallCannotReinstallAfterStop() {
    HandlerFixture fixture = new HandlerFixture(plugin);
    try (SchedulerHarness scheduler = new SchedulerHarness(plugin, true, false)) {
      EventController controller = new EventController();
      controller.start();
      scheduler.runFirst();

      controller.markSamplerActivity();
      controller.onTick();
      Assertions.assertEquals(1, scheduler.size());

      controller.stop();
      scheduler.runAll();

      Assertions.assertFalse(fixture.registered() instanceof NaughtyRegisteredListener);
      Assertions.assertFalse(controller.isSpiesInjected());
      Assertions.assertFalse(controller.getRunning().get());
      Assertions.assertEquals(0, scheduler.size());
    } finally {
      fixture.close();
    }
  }

  @Test
  void delayedOldRuntimeCleanupCannotRemoveReloadedRuntimeInstrumentation() {
    HandlerFixture fixture = new HandlerFixture(plugin);
    try (SchedulerHarness scheduler = new SchedulerHarness(plugin, true, false)) {
      EventController oldController = new EventController();
      oldController.start();
      scheduler.runFirst();
      oldController.markSamplerActivity();
      oldController.onTick();
      scheduler.runFirst();
      Assertions.assertInstanceOf(NaughtyRegisteredListener.class, fixture.registered());

      oldController.stop();
      EventController newController = new EventController();
      newController.start();
      scheduler.runLast();
      newController.markSamplerActivity();
      newController.onTick();
      scheduler.runLast();
      NaughtyRegisteredListener current = Assertions.assertInstanceOf(
          NaughtyRegisteredListener.class,
          fixture.registered()
      );

      scheduler.runFirst();

      Assertions.assertSame(current, fixture.registered());
      newController.stop();
      scheduler.runAll();
      Assertions.assertFalse(fixture.registered() instanceof NaughtyRegisteredListener);
    } finally {
      fixture.close();
    }
  }

  @Test
  void paperMainThreadMutatesInlineWithoutScheduling() {
    HandlerFixture fixture = new HandlerFixture(plugin);
    try (SchedulerHarness scheduler = new SchedulerHarness(plugin, false, true)) {
      EventController controller = new EventController();
      controller.start();
      controller.markSamplerActivity();
      controller.onTick();

      Assertions.assertInstanceOf(NaughtyRegisteredListener.class, fixture.registered());
      Assertions.assertEquals(0, scheduler.size());

      controller.stop();
      Assertions.assertFalse(fixture.registered() instanceof NaughtyRegisteredListener);
    } finally {
      fixture.close();
    }
  }

  private static final class HandlerFixture implements AutoCloseable {
    private final HandlerList handlerList;
    private final Listener listener;

    private HandlerFixture(React plugin) {
      handlerList = new HandlerList();
      listener = Mockito.mock(Listener.class);
      EventExecutor executor = (ignored, event) -> {
      };
      handlerList.register(new RegisteredListener(
          listener,
          executor,
          EventPriority.NORMAL,
          plugin,
          false
      ));
    }

    private RegisteredListener registered() {
      RegisteredListener[] registered = handlerList.getRegisteredListeners();
      Assertions.assertEquals(1, registered.length);
      return registered[0];
    }

    @Override
    public void close() {
      HandlerList.unregisterAll(listener);
    }
  }

  private static final class SchedulerHarness implements AutoCloseable {
    private final Deque<Runnable> tasks;
    private final MockedStatic<J> scheduling;
    private final MockedStatic<Bukkit> bukkit;
    private final MockedStatic<FoliaScheduler> global;

    private SchedulerHarness(React plugin, boolean folia, boolean authoritativeThread) {
      tasks = new ArrayDeque<>();
      scheduling = Mockito.mockStatic(J.class);
      bukkit = Mockito.mockStatic(Bukkit.class);
      global = Mockito.mockStatic(FoliaScheduler.class);
      scheduling.when(J::isFoliaThreading).thenReturn(folia);
      if (folia) {
        bukkit.when(Bukkit::isGlobalTickThread).thenReturn(authoritativeThread);
      } else {
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(authoritativeThread);
      }
      global.when(() -> FoliaScheduler.runGlobal(
          Mockito.eq(plugin),
          Mockito.any(Runnable.class)
      )).thenAnswer(invocation -> {
        tasks.addLast(invocation.getArgument(1));
        return true;
      });
    }

    private int size() {
      return tasks.size();
    }

    private void runFirst() {
      Runnable task = tasks.pollFirst();
      Assertions.assertNotNull(task);
      task.run();
    }

    private void runLast() {
      Runnable task = tasks.pollLast();
      Assertions.assertNotNull(task);
      task.run();
    }

    private void runAll() {
      while (!tasks.isEmpty()) {
        runFirst();
      }
    }

    @Override
    public void close() {
      global.close();
      bukkit.close();
      scheduling.close();
    }
  }
}
