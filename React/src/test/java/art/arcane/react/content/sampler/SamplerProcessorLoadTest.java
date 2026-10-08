package art.arcane.react.content.sampler;

import art.arcane.react.core.telemetry.HostTelemetrySnapshot;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

class SamplerProcessorLoadTest {
  @Test
  void samplersReadTheSharedHostWindow() {
    AtomicReference<HostTelemetrySnapshot> host = new AtomicReference<>(withCpu(0.9D, 0.25D));
    SamplerProcessorSystemLoad system = new SamplerProcessorSystemLoad(host::get);
    SamplerProcessorProcessLoad process = new SamplerProcessorProcessLoad(host::get);
    SamplerProcessorOutsideLoad outside = new SamplerProcessorOutsideLoad(host::get);

    Assertions.assertTrue(system.isSampleAvailable());
    Assertions.assertTrue(process.isSampleAvailable());
    Assertions.assertTrue(outside.isSampleAvailable());
    Assertions.assertEquals(0.9D, system.onSample(), 1.0E-9D);
    Assertions.assertEquals(0.25D, process.onSample(), 1.0E-9D);
    Assertions.assertEquals(0.65D, outside.onSample(), 1.0E-9D);
    Assertions.assertEquals(1L, system.captureReading().sampledAtMs());
    Assertions.assertEquals(1L, process.captureReading().sampledAtMs());
    Assertions.assertEquals(1L, outside.captureReading().sampledAtMs());
    Assertions.assertEquals(0.65D, outside.captureReading().value(), 1.0E-9D);

    host.set(withCpu(0.1D, 0.3D));
    Assertions.assertEquals(0D, outside.onSample(), 1.0E-9D);
  }

  @Test
  void loadIsUnavailableBeforeTheFirstMeasuredInterval() {
    HostTelemetrySnapshot empty = HostTelemetrySnapshot.empty();
    SamplerProcessorSystemLoad system = new SamplerProcessorSystemLoad(() -> empty);
    SamplerProcessorProcessLoad process = new SamplerProcessorProcessLoad(() -> empty);
    SamplerProcessorOutsideLoad outside = new SamplerProcessorOutsideLoad(() -> empty);

    Assertions.assertFalse(system.isSampleAvailable());
    Assertions.assertFalse(process.isSampleAvailable());
    Assertions.assertFalse(outside.isSampleAvailable());
    Assertions.assertEquals(0D, system.onSample(), 1.0E-9D);
    Assertions.assertEquals(0D, process.onSample(), 1.0E-9D);
    Assertions.assertEquals(0D, outside.onSample(), 1.0E-9D);
  }

  @Test
  void outsideLoadNeedsBothMeasurements() {
    SamplerProcessorOutsideLoad outside = new SamplerProcessorOutsideLoad(() -> withCpu(0.5D, Double.NaN));

    Assertions.assertFalse(outside.isSampleAvailable());
  }

  private static HostTelemetrySnapshot withCpu(double systemLoad, double processLoad) {
    HostTelemetrySnapshot empty = HostTelemetrySnapshot.empty();
    return new HostTelemetrySnapshot(
        empty.environment(),
        true,
        1L,
        empty.physicalMemoryUsed(),
        empty.physicalMemoryFree(),
        empty.diskUsable(),
        empty.diskReadBytesPerSecond(),
        empty.diskWriteBytesPerSecond(),
        empty.networkReceiveBytesPerSecond(),
        empty.networkSendBytesPerSecond(),
        empty.networkReceiveDrops(),
        empty.networkReceiveErrors(),
        empty.networkSendErrors(),
        empty.heapMax(),
        empty.heapCommitted(),
        empty.heapUtilization(),
        empty.nonHeapUsed(),
        empty.directBufferBytes(),
        empty.directBufferCount(),
        empty.gcCollectionsPerMinute(),
        empty.loadedClasses(),
        empty.processUptimeMs(),
        systemLoad,
        processLoad
    );
  }
}
