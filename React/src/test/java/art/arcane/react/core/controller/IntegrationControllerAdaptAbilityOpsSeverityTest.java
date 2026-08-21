package art.arcane.react.core.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationControllerAdaptAbilityOpsSeverityTest {
  @Test
  void highAbilityOpsRemainInformationalWhileMsptIsHealthy() {
    assertFalse(IntegrationController.shouldReportSevereAdaptAbilityOps(240D, 3, 49.9D));
    assertFalse(IntegrationController.shouldReportSevereAdaptAbilityOps(300D, 30, 20D));
  }

  @Test
  void highAbilityOpsRequireSustainedSamplesAndImpactedMspt() {
    assertFalse(IntegrationController.shouldReportSevereAdaptAbilityOps(239D, 3, 60D));
    assertFalse(IntegrationController.shouldReportSevereAdaptAbilityOps(240D, 2, 60D));
    assertTrue(IntegrationController.shouldReportSevereAdaptAbilityOps(240D, 3, 50D));
  }

  @Test
  void unavailableMsptCannotProduceSevereAbilityOpsReport() {
    assertFalse(IntegrationController.shouldReportSevereAdaptAbilityOps(500D, 10, -1D));
    assertFalse(IntegrationController.shouldReportSevereAdaptAbilityOps(500D, 10, Double.NaN));
    assertFalse(IntegrationController.shouldReportSevereAdaptAbilityOps(500D, 10, Double.POSITIVE_INFINITY));
  }
}
