package art.arcane.react.core.integration;

import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
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
  void nonAdaptSelectionDoesNotReadDynamicDescriptors() {
    IntegrationServiceContract provider = Mockito.mock(IntegrationServiceContract.class);

    Set<String> keys = IntegrationMetricKeySelector.expectedKeys("iris", provider);

    Assertions.assertEquals(IntegrationMetricSchema.irisKeys(), keys);
    Mockito.verifyNoInteractions(provider);
  }
}
