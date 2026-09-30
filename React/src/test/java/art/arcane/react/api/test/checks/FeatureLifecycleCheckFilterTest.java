package art.arcane.react.api.test.checks;

import art.arcane.react.React;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.test.TestCheck;
import art.arcane.react.api.test.TestReport;
import art.arcane.react.api.test.TestStatus;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.registry.Registry;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class FeatureLifecycleCheckFilterTest {
  @Test
  @SuppressWarnings("unchecked")
  void featuresTheControllerRefusesToActivateAreNotCycled() {
    React previous = React.instance;
    React plugin = Mockito.mock(React.class);
    FeatureController controller = Mockito.mock(FeatureController.class);
    Registry<Feature> registry = Mockito.mock(Registry.class);
    Feature refused = feature("capability-absent");
    Feature accepted = feature("capability-present");
    Map<String, Feature> active = new HashMap<>();
    active.put(accepted.getId(), accepted);
    AtomicInteger completions = new AtomicInteger();

    Mockito.when(registry.all()).thenReturn(List.of(refused, accepted));
    Mockito.when(controller.getFeatures()).thenReturn(registry);
    Mockito.when(controller.getActiveFeatures()).thenReturn(active);
    Mockito.when(controller.shouldActivateFeature(refused)).thenReturn(false);
    Mockito.when(controller.shouldActivateFeature(accepted)).thenReturn(true);
    Mockito.doAnswer(invocation -> {
      Feature feature = invocation.getArgument(0);
      active.remove(feature.getId());
      return null;
    }).when(controller).deactivateFeature(Mockito.any(Feature.class));
    Mockito.doAnswer(invocation -> {
      Feature feature = invocation.getArgument(0);
      if (controller.shouldActivateFeature(feature)) {
        active.put(feature.getId(), feature);
      }
      return null;
    }).when(controller).activateFeature(Mockito.any(Feature.class));
    React.instance = plugin;

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class);
         MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      react.when(() -> React.controller(FeatureController.class)).thenReturn(controller);
      scheduling.when(() -> J.s(Mockito.any(Runnable.class), Mockito.anyInt())).thenAnswer(invocation -> {
        Runnable task = invocation.getArgument(0);
        task.run();
        return null;
      });
      bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
      TestReport report = new TestReport("Paper", "26.1", false, true, 0L);

      new FeatureLifecycleCheck().run(report, completions::incrementAndGet);

      Assertions.assertEquals(1, completions.get());
      Assertions.assertEquals(0, report.count(TestStatus.FAIL));
      Assertions.assertTrue(report.checks().stream().noneMatch(check -> refused.getId().equals(check.name())));
      Assertions.assertEquals(TestStatus.PASS, find(report, accepted.getId()).status());
      Mockito.verify(controller, Mockito.never()).deactivateFeature(refused);
      Mockito.verify(controller, Mockito.never()).activateFeature(refused);
      Assertions.assertTrue(active.containsKey(accepted.getId()));
    } finally {
      React.instance = previous;
    }
  }

  private static Feature feature(String id) {
    Feature feature = Mockito.mock(Feature.class);
    Mockito.when(feature.getId()).thenReturn(id);
    Mockito.when(feature.isEnabled()).thenReturn(true);
    return feature;
  }

  private static TestCheck find(TestReport report, String name) {
    for (TestCheck check : report.checks()) {
      if (name.equals(check.name())) {
        return check;
      }
    }
    throw new AssertionError("Missing test result for " + name);
  }
}
