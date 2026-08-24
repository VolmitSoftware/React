package art.arcane.react;

import art.arcane.multiburst.MultiBurst;
import art.arcane.react.core.bridge.NmsBridgeRegistry;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.react.util.plugin.IController;
import art.arcane.react.util.project.registry.Registry;
import art.arcane.volmlib.util.hud.HudActionBar;
import art.arcane.volmlib.util.hud.HudTitleService;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.event.server.PluginDisableEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class ReactShutdownLifecycleTest {
  @Test
  void pluginDisableEventDrainsOnceBeforeFrameworkSchedulerRetirement() throws ReflectiveOperationException {
    try (ShutdownHarness harness = new ShutdownHarness()) {
      harness.firePluginDisableEvent();
      harness.firePluginDisableEvent();

      Assertions.assertEquals(1, harness.controllerStops());
      Assertions.assertFalse(harness.schedulerCancelled());
      Mockito.verify(harness.ticker()).close();
      Mockito.verify(harness.bridgeRegistry()).clear();

      harness.plugin().onDisable();

      Assertions.assertEquals(1, harness.controllerStops());
      Assertions.assertTrue(harness.schedulerCancelled());
      Mockito.verify(harness.ticker()).close();
      Mockito.verify(harness.bridgeRegistry()).clear();
    }
  }

  @Test
  void frameworkDisableBeforeEventStillBeginsCleanupExactlyOnce() throws ReflectiveOperationException {
    try (ShutdownHarness harness = new ShutdownHarness()) {
      harness.plugin().onDisable();
      harness.firePluginDisableEvent();

      Assertions.assertEquals(1, harness.controllerStops());
      Assertions.assertTrue(harness.schedulerCancelled());
      Mockito.verify(harness.ticker()).close();
      Mockito.verify(harness.bridgeRegistry()).clear();
    }
  }

  private static Object readField(Object target, String name) throws ReflectiveOperationException {
    Field field = React.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
    Field field = React.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Object readStaticField(String name) throws ReflectiveOperationException {
    Field field = React.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(null);
  }

  private static void setStaticField(String name, Object value) throws ReflectiveOperationException {
    Field field = React.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(null, value);
  }

  private static final class ShutdownHarness implements AutoCloseable {
    private final React previousInstance;
    private final Ticker previousTicker;
    private final MultiBurst previousBurst;
    private final HudActionBar previousHudBar;
    private final HudTitleService previousHudTitles;
    private final BukkitAudiences previousAudienceProvider;
    private final React plugin;
    private final Ticker ticker;
    private final NmsBridgeRegistry bridgeRegistry;
    private final AtomicBoolean schedulerCancelled;
    private final AtomicInteger controllerStops;
    private final MockedStatic<J> scheduler;

    @SuppressWarnings("unchecked")
    private ShutdownHarness() throws ReflectiveOperationException {
      previousInstance = React.instance;
      previousTicker = React.ticker;
      previousBurst = React.burst;
      previousHudBar = (HudActionBar) readStaticField("hudBar");
      previousHudTitles = (HudTitleService) readStaticField("hudTitles");
      previousAudienceProvider = (BukkitAudiences) readStaticField("audienceProvider");

      plugin = Mockito.mock(React.class, Mockito.CALLS_REAL_METHODS);
      ticker = Mockito.mock(Ticker.class);
      bridgeRegistry = Mockito.mock(NmsBridgeRegistry.class);
      schedulerCancelled = new AtomicBoolean(false);
      controllerStops = new AtomicInteger(0);
      scheduler = Mockito.mockStatic(J.class);
      scheduler.when(J::cancelPluginTasks).thenAnswer(invocation -> {
        schedulerCancelled.set(true);
        return null;
      });

      Registry<IController> controllers = Mockito.mock(Registry.class);
      IController cleanupController = Mockito.mock(IController.class);
      Mockito.doAnswer(invocation -> {
        Assertions.assertFalse(schedulerCancelled.get());
        Assertions.assertSame(bridgeRegistry, readField(plugin, "bridgeRegistry"));
        Mockito.verify(bridgeRegistry, Mockito.never()).clear();
        controllerStops.incrementAndGet();
        return null;
      }).when(cleanupController).stop();
      Mockito.when(controllers.all()).thenReturn(List.of(cleanupController));
      Mockito.when(ticker.close()).thenReturn(true);
      Mockito.doNothing().when(plugin).unregisterListener(Mockito.any());
      Mockito.doNothing().when(plugin).unregisterAll();

      setField(plugin, "alreadyDrained", new AtomicBoolean(false));
      setField(plugin, "controllerRegistry", controllers);
      setField(plugin, "bridgeRegistry", bridgeRegistry);
      React.instance = plugin;
      React.ticker = ticker;
      React.burst = null;
      setStaticField("hudBar", null);
      setStaticField("hudTitles", null);
      setStaticField("audienceProvider", null);
    }

    private React plugin() {
      return plugin;
    }

    private Ticker ticker() {
      return ticker;
    }

    private NmsBridgeRegistry bridgeRegistry() {
      return bridgeRegistry;
    }

    private int controllerStops() {
      return controllerStops.get();
    }

    private boolean schedulerCancelled() {
      return schedulerCancelled.get();
    }

    private void firePluginDisableEvent() {
      PluginDisableEvent event = Mockito.mock(PluginDisableEvent.class);
      Mockito.when(event.getPlugin()).thenReturn(plugin);
      plugin.onPluginDisable(event);
    }

    @Override
    public void close() throws ReflectiveOperationException {
      scheduler.close();
      React.instance = previousInstance;
      React.ticker = previousTicker;
      React.burst = previousBurst;
      setStaticField("hudBar", previousHudBar);
      setStaticField("hudTitles", previousHudTitles);
      setStaticField("audienceProvider", previousAudienceProvider);
    }
  }
}
