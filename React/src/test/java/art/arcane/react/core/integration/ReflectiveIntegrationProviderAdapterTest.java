package art.arcane.react.core.integration;

import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.integration.IntegrationHandshakeRequest;
import art.arcane.volmlib.integration.IntegrationHandshakeResponse;
import art.arcane.volmlib.integration.IntegrationProtocolVersion;
import art.arcane.volmlib.integration.IntegrationServiceContract;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class ReflectiveIntegrationProviderAdapterTest {
  @Test
  void convertsClassloaderNeutralWorldGroupsAndSamples() {
    ForeignProvider provider = new ForeignProvider();
    ReflectiveIntegrationProviderAdapter adapter = ReflectiveIntegrationProviderAdapter.tryCreate(
        provider,
        IntegrationServiceContract.class
    );

    Assertions.assertNotNull(adapter);
    Assertions.assertEquals("iris", adapter.pluginId());
    Assertions.assertTrue(adapter.supportedProtocols().contains(new IntegrationProtocolVersion(1, 2)));
    IntegrationHandshakeResponse response = adapter.handshake(new IntegrationHandshakeRequest(
        "react",
        "test",
        Set.of(new IntegrationProtocolVersion(1, 2)),
        Set.of("metrics", "metric-groups"),
        System.currentTimeMillis()
    ));
    Assertions.assertTrue(provider.handshakeInvoked());
    Assertions.assertTrue(response.accepted());
    Assertions.assertEquals(new IntegrationProtocolVersion(1, 2), response.negotiatedProtocol());

    List<IntegrationMetricGroup> groups = adapter.metricGroups();

    Assertions.assertEquals(1, groups.size());
    IntegrationMetricGroup group = groups.get(0);
    Assertions.assertEquals("world", group.scopeKind());
    Assertions.assertEquals("minecraft:overworld", group.scopeId());
    Assertions.assertEquals(12D, group.samples()
        .get(IntegrationMetricSchema.IRIS_LOADED_CHUNKS)
        .valueOr(-1D));
  }

  public static final class ForeignProvider {
    private boolean handshakeInvoked;

    public String pluginId() {
      return "iris";
    }

    public String pluginVersion() {
      return "test";
    }

    public Set<ForeignProtocol> supportedProtocols() {
      return Set.of(new ForeignProtocol(1, 2));
    }

    public Set<String> capabilities() {
      return Set.of("metrics", "metric-groups");
    }

    public Set<ForeignDescriptor> metricDescriptors() {
      return Set.of(descriptor());
    }

    public ForeignHandshakeResponse handshake(ForeignHandshakeRequest request) {
      handshakeInvoked = true;
      ForeignProtocol negotiated = request.supportedProtocols().stream().findFirst().orElse(null);
      return new ForeignHandshakeResponse(
          "iris",
          "test",
          negotiated != null,
          negotiated,
          supportedProtocols(),
          capabilities(),
          negotiated == null ? "no-common-protocol" : "ok",
          System.currentTimeMillis()
      );
    }

    public boolean handshakeInvoked() {
      return handshakeInvoked;
    }

    public ForeignHeartbeat heartbeat() {
      return new ForeignHeartbeat(new ForeignProtocol(1, 2), true, System.currentTimeMillis(), "ok");
    }

    public Map<String, ForeignSample> sampleMetrics(Set<String> keys) {
      ForeignSample sample = sample();
      return Map.of(IntegrationMetricSchema.IRIS_LOADED_CHUNKS, sample);
    }

    public List<ForeignGroup> metricGroups() {
      return List.of(new ForeignGroup(
          "world",
          "minecraft:overworld",
          "world",
          Map.of("plugin", "iris"),
          Map.of(IntegrationMetricSchema.IRIS_LOADED_CHUNKS, sample())
      ));
    }

    private ForeignDescriptor descriptor() {
      return new ForeignDescriptor(
          IntegrationMetricSchema.IRIS_LOADED_CHUNKS,
          ForeignType.LONG,
          "chunks",
          Map.of("plugin", "iris", "domain", "world")
      );
    }

    private ForeignSample sample() {
      return new ForeignSample(descriptor(), 12D, true, System.currentTimeMillis(), "");
    }
  }

  public record ForeignProtocol(int major, int minor) {
  }

  public record ForeignHandshakeRequest(
      String requesterPluginId,
      String requesterVersion,
      Set<ForeignProtocol> supportedProtocols,
      Set<String> capabilities,
      long requestedAtMs
  ) {
  }

  public record ForeignHandshakeResponse(
      String responderPluginId,
      String responderVersion,
      boolean accepted,
      ForeignProtocol negotiatedProtocol,
      Set<ForeignProtocol> supportedProtocols,
      Set<String> capabilities,
      String message,
      long respondedAtMs
  ) {
  }

  public enum ForeignType {
    LONG
  }

  public record ForeignDescriptor(
      String key,
      ForeignType type,
      String unit,
      Map<String, String> tags
  ) {
  }

  public record ForeignSample(
      ForeignDescriptor descriptor,
      Double numericValue,
      boolean available,
      long sampledAtMs,
      String message
  ) {
  }

  public record ForeignHeartbeat(
      ForeignProtocol protocol,
      boolean healthy,
      long lastHeartbeatMs,
      String message
  ) {
  }

  public record ForeignGroup(
      String scopeKind,
      String scopeId,
      String label,
      Map<String, String> tags,
      Map<String, ForeignSample> samples
  ) {
  }
}
