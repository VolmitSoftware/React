package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.tweak.ReactTickedTweak;
import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.react.util.project.registry.Registry;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class TweakControllerLifecycleTest {
  private React previous;
  private React plugin;
  private Ticker ticker;
  private TweakController controller;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    plugin = Mockito.mock(React.class);
    ticker = Mockito.mock(Ticker.class);
    React.instance = plugin;
    Mockito.when(plugin.getTicker()).thenReturn(ticker);
    Mockito.when(plugin.isEnabled()).thenReturn(true);
    Mockito.when(plugin.isMonitoringOnly()).thenReturn(false);

    controller = new TweakController();
    controller.setActiveTweaks(new HashMap<>());
    controller.setTickedTweaks(new HashMap<>());
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void activateTweakEnforcesEnabledPluginAndRuntimeGates() {
    Tweak disabled = tweak("disabled", false, -1);
    Tweak pluginBlocked = tweak("plugin-blocked", true, -1);
    Tweak runtimeBlocked = tweak("runtime-blocked", true, -1);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      controller.activateTweak(disabled);
      Mockito.when(plugin.isEnabled()).thenReturn(false);
      controller.activateTweak(pluginBlocked);
      Mockito.when(plugin.isEnabled()).thenReturn(true);
      Mockito.when(plugin.isMonitoringOnly()).thenReturn(true);
      controller.activateTweak(runtimeBlocked);

      Assertions.assertTrue(controller.getActiveTweaks().isEmpty());
      Mockito.verify(disabled, Mockito.never()).onActivate();
      Mockito.verify(pluginBlocked, Mockito.never()).onActivate();
      Mockito.verify(runtimeBlocked, Mockito.never()).onActivate();
    }
  }

  @Test
  void activationRollsBackEveryStartedResourceWhenCommitFails() {
    Tweak tweak = listenerTweak("transactional", true, 50);
    Listener listener = (Listener) tweak;
    RuntimeException failure = new IllegalStateException("commit failed");
    Map<String, Tweak> active = new FailingTweakMap(failure);
    controller.setActiveTweaks(active);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      Assertions.assertDoesNotThrow(() -> controller.activateTweak(tweak));

      Assertions.assertTrue(active.isEmpty());
      Assertions.assertTrue(controller.getTickedTweaks().isEmpty());
      Mockito.verify(plugin).registerListener(listener);
      Mockito.verify(plugin).unregisterListener(listener);
      Mockito.verify(ticker).unregister(Mockito.any(ReactTickedTweak.class));
      Mockito.verify(tweak).onDeactivate();
      react.verify(() -> React.reportError("Failed to activate tweak transactional.", failure));
    }
  }

  @Test
  void postStartContinuesAfterTweakActivationFailure() {
    Tweak broken = tweak("broken", true, -1);
    Tweak healthy = tweak("healthy", true, -1);
    RuntimeException failure = new IllegalStateException("activation failed");
    Mockito.doThrow(failure).when(broken).onActivate();
    controller.setTweaks(registry(Map.of("broken", broken, "healthy", healthy)));

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      Assertions.assertDoesNotThrow(controller::postStart);

      Assertions.assertEquals(Set.of("healthy"), controller.getActiveTweaks().keySet());
      Mockito.verify(broken).onDeactivate();
      Mockito.verify(healthy).onActivate();
      react.verify(() -> React.reportError("Failed to activate tweak broken.", failure));
    }
  }

  @Test
  void stopRunsEveryCleanupStepAndContinuesAfterFailures() {
    Tweak broken = listenerTweak("broken", true, -1);
    Tweak healthy = tweak("healthy", true, -1);
    Listener listener = (Listener) broken;
    ReactTickedTweak scheduled = Mockito.mock(ReactTickedTweak.class);
    RuntimeException schedulerFailure = new IllegalStateException("scheduler cleanup failed");
    RuntimeException lifecycleFailure = new IllegalStateException("deactivation failed");
    Mockito.doThrow(schedulerFailure).when(scheduled).unregister();
    Mockito.doThrow(lifecycleFailure).when(broken).onDeactivate();
    Map<String, Tweak> active = new LinkedHashMap<>();
    active.put(broken.getId(), broken);
    active.put(healthy.getId(), healthy);
    controller.setActiveTweaks(active);
    controller.getTickedTweaks().put(broken.getId(), scheduled);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      IllegalStateException stopFailure = Assertions.assertThrows(
          IllegalStateException.class,
          controller::stop
      );

      Assertions.assertEquals("One or more tweaks failed to stop cleanly", stopFailure.getMessage());
      Assertions.assertTrue(controller.getActiveTweaks().isEmpty());
      Assertions.assertTrue(controller.getTickedTweaks().isEmpty());
      Mockito.verify(plugin).unregisterListener(listener);
      Mockito.verify(broken).onDeactivate();
      Mockito.verify(healthy).onDeactivate();
      react.verify(() -> React.reportError("Failed to deactivate tweak broken.", schedulerFailure));
      Assertions.assertEquals(List.of(lifecycleFailure), List.of(schedulerFailure.getSuppressed()));
    }
  }

  private Tweak tweak(String id, boolean enabled, int tickInterval) {
    Tweak tweak = Mockito.mock(Tweak.class);
    stubTweak(tweak, id, enabled, tickInterval);
    return tweak;
  }

  private Tweak listenerTweak(String id, boolean enabled, int tickInterval) {
    Tweak tweak = Mockito.mock(
        Tweak.class,
        Mockito.withSettings().extraInterfaces(Listener.class)
    );
    stubTweak(tweak, id, enabled, tickInterval);
    return tweak;
  }

  private void stubTweak(Tweak tweak, String id, boolean enabled, int tickInterval) {
    Mockito.when(tweak.getId()).thenReturn(id);
    Mockito.when(tweak.isEnabled()).thenReturn(enabled);
    Mockito.when(tweak.getTickInterval()).thenReturn(tickInterval);
  }

  @SuppressWarnings("unchecked")
  private Registry<Tweak> registry(Map<String, Tweak> tweaks) {
    Registry<Tweak> registry = Mockito.mock(Registry.class);
    Set<String> ids = new LinkedHashSet<>(tweaks.keySet());
    Mockito.when(registry.ids()).thenReturn(ids);
    Mockito.when(registry.size()).thenReturn(tweaks.size());
    Mockito.when(registry.all()).thenReturn(tweaks.values());
    for (Map.Entry<String, Tweak> entry : tweaks.entrySet()) {
      Mockito.when(registry.get(entry.getKey())).thenReturn(entry.getValue());
    }
    return registry;
  }

  private static final class FailingTweakMap extends HashMap<String, Tweak> {
    private final RuntimeException failure;

    private FailingTweakMap(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public Tweak put(String key, Tweak value) {
      throw failure;
    }
  }
}
