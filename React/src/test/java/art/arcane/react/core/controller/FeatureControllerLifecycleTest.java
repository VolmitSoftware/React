package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.feature.ReactTickedFeature;
import art.arcane.react.core.integration.IntegrationCapabilitySupport;
import art.arcane.react.model.ReactConfiguration;
import art.arcane.react.util.common.scheduling.J;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

class FeatureControllerLifecycleTest {
  private React previous;
  private React plugin;
  private Ticker ticker;
  private FeatureController controller;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    plugin = Mockito.mock(React.class);
    ticker = Mockito.mock(Ticker.class);
    React.instance = plugin;
    Mockito.when(plugin.getTicker()).thenReturn(ticker);
    Mockito.when(plugin.isEnabled()).thenReturn(true);
    Mockito.when(plugin.isMonitoringOnly()).thenReturn(false);

    controller = new FeatureController();
    controller.setActiveFeatures(new ConcurrentHashMap<>());
    controller.setTickedFeatures(new HashMap<>());
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void activateFeatureEnforcesEveryCanonicalGate() {
    Feature disabled = feature("disabled", false, -1);
    Feature runtimeBlocked = feature("runtime-blocked", true, -1);
    Feature secretBlocked = gatedFeature("secret-blocked", true, Set.of());
    CapabilityGatedFeature secretGate = (CapabilityGatedFeature) secretBlocked;
    Mockito.when(secretGate.isSecretBundle()).thenReturn(true);
    Feature capabilityBlocked = gatedFeature("capability-blocked", false, Set.of("adapt"));
    ReactConfiguration configuration = Mockito.mock(ReactConfiguration.class);
    Mockito.when(configuration.isIntegrationSecretsEnabled()).thenReturn(false);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<ReactConfiguration> configurations = Mockito.mockStatic(ReactConfiguration.class);
         MockedStatic<IntegrationCapabilitySupport> capabilities = Mockito.mockStatic(IntegrationCapabilitySupport.class)) {
      configurations.when(ReactConfiguration::get).thenReturn(configuration);
      capabilities.when(() -> IntegrationCapabilitySupport.hasCapability(null, "adapt")).thenReturn(false);

      controller.activateFeature(disabled);
      Mockito.when(plugin.isMonitoringOnly()).thenReturn(true);
      controller.activateFeature(runtimeBlocked);
      Mockito.when(plugin.isMonitoringOnly()).thenReturn(false);
      controller.activateFeature(secretBlocked);
      controller.activateFeature(capabilityBlocked);

      Assertions.assertTrue(controller.getActiveFeatures().isEmpty());
      Mockito.verify(disabled, Mockito.never()).onActivate();
      Mockito.verify(runtimeBlocked, Mockito.never()).onActivate();
      Mockito.verify(secretBlocked, Mockito.never()).onActivate();
      Mockito.verify(capabilityBlocked, Mockito.never()).onActivate();
    }
  }

  @Test
  void activationRollsBackEveryStartedResourceWhenCommitFails() {
    Feature feature = listenerFeature("transactional", true, 50);
    Listener listener = (Listener) feature;
    RuntimeException failure = new IllegalStateException("commit failed");
    Map<String, Feature> active = new FailingFeatureMap(failure);
    controller.setActiveFeatures(active);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      Assertions.assertDoesNotThrow(() -> controller.activateFeature(feature));

      Assertions.assertTrue(active.isEmpty());
      Assertions.assertTrue(controller.getTickedFeatures().isEmpty());
      Mockito.verify(plugin).registerListener(listener);
      Mockito.verify(plugin).unregisterListener(listener);
      Mockito.verify(ticker).unregister(Mockito.any(ReactTickedFeature.class));
      Mockito.verify(feature).onDeactivate();
      react.verify(() -> React.reportError("Failed to activate feature transactional.", failure));
    }
  }

  @Test
  void postStartContinuesAfterFeatureActivationFailure() {
    Feature broken = feature("broken", true, -1);
    Feature healthy = feature("healthy", true, -1);
    RuntimeException failure = new IllegalStateException("activation failed");
    Mockito.doThrow(failure).when(broken).onActivate();
    controller.setFeatures(registry(Map.of("broken", broken, "healthy", healthy)));

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      Assertions.assertDoesNotThrow(controller::postStart);

      Assertions.assertEquals(Set.of("healthy"), controller.getActiveFeatures().keySet());
      Mockito.verify(broken).onDeactivate();
      Mockito.verify(healthy).onActivate();
      react.verify(() -> React.reportError("Failed to activate feature broken.", failure));
    }
  }

  @Test
  void stopRunsEveryCleanupStepAndContinuesAfterFailures() {
    Feature broken = listenerFeature("broken", true, -1);
    Feature healthy = feature("healthy", true, -1);
    Listener listener = (Listener) broken;
    ReactTickedFeature scheduled = Mockito.mock(ReactTickedFeature.class);
    RuntimeException schedulerFailure = new IllegalStateException("scheduler cleanup failed");
    RuntimeException lifecycleFailure = new IllegalStateException("deactivation failed");
    Mockito.doThrow(schedulerFailure).when(scheduled).unregister();
    Mockito.doThrow(lifecycleFailure).when(broken).onDeactivate();
    Map<String, Feature> active = new LinkedHashMap<>();
    active.put(broken.getId(), broken);
    active.put(healthy.getId(), healthy);
    controller.setActiveFeatures(active);
    controller.getTickedFeatures().put(broken.getId(), scheduled);
    controller.setFeatures(registry(Map.of()));

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      IllegalStateException stopFailure = Assertions.assertThrows(
          IllegalStateException.class,
          controller::stop
      );

      Assertions.assertEquals("One or more features failed to stop cleanly", stopFailure.getMessage());
      Assertions.assertTrue(controller.getActiveFeatures().isEmpty());
      Assertions.assertTrue(controller.getTickedFeatures().isEmpty());
      Mockito.verify(plugin).unregisterListener(listener);
      Mockito.verify(broken).onDeactivate();
      Mockito.verify(healthy).onDeactivate();
      react.verify(() -> React.reportError("Failed to deactivate feature broken.", schedulerFailure));
      Assertions.assertEquals(List.of(lifecycleFailure), List.of(schedulerFailure.getSuppressed()));
    }
  }

  @Test
  void queuedGateReconcileCannotReactivateAFeatureAfterStop() {
    Feature feature = feature("queued", true, -1);
    AtomicReference<Runnable> queued = new AtomicReference<>();
    controller.setFeatures(registry(Map.of(feature.getId(), feature)));
    Mockito.when(plugin.isReady()).thenReturn(true);

    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        queued.set(invocation.getArgument(0));
        return null;
      });

      controller.onTick();
      Assertions.assertNotNull(queued.get());
      controller.stop();
      queued.get().run();

      Assertions.assertTrue(controller.getActiveFeatures().isEmpty());
      Mockito.verify(feature, Mockito.never()).onActivate();
    }
  }

  private Feature feature(String id, boolean enabled, int tickInterval) {
    Feature feature = Mockito.mock(Feature.class);
    stubFeature(feature, id, enabled, tickInterval);
    return feature;
  }

  private Feature listenerFeature(String id, boolean enabled, int tickInterval) {
    Feature feature = Mockito.mock(
        Feature.class,
        Mockito.withSettings().extraInterfaces(Listener.class)
    );
    stubFeature(feature, id, enabled, tickInterval);
    return feature;
  }

  private Feature gatedFeature(String id, boolean secret, Set<String> capabilities) {
    Feature feature = Mockito.mock(
        Feature.class,
        Mockito.withSettings().extraInterfaces(CapabilityGatedFeature.class)
    );
    stubFeature(feature, id, true, -1);
    CapabilityGatedFeature gated = (CapabilityGatedFeature) feature;
    Mockito.when(gated.isSecretBundle()).thenReturn(secret);
    Mockito.when(gated.requiredCapabilities()).thenReturn(capabilities);
    return feature;
  }

  private void stubFeature(Feature feature, String id, boolean enabled, int tickInterval) {
    Mockito.when(feature.getId()).thenReturn(id);
    Mockito.when(feature.isEnabled()).thenReturn(enabled);
    Mockito.when(feature.getTickInterval()).thenReturn(tickInterval);
  }

  @SuppressWarnings("unchecked")
  private Registry<Feature> registry(Map<String, Feature> features) {
    Registry<Feature> registry = Mockito.mock(Registry.class);
    Set<String> ids = new LinkedHashSet<>(features.keySet());
    Mockito.when(registry.ids()).thenReturn(ids);
    Mockito.when(registry.size()).thenReturn(features.size());
    Mockito.when(registry.all()).thenReturn(features.values());
    for (Map.Entry<String, Feature> entry : features.entrySet()) {
      Mockito.when(registry.get(entry.getKey())).thenReturn(entry.getValue());
    }
    return registry;
  }

  private static final class FailingFeatureMap extends HashMap<String, Feature> {
    private final RuntimeException failure;

    private FailingFeatureMap(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public Feature put(String key, Feature value) {
      throw failure;
    }
  }
}
