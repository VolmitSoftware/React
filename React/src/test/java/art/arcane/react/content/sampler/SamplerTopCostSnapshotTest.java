package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.CostSnapshot;
import art.arcane.react.util.common.scheduling.J;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class SamplerTopCostSnapshotTest {
  @Test
  void topSamplersReadThePublishedSnapshotWithoutAMainThreadHop() {
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.costSnapshot()).thenReturn(new CostSnapshot(100D, 25D, 60D, null));
    Sampler tickTime = Mockito.mock(Sampler.class);
    Mockito.when(tickTime.sample()).thenReturn(40D);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(tickTime);

      Assertions.assertEquals(10D, new SamplerTopChunkCost().onSample(), 1.0E-9D);
      Assertions.assertEquals(24D, new SamplerTopWorldMSPT().onSample(), 1.0E-9D);
      scheduling.verify(() -> J.s(Mockito.any(Runnable.class)), Mockito.never());
    }
  }

  @Test
  void topSamplersReadZeroWhenNothingIsCharged() {
    ObserverController observer = Mockito.mock(ObserverController.class);
    Mockito.when(observer.costSnapshot()).thenReturn(CostSnapshot.EMPTY);
    Sampler tickTime = Mockito.mock(Sampler.class);
    Mockito.when(tickTime.sample()).thenReturn(40D);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> ignored = Mockito.mockStatic(J.class)) {
      react.when(() -> React.controller(ObserverController.class)).thenReturn(observer);
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(tickTime);

      Assertions.assertEquals(0D, new SamplerTopChunkCost().onSample());
      Assertions.assertEquals(0D, new SamplerTopWorldMSPT().onSample());
    }
  }

  @Test
  void onlyTheEntitiesSamplerIsAChunkGauge() {
    Assertions.assertTrue(new SamplerEntities().isChunkGauge());
    Assertions.assertFalse(new SamplerPhysicsUpdates().isChunkGauge());
  }
}
