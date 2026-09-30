package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.event.NaughtyRegisteredListener;
import art.arcane.react.api.event.layer.ServerTickEvent;
import art.arcane.react.testutil.IsolatedClassLoader;
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

class EventControllerModeTest {
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
  void defaultModeIsOnDemandAndInstallsNothingWithoutDemand() {
    HandlerFixture fixture = new HandlerFixture(plugin);
    try (SchedulerHarness ignored = new SchedulerHarness(plugin, false, true)) {
      EventController controller = new EventController();
      Assertions.assertEquals(EventController.InstrumentationMode.ON_DEMAND, controller.getInstrumentation());
      controller.start();

      Assertions.assertSame(fixture.original(), fixture.registered());
      controller.stop();
    }
  }

  @Test
  void missingModeFallsBackToOnDemand() {
    HandlerFixture fixture = new HandlerFixture(plugin);
    try (SchedulerHarness ignored = new SchedulerHarness(plugin, false, true)) {
      EventController controller = new EventController();
      controller.setInstrumentation(null);
      controller.start();

      Assertions.assertSame(fixture.original(), fixture.registered());
      controller.stop();
    }
  }

  @Test
  void alwaysModeInstallsWithoutDemandAndStaysInstalled() {
    HandlerFixture fixture = new HandlerFixture(plugin);
    try (SchedulerHarness ignored = new SchedulerHarness(plugin, false, true)) {
      EventController controller = new EventController();
      controller.setInstrumentation(EventController.InstrumentationMode.ALWAYS);
      controller.start();

      NaughtyRegisteredListener installed = Assertions.assertInstanceOf(
          NaughtyRegisteredListener.class,
          fixture.registered()
      );
      Assertions.assertSame(fixture.original(), installed.delegate());

      controller.setLastSamplerActivity(0L);
      controller.onTick();
      Assertions.assertSame(installed, fixture.registered());
      Assertions.assertTrue(controller.isSpiesInjected());

      controller.stop();
      Assertions.assertSame(fixture.original(), fixture.registered());
    } finally {
      fixture.close();
    }
  }

  @Test
  void onDemandInstallsOnDemandAndRestoresTheOriginalAfterTheWindow() {
    HandlerFixture fixture = new HandlerFixture(plugin);
    try (SchedulerHarness ignored = new SchedulerHarness(plugin, false, true)) {
      EventController controller = new EventController();
      controller.setInstrumentation(EventController.InstrumentationMode.ON_DEMAND);
      controller.setSamplerActivityWindowMS(15_000L);
      controller.start();
      controller.onTick();
      Assertions.assertSame(fixture.original(), fixture.registered());
      Assertions.assertFalse(controller.isMeasuring());

      controller.markSamplerActivity();
      NaughtyRegisteredListener installed = Assertions.assertInstanceOf(
          NaughtyRegisteredListener.class,
          fixture.registered()
      );
      Assertions.assertSame(fixture.original(), installed.delegate());

      controller.setLastSamplerActivity(System.currentTimeMillis() - 15_001L);
      controller.onTick();
      Assertions.assertSame(fixture.original(), fixture.registered());
      Assertions.assertFalse(controller.isSpiesInjected());
    } finally {
      fixture.close();
    }
  }

  @Test
  void dutyCycleOpensPeriodicWindowsAndPublishesTheFinalDrain() throws EventException {
    HandlerFixture fixture = new HandlerFixture(plugin);
    try (SchedulerHarness ignored = new SchedulerHarness(plugin, false, true)) {
      EventController controller = new EventController();
      controller.setInstrumentation(EventController.InstrumentationMode.DUTY_CYCLE);
      controller.setDutyCycleOnMS(5_000L);
      controller.setDutyCyclePeriodMS(60_000L);
      controller.start();
      Assertions.assertSame(fixture.original(), fixture.registered());

      controller.onTick();
      NaughtyRegisteredListener installed = Assertions.assertInstanceOf(
          NaughtyRegisteredListener.class,
          fixture.registered()
      );
      for (int call = 0; call < 3; call++) {
        installed.callEvent(Mockito.mock(Event.class));
        controller.on(new ServerTickEvent());
      }

      controller.setDutyWindowStartMs(System.currentTimeMillis() - 5_000L);
      controller.onTick();
      Assertions.assertSame(fixture.original(), fixture.registered());
      Assertions.assertTrue(controller.isMeasuring());
      Assertions.assertEquals(3, controller.getCalls());
      Assertions.assertEquals(1D, controller.getCallsPerTick());
      Assertions.assertEquals(3, controller.snapshotPluginEventCalls().get("MeasuredPlugin"));
      Assertions.assertTrue(controller.getWindowSeconds() > 0D);

      controller.onTick();
      Assertions.assertSame(fixture.original(), fixture.registered());

      controller.setDutyWindowStartMs(System.currentTimeMillis() - 60_000L);
      controller.onTick();
      Assertions.assertInstanceOf(NaughtyRegisteredListener.class, fixture.registered());
    } finally {
      fixture.close();
    }
  }

