package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.J;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

class FeatureIncidentModeLifecycleTest {
  @Test
  void staleEvaluationCannotCrossADeactivateReactivateBoundary() {
    FeatureIncidentMode feature = new FeatureIncidentMode();
    List<Runnable> queued = new ArrayList<>();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.s(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
        queued.add(invocation.getArgument(0));
        return null;
      });

      feature.onActivate();
      feature.onTick();
      Assertions.assertEquals(1, queued.size());

      feature.onDeactivate();
      feature.onActivate();
      queued.getFirst().run();

      react.verify(() -> React.sampler(Mockito.anyString()), Mockito.never());
      Assertions.assertFalse(feature.isIncidentActive());

      feature.onTick();
      Assertions.assertEquals(2, queued.size());
    }
  }
}
