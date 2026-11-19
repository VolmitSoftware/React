package art.arcane.react.core.integration;

import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.integration.IntegrationMetricType;
import art.arcane.volmlib.integration.IntegrationServiceContract;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

class IntegrationMetricKeySelectorTest {
  @Test
  void adaptSelectionUnionsFixedAndDynamicProviderDescriptors() {
    String dynamicKey = IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_PREFIX
        + "excavation-spelunker."
        + IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_TIMING_MS;
    IntegrationMetricDescriptor dynamicDescriptor = IntegrationMetricSchema.descriptor(dynamicKey);
    IntegrationMetricDescriptor irisDescriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.IRIS_PREGEN_QUEUE);
    IntegrationServiceContract provider = Mockito.mock(IntegrationServiceContract.class);
    Mockito.when(provider.metricDescriptors()).thenReturn(Set.of(dynamicDescriptor, irisDescriptor));

    Set<String> keys = IntegrationMetricKeySelector.expectedKeys("Adapt", provider);

    Assertions.assertTrue(keys.containsAll(IntegrationMetricSchema.adaptKeys()));
    Assertions.assertTrue(keys.contains(dynamicKey));
    Assertions.assertFalse(keys.contains(IntegrationMetricSchema.IRIS_PREGEN_QUEUE));
  }

  @Test
  void irisSelectionUnionsFixedAndProviderDescriptorsWithinIrisNamespace() {
    IntegrationServiceContract provider = Mockito.mock(IntegrationServiceContract.class);
    IntegrationMetricDescriptor dynamicIris = new IntegrationMetricDescriptor(
        "iris.experimental-signal",
        IntegrationMetricType.DOUBLE,
        "ratio",
        java.util.Map.of("plugin", "iris")
    );
    IntegrationMetricDescriptor adaptDescriptor = IntegrationMetricSchema.descriptor(
        IntegrationMetricSchema.ADAPT_SESSION_LOAD
    );
    Mockito.when(provider.metricDescriptors()).thenReturn(Set.of(dynamicIris, adaptDescriptor));

    Set<String> keys = IntegrationMetricKeySelector.expectedKeys("iris", provider);

    Assertions.assertTrue(keys.containsAll(IntegrationMetricSchema.irisKeys()));
    Assertions.assertTrue(keys.contains(dynamicIris.key()));
    Assertions.assertFalse(keys.contains(IntegrationMetricSchema.ADAPT_SESSION_LOAD));
    Mockito.verify(provider).metricDescriptors();
  }

  @Test
  void glossSelectionUnionsFixedAndProviderDescriptorsWithinGlossNamespace() {
    IntegrationServiceContract provider = Mockito.mock(IntegrationServiceContract.class);
    IntegrationMetricDescriptor dynamicGloss = new IntegrationMetricDescriptor(
        "gloss.experimental-signal",
        IntegrationMetricType.DOUBLE,
        "ratio",
        java.util.Map.of("plugin", "gloss")
    );
    IntegrationMetricDescriptor irisDescriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.IRIS_PREGEN_QUEUE);
    Mockito.when(provider.metricDescriptors()).thenReturn(Set.of(dynamicGloss, irisDescriptor));

    Set<String> keys = IntegrationMetricKeySelector.expectedKeys("Gloss", provider);

    Assertions.assertTrue(keys.containsAll(IntegrationMetricSchema.glossKeys()));
    Assertions.assertTrue(keys.contains(dynamicGloss.key()));
    Assertions.assertFalse(keys.contains(IntegrationMetricSchema.IRIS_PREGEN_QUEUE));
  }

  @Test
  void hiddenoreSelectionUsesFixedHiddenoreKeys() {
    IntegrationServiceContract provider = Mockito.mock(IntegrationServiceContract.class);
    Mockito.when(provider.metricDescriptors()).thenReturn(Set.of());

    Set<String> keys = IntegrationMetricKeySelector.expectedKeys("HiddenOre", provider);

    Assertions.assertEquals(IntegrationMetricSchema.hiddenoreKeys(), keys);
  }

  @Test
  void biletoolsSelectionUsesFixedBiletoolsKeys() {
    IntegrationServiceContract provider = Mockito.mock(IntegrationServiceContract.class);
    Mockito.when(provider.metricDescriptors()).thenReturn(Set.of());

    Set<String> keys = IntegrationMetricKeySelector.expectedKeys("BileTools", provider);

    Assertions.assertEquals(IntegrationMetricSchema.biletoolsKeys(), keys);
  }
}
