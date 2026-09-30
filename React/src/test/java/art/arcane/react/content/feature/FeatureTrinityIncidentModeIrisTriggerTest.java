package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.IncidentController;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.core.incident.IncidentEvidence;
import art.arcane.react.core.incident.IncidentRecord;
import art.arcane.react.core.integration.RemoteSamplerBridge;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.List;

class FeatureTrinityIncidentModeIrisTriggerTest {
  @Test
  void pregenAtTheInFlightThresholdEngagesDuringServerPressure() throws ReflectiveOperationException {
    TrinityRun run = tick(new FeatureTrinityIncidentMode(), 70D, 16D);

    Assertions.assertTrue(run.engaged());
    IncidentRecord started = run.record();
    Assertions.assertTrue(started.cause().startsWith("Iris pregenerator in-flight chunk requests"), started.cause());
    IncidentEvidence iris = evidence(started, IntegrationMetricSchema.IRIS_PREGEN_QUEUE);
    Assertions.assertTrue(iris.available());
    Assertions.assertEquals(16D, iris.value());
    Assertions.assertEquals(16D, iris.minimum());
  }

  @Test
  void pregenBelowTheInFlightThresholdDoesNotEngage() throws ReflectiveOperationException {
    Assertions.assertFalse(tick(new FeatureTrinityIncidentMode(), 70D, 15D).engaged());
  }

  @Test
  void idlePregenNeverEngagesEvenWithAZeroThreshold() throws ReflectiveOperationException {
    FeatureTrinityIncidentMode feature = new FeatureTrinityIncidentMode();
    setInt(feature, "enterIrisPregenInFlight", 0);

    Assertions.assertFalse(tick(feature, 70D, 0D).engaged());
    Assertions.assertFalse(tick(feature, 70D, -1D).engaged());
  }

  @Test
  void oversizedThresholdClampsToTheLargestIrisConcurrencyCap() throws ReflectiveOperationException {
    FeatureTrinityIncidentMode feature = new FeatureTrinityIncidentMode();
    setInt(feature, "enterIrisPregenInFlight", 5_000);

    TrinityRun run = tick(feature, 70D, 256D);

    Assertions.assertTrue(run.engaged());
    Assertions.assertEquals(256D, evidence(run.record(), IntegrationMetricSchema.IRIS_PREGEN_QUEUE).minimum());
  }

  @Test
  void saturatedPregenWithoutServerPressureDoesNotEngage() throws ReflectiveOperationException {
    Assertions.assertFalse(tick(new FeatureTrinityIncidentMode(), 40D, 256D).engaged());
  }

  private static TrinityRun tick(FeatureTrinityIncidentMode feature, double tickMs, double pregenInFlight)
      throws ReflectiveOperationException {
    Sampler tickSampler = Mockito.mock(Sampler.class);
    Mockito.when(tickSampler.sample()).thenReturn(tickMs);
    RemoteSamplerBridge bridge = Mockito.mock(RemoteSamplerBridge.class);
    Mockito.when(bridge.valueOr(Mockito.anyString(), Mockito.anyString(), Mockito.anyDouble()))
        .thenAnswer(invocation -> invocation.getArgument(2));
    Mockito.when(bridge.valueOr("iris", IntegrationMetricSchema.IRIS_PREGEN_QUEUE, -1D)).thenReturn(pregenInFlight);
    IntegrationController integration = Mockito.mock(IntegrationController.class);
    Mockito.when(integration.getRemoteSamplerBridge()).thenReturn(bridge);
    IncidentController incidents = Mockito.mock(IncidentController.class);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class);
         MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      react.when(() -> React.sampler(SamplerTickTime.ID)).thenReturn(tickSampler);
      react.when(() -> React.controller(IntegrationController.class)).thenReturn(integration);
      react.when(() -> React.controller(IncidentController.class)).thenReturn(incidents);
      feature.onActivate();
      feature.onTick();
    }

    ArgumentCaptor<IncidentRecord> records = ArgumentCaptor.forClass(IncidentRecord.class);
    Mockito.verify(incidents, Mockito.atLeast(0)).record(records.capture());
    List<IncidentRecord> captured = records.getAllValues();
    IncidentRecord started = null;
    for (IncidentRecord record : captured) {
      if ("STARTED".equals(record.phase())) {
        started = record;
      }
    }
    return new TrinityRun(getBoolean(feature, "engaged"), started);
  }

  private static IncidentEvidence evidence(IncidentRecord record, String metricId) {
    for (IncidentEvidence evidence : record.evidence()) {
      if (metricId.equals(evidence.metricId())) {
        return evidence;
      }
    }
    throw new AssertionError("No evidence for " + metricId + " in " + record.evidence());
  }

  private static void setInt(Object owner, String name, int value) throws ReflectiveOperationException {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.setInt(owner, value);
  }

  private static boolean getBoolean(Object owner, String name) throws ReflectiveOperationException {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.getBoolean(owner);
  }

  private record TrinityRun(boolean engaged, IncidentRecord record) {
  }
}
