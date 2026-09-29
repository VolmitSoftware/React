package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.integration.RemoteSamplerBridge;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class SamplerGlossTickMsTest {

  @Test
  void suffixNamesTheMillisecondsPerSecondUnit() {
    IntegrationController controller = Mockito.mock(IntegrationController.class);
    RemoteSamplerBridge bridge = Mockito.mock(RemoteSamplerBridge.class);
    Mockito.when(controller.getRemoteSamplerBridge()).thenReturn(bridge);
    Mockito.when(bridge.isAvailable("gloss", IntegrationMetricSchema.GLOSS_TICK_MS)).thenReturn(true);
    SamplerGlossTickMs sampler = new SamplerGlossTickMs();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.controller(IntegrationController.class)).thenReturn(controller);

      Assertions.assertEquals(" ms/s", sampler.formattedSuffix(3.2D));
    }
  }
}
