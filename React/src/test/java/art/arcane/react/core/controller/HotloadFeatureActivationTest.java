package art.arcane.react.core.controller;

import art.arcane.react.api.feature.Feature;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.Mockito;

class HotloadFeatureActivationTest {
  @ParameterizedTest
  @CsvSource({
      "false, false, 0, 0",
      "false, true, 0, 1",
      "true, false, 1, 0",
      "true, true, 1, 1"
  })
  void hotloadUsesCanonicalEligibilityForEveryActivationState(
      boolean wasActive,
      boolean eligible,
      int expectedDeactivations,
      int expectedActivations
  ) {
    FeatureController controller = Mockito.mock(FeatureController.class);
    Feature feature = Mockito.mock(Feature.class);
    Mockito.when(controller.shouldActivateFeature(feature)).thenReturn(eligible);

    HotloadController.reconcileFeatureActivation(controller, feature, wasActive);

    Mockito.verify(controller).shouldActivateFeature(feature);
    Mockito.verify(controller, Mockito.times(expectedDeactivations)).deactivateFeature(feature);
    Mockito.verify(controller, Mockito.times(expectedActivations)).activateFeature(feature);

    if (wasActive && eligible) {
      InOrder order = Mockito.inOrder(controller);
      order.verify(controller).shouldActivateFeature(feature);
      order.verify(controller).deactivateFeature(feature);
      order.verify(controller).activateFeature(feature);
    }
  }
}
