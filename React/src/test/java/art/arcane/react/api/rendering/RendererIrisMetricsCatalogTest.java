package art.arcane.react.api.rendering;

import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RendererIrisMetricsCatalogTest {
  @Test
  void globalDashboardsCoverEveryPublishedIrisScalar() {
    Assertions.assertEquals(
        IntegrationMetricSchema.irisKeys(),
        RendererIrisMetrics.dashboardMetricKeys()
    );
  }

  @Test
  void worldDashboardsCoverEveryPublishedIrisWorldMetric() {
    Assertions.assertEquals(
        IntegrationMetricSchema.irisWorldKeys(),
        RendererIrisWorldMetrics.dashboardMetricKeys()
    );
  }
}
