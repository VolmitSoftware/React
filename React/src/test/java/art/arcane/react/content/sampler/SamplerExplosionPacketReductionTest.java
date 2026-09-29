package art.arcane.react.content.sampler;

import art.arcane.react.React;
import art.arcane.react.content.feature.FeatureExplosionPacketBatching;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class SamplerExplosionPacketReductionTest {

  @Test
  void idleWindowsDoNotDiluteTheReduction() {
    FeatureExplosionPacketBatching feature = Mockito.mock(FeatureExplosionPacketBatching.class);
    Mockito.when(feature.readAndResetExplosions()).thenReturn(100L, 0L, 0L, 0L, 0L);
    Mockito.when(feature.readAndResetClusters()).thenReturn(10L, 0L, 0L, 0L, 0L);
    SamplerExplosionPacketReduction sampler = new SamplerExplosionPacketReduction();
    double reduction = 0D;

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.feature(FeatureExplosionPacketBatching.class)).thenReturn(feature);

      for (int i = 0; i < 5; i++) {
        reduction = sampler.onSample();
      }
    }

    Assertions.assertEquals(0.9D, reduction, 1.0E-9D);
    Assertions.assertTrue(sampler.isSampleAvailable());
  }

  @Test
  void reductionIsTheRatioOfSummedClustersToSummedExplosions() {
    FeatureExplosionPacketBatching feature = Mockito.mock(FeatureExplosionPacketBatching.class);
    Mockito.when(feature.readAndResetExplosions()).thenReturn(10L, 90L);
    Mockito.when(feature.readAndResetClusters()).thenReturn(9L, 1L);
    SamplerExplosionPacketReduction sampler = new SamplerExplosionPacketReduction();
    double reduction = 0D;

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.feature(FeatureExplosionPacketBatching.class)).thenReturn(feature);

      for (int i = 0; i < 2; i++) {
        reduction = sampler.onSample();
      }
    }

    Assertions.assertEquals(0.9D, reduction, 1.0E-9D);
  }

  @Test
  void onlyTheMostRecentNonIdleWindowsCount() {
    FeatureExplosionPacketBatching feature = Mockito.mock(FeatureExplosionPacketBatching.class);
    Mockito.when(feature.readAndResetExplosions()).thenReturn(1000L, 10L, 10L, 10L, 10L, 10L);
    Mockito.when(feature.readAndResetClusters()).thenReturn(1000L, 5L, 5L, 5L, 5L, 5L);
    SamplerExplosionPacketReduction sampler = new SamplerExplosionPacketReduction();
    double reduction = 0D;

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.feature(FeatureExplosionPacketBatching.class)).thenReturn(feature);

      for (int i = 0; i < 6; i++) {
        reduction = sampler.onSample();
      }
    }

    Assertions.assertEquals(0.5D, reduction, 1.0E-9D);
  }

  @Test
  void unavailableUntilAnExplosionWindowIsObserved() {
    FeatureExplosionPacketBatching feature = Mockito.mock(FeatureExplosionPacketBatching.class);
    Mockito.when(feature.readAndResetExplosions()).thenReturn(0L);
    Mockito.when(feature.readAndResetClusters()).thenReturn(0L);
    SamplerExplosionPacketReduction sampler = new SamplerExplosionPacketReduction();

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      react.when(() -> React.feature(FeatureExplosionPacketBatching.class)).thenReturn(feature);

      sampler.onSample();
      sampler.onSample();
    }

    Assertions.assertFalse(sampler.isSampleAvailable());
  }
}
