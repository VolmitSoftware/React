package art.arcane.react.core.integration;

import art.arcane.react.React;
import art.arcane.react.api.metric.ReactMetrics;
import art.arcane.react.content.sampler.SamplerUnknown;
import art.arcane.volmlib.integration.IntegrationHandshakeRequest;
import art.arcane.volmlib.integration.IntegrationHandshakeResponse;
import art.arcane.volmlib.integration.IntegrationHeartbeat;
import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.integration.IntegrationMetricType;
import art.arcane.volmlib.integration.IntegrationProtocolNegotiator;
import art.arcane.volmlib.integration.IntegrationProtocolVersion;
import art.arcane.volmlib.integration.IntegrationServiceContract;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class ReactIntegrationService implements IntegrationServiceContract {
  private static final long SAMPLER_FAILURE_LOG_INTERVAL_MS = 10_000L;
  private static final String SAMPLER_METRIC_PREFIX = "react.sampler.";
  private static final IntegrationProtocolVersion CURRENT_PROTOCOL = new IntegrationProtocolVersion(1, 2);
  private static final Set<IntegrationProtocolVersion> SUPPORTED_PROTOCOLS = Set.of(
      new IntegrationProtocolVersion(1, 0),
      new IntegrationProtocolVersion(1, 1),
      CURRENT_PROTOCOL
  );
  private static final Set<String> CAPABILITIES = Set.of(
      "handshake",
      "heartbeat",
      "metrics",
      "react-status"
  );
  private final AtomicLong lastSamplerFailureLogMs = new AtomicLong(0L);

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
    List<String> samplerIds = samplerIds();
    Set<IntegrationMetricDescriptor> descriptors = new LinkedHashSet<>(samplerIds.size());
    for (String samplerId : samplerIds) {
      descriptors.add(samplerDescriptor(samplerId));
    }
    return Collections.unmodifiableSet(descriptors);
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

    return new IntegrationHandshakeResponse(
        pluginId(),
        pluginVersion(),
        true,
        negotiated.get(),
        SUPPORTED_PROTOCOLS,
        CAPABILITIES,
        "ok",
        now
    );
  }

  @Override
  public IntegrationHeartbeat heartbeat() {
    long now = System.currentTimeMillis();
    return new IntegrationHeartbeat(CURRENT_PROTOCOL, true, now, "ok");
  }

  @Override
  public Map<String, IntegrationMetricSample> sampleMetrics(Set<String> metricKeys) {
    Map<String, IntegrationMetricSample> samples = new LinkedHashMap<>();
    if (metricKeys == null || metricKeys.isEmpty()) {
      return samples;
    }

    List<String> requestedKeys = new ArrayList<>(metricKeys.size());
    for (String metricKey : metricKeys) {
      if (metricKey != null && !metricKey.isBlank()) {
        requestedKeys.add(metricKey);
      }
    }
    Collections.sort(requestedKeys);

    Set<String> availableSamplerIds = Set.copyOf(samplerIds());
    long now = System.currentTimeMillis();
    for (String metricKey : requestedKeys) {
      if (!isSamplerMetricKey(metricKey)) {
        samples.put(metricKey, IntegrationMetricSample.unavailable(
            IntegrationMetricSchema.descriptor(metricKey),
            "react-does-not-publish-this-metric",
            now
        ));
        continue;
      }

      String samplerId = samplerId(metricKey);
      IntegrationMetricDescriptor descriptor = samplerDescriptor(samplerId);
      if (!availableSamplerIds.contains(samplerId)) {
        samples.put(metricKey, IntegrationMetricSample.unavailable(
            descriptor,
            "react-sampler-not-registered",
            now
        ));
        continue;
      }

      samples.put(metricKey, sampleSampler(descriptor, samplerId, now));
    }
    return samples;
  }

  private List<String> samplerIds() {
    Set<String> registeredIds = ReactMetrics.hostMetricKeys();
    if (registeredIds.isEmpty()) {
      return List.of();
    }

    List<String> samplerIds = new ArrayList<>(registeredIds.size());
    for (String samplerId : registeredIds) {
      if (samplerId == null
          || samplerId.isBlank()
          || SamplerUnknown.ID.equals(samplerId)) {
        continue;
      }
      samplerIds.add(samplerId);
    }
    Collections.sort(samplerIds);
    return samplerIds;
  }

  private IntegrationMetricDescriptor samplerDescriptor(String samplerId) {
    return new IntegrationMetricDescriptor(
        SAMPLER_METRIC_PREFIX + samplerId,
        IntegrationMetricType.DOUBLE,
        "",
        Map.of(
            "plugin", "react",
            "domain", "sampler",
            "scope", "global",
            "sampler-id", samplerId
        )
    );
  }

  private IntegrationMetricSample sampleSampler(
      IntegrationMetricDescriptor descriptor,
      String samplerId,
      long now
  ) {
    double value;
    try {
      value = ReactMetrics.readHostMetric(samplerId);
    } catch (Throwable e) {
      reportSamplerFailure(samplerId, e, now);
      return IntegrationMetricSample.unavailable(
          descriptor,
          "react-sampler-error:" + e.getClass().getSimpleName(),
          now
      );
    }

    if (!Double.isFinite(value)) {
      return IntegrationMetricSample.unavailable(
          descriptor,
          "react-sampler-value-unavailable",
          now
      );
    }

    return IntegrationMetricSample.available(descriptor, value, now);
  }

  private void reportSamplerFailure(String samplerId, Throwable failure, long now) {
    long last = lastSamplerFailureLogMs.get();
    if (now - last < SAMPLER_FAILURE_LOG_INTERVAL_MS
        || !lastSamplerFailureLogMs.compareAndSet(last, now)) {
      return;
    }

    React.warn("Integration sampler read failed: id=" + samplerId, failure);
  }

  private boolean isSamplerMetricKey(String metricKey) {
    return metricKey.startsWith(SAMPLER_METRIC_PREFIX)
        && metricKey.length() > SAMPLER_METRIC_PREFIX.length();
  }

  private String samplerId(String metricKey) {
    return metricKey.substring(SAMPLER_METRIC_PREFIX.length());
  }
}
