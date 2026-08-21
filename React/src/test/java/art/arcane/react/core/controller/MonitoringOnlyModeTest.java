package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.react.util.project.registry.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

class MonitoringOnlyModeTest {
  @Test
  void monitoringOnlyKeepsRenderersAndRestoresConfiguredFeaturesAndTweaks() {
    React previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Feature gameplayFeature = feature("gameplay-feature");
    Feature mapFeature = rendererFeature("map-feature");
    Tweak tweak = tweak("runtime-tweak");
    AtomicBoolean monitoringOnly = new AtomicBoolean(false);

    React.instance = plugin;
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    Mockito.when(plugin.isEnabled()).thenReturn(true);
    Mockito.when(plugin.isReady()).thenReturn(true);
    Mockito.when(plugin.isMonitoringOnly()).thenAnswer(invocation -> monitoringOnly.get());
    FeatureController featureController = featureController(gameplayFeature, mapFeature);
    TweakController tweakController = tweakController(tweak);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      featureController.activateFeature(gameplayFeature);
      featureController.activateFeature(mapFeature);
      tweakController.activateTweak(tweak);

      monitoringOnly.set(true);
      featureController.reconcileRuntimeMode();
      tweakController.reconcileRuntimeMode();

      Assertions.assertFalse(featureController.getActiveFeatures().containsKey(gameplayFeature.getId()));
      Assertions.assertTrue(featureController.getActiveFeatures().containsKey(mapFeature.getId()));
      Assertions.assertTrue(tweakController.getActiveTweaks().isEmpty());
      Assertions.assertTrue(gameplayFeature.isEnabled());
      Assertions.assertTrue(tweak.isEnabled());

      monitoringOnly.set(false);
      featureController.reconcileRuntimeMode();
      tweakController.reconcileRuntimeMode();

      Assertions.assertTrue(featureController.getActiveFeatures().containsKey(gameplayFeature.getId()));
      Assertions.assertTrue(featureController.getActiveFeatures().containsKey(mapFeature.getId()));
      Assertions.assertTrue(tweakController.getActiveTweaks().containsKey(tweak.getId()));
    } finally {
      React.instance = previous;
    }
  }

  @SuppressWarnings("unchecked")
  private FeatureController featureController(Feature gameplayFeature, Feature mapFeature) {
    FeatureController controller = new FeatureController();
    Registry<Feature> registry = Mockito.mock(Registry.class);
    Mockito.when(registry.all()).thenReturn(List.of(gameplayFeature, mapFeature));
    controller.setFeatures(registry);
    controller.setActiveFeatures(new ConcurrentHashMap<>());
    controller.setTickedFeatures(new HashMap<>());
    return controller;
  }

  @SuppressWarnings("unchecked")
  private TweakController tweakController(Tweak tweak) {
    TweakController controller = new TweakController();
    Registry<Tweak> registry = Mockito.mock(Registry.class);
    Mockito.when(registry.all()).thenReturn(List.of(tweak));
    controller.setTweaks(registry);
    controller.setActiveTweaks(new HashMap<>());
    controller.setTickedTweaks(new HashMap<>());
    return controller;
  }

  private Feature feature(String id) {
    Feature feature = Mockito.mock(Feature.class);
    Mockito.when(feature.getId()).thenReturn(id);
    Mockito.when(feature.getName()).thenReturn(id);
    Mockito.when(feature.isEnabled()).thenReturn(true);
    Mockito.when(feature.getTickInterval()).thenReturn(-1);
    return feature;
  }

  private Feature rendererFeature(String id) {
    Feature feature = Mockito.mock(
        Feature.class,
        Mockito.withSettings().extraInterfaces(ReactRenderer.class)
    );
    Mockito.when(feature.getId()).thenReturn(id);
    Mockito.when(feature.getName()).thenReturn(id);
    Mockito.when(feature.isEnabled()).thenReturn(true);
    Mockito.when(feature.getTickInterval()).thenReturn(-1);
    return feature;
  }

  private Tweak tweak(String id) {
    Tweak tweak = Mockito.mock(Tweak.class);
    Mockito.when(tweak.getId()).thenReturn(id);
    Mockito.when(tweak.getName()).thenReturn(id);
    Mockito.when(tweak.isEnabled()).thenReturn(true);
    Mockito.when(tweak.getTickInterval()).thenReturn(-1);
    return tweak;
  }
}