  @Test
  void stopUninstallsOnTheCallingThreadEvenWhileAReconciliationIsQueued() {
    HandlerFixture fixture = new HandlerFixture(plugin);
    try (SchedulerHarness scheduler = new SchedulerHarness(plugin, true, false)) {
      EventController controller = new EventController();
      controller.setInstrumentation(EventController.InstrumentationMode.ON_DEMAND);
      controller.start();
      scheduler.runFirst();
      controller.markSamplerActivity();
      scheduler.runFirst();
      Assertions.assertInstanceOf(NaughtyRegisteredListener.class, fixture.registered());

      controller.markSamplerActivity();
      controller.onTick();
      Assertions.assertEquals(1, scheduler.size());

      controller.stop();
      Assertions.assertSame(fixture.original(), fixture.registered());
      Assertions.assertFalse(controller.isSpiesInjected());
      Assertions.assertEquals(1, scheduler.size());

      scheduler.runFirst();
      Assertions.assertSame(fixture.original(), fixture.registered());
      Assertions.assertFalse(controller.isSpiesInjected());
    } finally {
      fixture.close();
    }
  }

  @Test
  void installedWrapperExposesTheOriginalExecutorWithoutPaperOnlyApi() {
    HandlerFixture fixture = new HandlerFixture(plugin);
    try (SchedulerHarness ignored = new SchedulerHarness(plugin, false, true)) {
      EventController controller = new EventController();
      controller.setInstrumentation(EventController.InstrumentationMode.ALWAYS);
      controller.start();

      NaughtyRegisteredListener installed = Assertions.assertInstanceOf(
          NaughtyRegisteredListener.class,
          fixture.registered()
      );
      Assertions.assertSame(fixture.executor(), installed.getExecutor());
      Assertions.assertSame(fixture.original(), installed.delegate());

      controller.stop();
      Assertions.assertSame(fixture.original(), fixture.registered());
    } finally {
      fixture.close();
    }
  }

  @Test
  void wrappersFromAnotherClassLoaderAreUnwrappedInsteadOfNested() throws Exception {
    HandlerFixture fixture = new HandlerFixture(plugin);
    try (SchedulerHarness ignored = new SchedulerHarness(plugin, false, true)) {
      RegisteredListener foreign = fixture.wrapWithAnotherRuntime();
      Assertions.assertFalse(foreign instanceof NaughtyRegisteredListener);
      Assertions.assertSame(foreign, fixture.registered());

      EventController controller = new EventController();
      controller.setInstrumentation(EventController.InstrumentationMode.ALWAYS);
      controller.start();
      NaughtyRegisteredListener installed = Assertions.assertInstanceOf(
          NaughtyRegisteredListener.class,
          fixture.registered()
      );
      Assertions.assertSame(fixture.original(), installed.delegate());

      controller.stop();
      Assertions.assertSame(fixture.original(), fixture.registered());
    } finally {
      fixture.close();
    }
  }

  @Test
  void idleOnDemandTickDoesNotScheduleAHandlerListWalk() {
    HandlerFixture fixture = new HandlerFixture(plugin);
    try (SchedulerHarness scheduler = new SchedulerHarness(plugin, true, false)) {
      EventController controller = new EventController();
      controller.setInstrumentation(EventController.InstrumentationMode.ON_DEMAND);
      controller.start();
      scheduler.runFirst();

      controller.onTick();
      controller.onTick();

      Assertions.assertEquals(0, scheduler.size());
      Assertions.assertSame(fixture.original(), fixture.registered());
    } finally {
      fixture.close();
    }
  }

  @Test
  void foreignOwnerWrappersAreUnwrappedInsteadOfNested() {
    HandlerFixture fixture = new HandlerFixture(plugin);
    try (SchedulerHarness ignored = new SchedulerHarness(plugin, false, true)) {
      EventController old = new EventController();
      old.setInstrumentation(EventController.InstrumentationMode.ALWAYS);
      old.start();
      NaughtyRegisteredListener foreign = Assertions.assertInstanceOf(
          NaughtyRegisteredListener.class,
          fixture.registered()
      );

      EventController current = new EventController();
      current.setInstrumentation(EventController.InstrumentationMode.ALWAYS);
      current.start();
      NaughtyRegisteredListener replaced = Assertions.assertInstanceOf(
          NaughtyRegisteredListener.class,
          fixture.registered()
      );

      Assertions.assertNotSame(foreign, replaced);
      Assertions.assertSame(fixture.original(), replaced.delegate());
      current.stop();
      Assertions.assertSame(fixture.original(), fixture.registered());
    } finally {
      fixture.close();
    }
  }

  private static final class HandlerFixture implements AutoCloseable {
    private final HandlerList handlerList;
    private final Listener listener;
    private final EventExecutor executor;
    private final RegisteredListener original;

    private HandlerFixture(React plugin) {
      handlerList = new HandlerList();
      listener = Mockito.mock(Listener.class);
      executor = (ignored, event) -> {
      };
      original = new RegisteredListener(listener, executor, EventPriority.NORMAL, plugin, false) {
        @Override
        public EventExecutor getExecutor() {
          throw new UnsupportedOperationException("RegisteredListener.getExecutor is Paper-only and must not be used");
        }
      };
      handlerList.register(original);
    }

    private RegisteredListener original() {
      return original;
    }

    private EventExecutor executor() {
      return executor;
    }

    private RegisteredListener wrapWithAnotherRuntime() throws Exception {
      Class<?> foreignType = new IsolatedClassLoader(NaughtyRegisteredListener.class).isolatedClass();
      RegisteredListener foreign = (RegisteredListener) foreignType
          .getConstructor(RegisteredListener.class, EventExecutor.class, long.class)
          .newInstance(original, executor, 99L);
      handlerList.unregister(original);
      handlerList.register(foreign);
      return foreign;
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

    @Override
    public void close() {
      global.close();
      bukkit.close();
      scheduling.close();
    }
  }
}
