package art.arcane.react.core.integration;

import art.arcane.react.React;
import art.arcane.volmlib.integration.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ReactIntegrationService implements IntegrationServiceContract {
  private static final Set<IntegrationProtocolVersion> SUPPORTED_PROTOCOLS = Set.of(
      new IntegrationProtocolVersion(1, 0),
      new IntegrationProtocolVersion(1, 1)
  );
  private static final Set<String> CAPABILITIES = Set.of(
      "handshake",
      "heartbeat",
      "metrics",
      "react-status"
  );

  private volatile IntegrationProtocolVersion negotiatedProtocol = new IntegrationProtocolVersion(1, 1);

  public void register() {
    Bukkit.getServicesManager().register(IntegrationServiceContract.class, this, React.instance, ServicePriority.Normal);
  }

  public void unregister() {
    Bukkit.getServicesManager().unregister(IntegrationServiceContract.class, this);
  }

  public IntegrationHandshakeRequest createRequest() {
    return new IntegrationHandshakeRequest(
        pluginId(),
        pluginVersion(),
        supportedProtocols(),
        capabilities(),
        System.currentTimeMillis()
    );
  }

  @Override
  public String pluginId() {
    return "react";
  }

  @Override
  public String pluginVersion() {
    return React.instance.getDescription().getVersion();
  }

  @Override
  public Set<IntegrationProtocolVersion> supportedProtocols() {
    return SUPPORTED_PROTOCOLS;
  }

  @Override
  public Set<String> capabilities() {
    return CAPABILITIES;
  }

  @Override
  public Set<IntegrationMetricDescriptor> metricDescriptors() {
    return Set.of();
  }

  @Override
  public IntegrationHandshakeResponse handshake(IntegrationHandshakeRequest request) {
    long now = System.currentTimeMillis();
    if (request == null) {
      return new IntegrationHandshakeResponse(
          pluginId(),
          pluginVersion(),
          false,
          null,
          SUPPORTED_PROTOCOLS,
          CAPABILITIES,
          "missing request",
          now
      );
    }

    Optional<IntegrationProtocolVersion> negotiated = IntegrationProtocolNegotiator.negotiate(
        SUPPORTED_PROTOCOLS,
        request.supportedProtocols()
    );
    if (negotiated.isEmpty()) {
      return new IntegrationHandshakeResponse(
          pluginId(),
          pluginVersion(),
          false,
          null,
          SUPPORTED_PROTOCOLS,
          CAPABILITIES,
          "no-common-protocol",
          now
      );
    }

    negotiatedProtocol = negotiated.get();
    return new IntegrationHandshakeResponse(
        pluginId(),
        pluginVersion(),
        true,
        negotiatedProtocol,
        SUPPORTED_PROTOCOLS,
        CAPABILITIES,
        "ok",
        now
    );
  }

  @Override
  public IntegrationHeartbeat heartbeat() {
    long now = System.currentTimeMillis();
    return new IntegrationHeartbeat(negotiatedProtocol, true, now, "ok");
  }

  @Override
  public Map<String, IntegrationMetricSample> sampleMetrics(Set<String> metricKeys) {
    Map<String, IntegrationMetricSample> out = new HashMap<>();
    if (metricKeys == null) {
      return out;
    }

    long now = System.currentTimeMillis();
    for (String key : metricKeys) {
      out.put(key, IntegrationMetricSample.unavailable(
          IntegrationMetricSchema.descriptor(key),
          "react-does-not-publish-this-metric",
          now
      ));
    }
    return out;
  }
}
