package art.arcane.react.api.web.resource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RuntimeMetricsResourceTest {
  @Test
  void unavailableControllerDoesNotInventHealthyRegionalMeasurements() {
    RuntimeMetricsResource.Snapshot snapshot = new RuntimeMetricsResource(() -> null).snapshotData();
    Assertions.assertFalse(snapshot.regionalAvailable());
    Assertions.assertFalse(snapshot.jfrAvailable());
    Assertions.assertEquals("source-unavailable", snapshot.regionalUnavailableReason());
    Assertions.assertEquals(0, snapshot.regional().observedPlayers());
    Assertions.assertTrue(snapshot.jfr().series().isEmpty());
  }
}
