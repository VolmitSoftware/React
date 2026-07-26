package art.arcane.react.core.integration;

import art.arcane.volmlib.integration.IntegrationHandshakeRequest;
import art.arcane.volmlib.integration.IntegrationHandshakeResponse;
import art.arcane.volmlib.integration.IntegrationHeartbeat;
import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.integration.IntegrationMetricType;
import art.arcane.volmlib.integration.IntegrationProtocolVersion;
import art.arcane.volmlib.integration.IntegrationServiceContract;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapts IntegrationServiceContract implementations loaded from other plugin
 * classloaders. This keeps integration functional even when VolmLib classes are
 * shaded/relocated differently.
 */
public final class ReflectiveIntegrationProviderAdapter implements IntegrationServiceContract {
  private static final Set<IntegrationProtocolVersion> EMPTY_PROTOCOLS = Set.of();
  private static final Set<String> EMPTY_CAPABILITIES = Set.of();
  private static final Set<IntegrationMetricDescriptor> EMPTY_DESCRIPTORS = Set.of();
  private static final MethodEntry MISSING_METHOD = new MethodEntry(null);
  private static final ClassValue<Map<String, MethodEntry>> NO_ARG_METHODS = new ClassValue<Map<String, MethodEntry>>() {
    @Override
    protected Map<String, MethodEntry> computeValue(Class<?> type) {
      return new ConcurrentHashMap<>();
    }
  };

  private final Object provider;
  private final Method pluginIdMethod;
  private final Method pluginVersionMethod;
  private final Method supportedProtocolsMethod;
  private final Method capabilitiesMethod;
  private final Method metricDescriptorsMethod;
  private final Method handshakeMethod;
  private final Method heartbeatMethod;
  private final Method sampleMetricsMethod;
  private final Method metricGroupsMethod;

  private final String pluginId;
  private final String pluginVersion;
  private final String sourceServiceClassName;

  private ReflectiveIntegrationProviderAdapter(Object provider, Class<?> serviceClass) {
    this.provider = provider;
    Class<?> providerClass = provider.getClass();
    this.pluginIdMethod = requireMethod(providerClass, "pluginId");
    this.pluginVersionMethod = requireMethod(providerClass, "pluginVersion");
    this.supportedProtocolsMethod = requireMethod(providerClass, "supportedProtocols");
    this.capabilitiesMethod = requireMethod(providerClass, "capabilities");
    this.metricDescriptorsMethod = requireMethod(providerClass, "metricDescriptors");
    this.handshakeMethod = requireSingleArgumentMethod(providerClass, "handshake");
    this.heartbeatMethod = requireMethod(providerClass, "heartbeat");
    this.sampleMetricsMethod = requireMethod(providerClass, "sampleMetrics", Set.class);
    this.metricGroupsMethod = optionalMethod(providerClass, "metricGroups");
    this.pluginId = normalize(invokeString(this.pluginIdMethod, ""));
    this.pluginVersion = invokeString(this.pluginVersionMethod, "");
    this.sourceServiceClassName = serviceClass == null ? providerClass.getName() : serviceClass.getName();
  }

  public static boolean supportsServiceClass(Class<?> serviceClass) {
    if (serviceClass == null) {
      return false;
    }

    String name = serviceClass.getName();
    return name.endsWith(".integration.IntegrationServiceContract")
        || "IntegrationServiceContract".equals(serviceClass.getSimpleName());
  }

  public static ReflectiveIntegrationProviderAdapter tryCreate(Object provider, Class<?> serviceClass) {
    if (provider == null || !supportsServiceClass(serviceClass)) {
      return null;
    }

    try {
      return new ReflectiveIntegrationProviderAdapter(provider, serviceClass);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private static Method requireMethod(Class<?> source, String name, Class<?>... args) {
    try {
      Method method = source.getMethod(name, args);
      method.setAccessible(true);
      return method;
    } catch (Throwable e) {
      throw new IllegalStateException("Missing method " + source.getName() + "#" + name, e);
    }
  }

  private static Method optionalMethod(Class<?> source, String name, Class<?>... args) {
    try {
      Method method = source.getMethod(name, args);
      method.setAccessible(true);
      return method;
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static Method requireSingleArgumentMethod(Class<?> source, String name) {
    for (Method method : source.getMethods()) {
      if (method.getName().equals(name) && method.getParameterCount() == 1) {
        method.setAccessible(true);
        return method;
      }
    }
    throw new IllegalStateException("Missing method " + source.getName() + "#" + name);
  }

  private static String toString(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String string = String.valueOf(value);
    return string == null ? fallback : string;
  }

  private static boolean toBoolean(Object value, boolean fallback) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    return fallback;
  }

  private static long toLong(Object value, long fallback) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    return fallback;
  }

  private static Integer toInteger(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    return null;
  }

  private static Double toDoubleOrNull(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return null;
  }

  public String sourceServiceClassName() {
    return sourceServiceClassName;
  }

  @Override
  public String pluginId() {
    return pluginId;
  }

  @Override
  public String pluginVersion() {
    return pluginVersion;
  }

  @Override
  public Set<IntegrationProtocolVersion> supportedProtocols() {
    return toProtocolSet(invokeObject(supportedProtocolsMethod), EMPTY_PROTOCOLS);
  }

  @Override
  public Set<String> capabilities() {
    return toStringSet(invokeObject(capabilitiesMethod), EMPTY_CAPABILITIES);
  }

  @Override
  public Set<IntegrationMetricDescriptor> metricDescriptors() {
    Object raw = invokeObject(metricDescriptorsMethod);
    if (!(raw instanceof Collection<?> values)) {
      return EMPTY_DESCRIPTORS;
    }

    Set<IntegrationMetricDescriptor> descriptors = new LinkedHashSet<>();
    for (Object value : values) {
      IntegrationMetricDescriptor descriptor = toMetricDescriptor(value, "");
      if (descriptor != null) {
        descriptors.add(descriptor);
      }
    }

    return descriptors.isEmpty() ? EMPTY_DESCRIPTORS : Set.copyOf(descriptors);
  }

  @Override
  public IntegrationHandshakeResponse handshake(IntegrationHandshakeRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Handshake request cannot be null");
    }
    Object remoteRequest = toRemoteHandshakeRequest(request);
    Object raw = invokeObject(handshakeMethod, remoteRequest);
    if (raw == null) {
      return null;
    }
    return new IntegrationHandshakeResponse(
        toString(invokeNoArg(raw, "responderPluginId"), pluginId()),
        toString(invokeNoArg(raw, "responderVersion"), pluginVersion()),
        toBoolean(invokeNoArg(raw, "accepted"), false),
        toProtocol(invokeNoArg(raw, "negotiatedProtocol")),
        toProtocolSet(invokeNoArg(raw, "supportedProtocols"), EMPTY_PROTOCOLS),
        toStringSet(invokeNoArg(raw, "capabilities"), EMPTY_CAPABILITIES),
        toString(invokeNoArg(raw, "message"), ""),
        toLong(invokeNoArg(raw, "respondedAtMs"), System.currentTimeMillis())
    );
  }

  private Object toRemoteHandshakeRequest(IntegrationHandshakeRequest request) {
    Object rawProtocols = invokeObject(supportedProtocolsMethod);
    Set<Object> requestedProtocols = new LinkedHashSet<>();
    if (rawProtocols instanceof Collection<?> protocols) {
      for (Object protocol : protocols) {
        IntegrationProtocolVersion converted = toProtocol(protocol);
        if (converted != null && request.supportedProtocols().contains(converted)) {
          requestedProtocols.add(protocol);
        }
      }
    }

    Class<?> requestType = handshakeMethod.getParameterTypes()[0];
    try {
      Constructor<?> constructor = requestType.getConstructor(
          String.class,
          String.class,
          Set.class,
          Set.class,
          long.class
      );
      constructor.setAccessible(true);
      return constructor.newInstance(
          request.requesterPluginId(),
          request.requesterVersion(),
          requestedProtocols,
          request.capabilities(),
          request.requestedAtMs()
      );
    } catch (Throwable e) {
      throw new IllegalStateException("Unable to construct reflective handshake request for " + pluginId(), e);
    }
  }

  @Override
  public IntegrationHeartbeat heartbeat() {
    Object raw = invokeObject(heartbeatMethod);
    long now = System.currentTimeMillis();
    if (raw == null) {
      return new IntegrationHeartbeat(null, false, now, "heartbeat-missing (reflective)");
    }

    IntegrationProtocolVersion protocol = toProtocol(invokeNoArg(raw, "protocol"));
    boolean healthy = toBoolean(invokeNoArg(raw, "healthy"), false);
    long lastHeartbeatMs = toLong(invokeNoArg(raw, "lastHeartbeatMs"), now);
    String message = toString(invokeNoArg(raw, "message"), "");
    return new IntegrationHeartbeat(protocol, healthy, lastHeartbeatMs, message);
  }

  @Override
  public Map<String, IntegrationMetricSample> sampleMetrics(Set<String> metricKeys) {
    Object raw = invokeObject(sampleMetricsMethod, metricKeys);
    if (!(raw instanceof Map<?, ?> map)) {
      return unavailableMetrics(metricKeys, "invalid-metric-map (reflective)");
    }

    Map<String, IntegrationMetricSample> out = new LinkedHashMap<>();
    long now = System.currentTimeMillis();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key = toString(entry.getKey(), "");
      if (key.isBlank()) {
        continue;
      }

      IntegrationMetricSample converted = toMetricSample(entry.getValue(), key, now);
      out.put(key, converted);
    }

    return out;
  }

  @Override
  public List<IntegrationMetricGroup> metricGroups() {
    if (metricGroupsMethod == null) {
      return List.of();
    }

    Object raw = invokeObject(metricGroupsMethod);
    if (!(raw instanceof Collection<?> values)) {
      return List.of();
    }

    List<IntegrationMetricGroup> groups = new ArrayList<>();
    for (Object value : values) {
      IntegrationMetricGroup group = toMetricGroup(value);
      if (group != null) {
        groups.add(group);
      }
    }
    return groups.isEmpty() ? List.of() : List.copyOf(groups);
  }

  private Map<String, IntegrationMetricSample> unavailableMetrics(Set<String> metricKeys, String reason) {
    if (metricKeys == null || metricKeys.isEmpty()) {
      return Map.of();
    }

    Map<String, IntegrationMetricSample> out = new LinkedHashMap<>();
    long now = System.currentTimeMillis();
    for (String key : metricKeys) {
      out.put(key, IntegrationMetricSample.unavailable(
          IntegrationMetricSchema.descriptor(key),
          reason,
          now
      ));
    }
    return out;
  }

  private IntegrationMetricSample toMetricSample(Object value, String keyFallback, long now) {
    if (value == null) {
      return IntegrationMetricSample.unavailable(
          IntegrationMetricSchema.descriptor(keyFallback),
          "sample-null (reflective)",
          now
      );
    }

    IntegrationMetricDescriptor descriptor = toMetricDescriptor(invokeNoArg(value, "descriptor"), keyFallback);
    if (descriptor == null) {
      descriptor = IntegrationMetricSchema.descriptor(keyFallback);
    }

    boolean available = toBoolean(invokeNoArg(value, "available"), false);
    Double numericValue = toDoubleOrNull(invokeNoArg(value, "numericValue"));
    long sampledAtMs = toLong(invokeNoArg(value, "sampledAtMs"), now);
    String message = toString(invokeNoArg(value, "message"), "");
    try {
      return new IntegrationMetricSample(descriptor, numericValue, available, sampledAtMs, message);
    } catch (IllegalArgumentException ignored) {
      return IntegrationMetricSample.unavailable(descriptor, "sample-invalid (reflective)", now);
    }
  }

  private IntegrationMetricGroup toMetricGroup(Object value) {
    if (value == null) {
      return null;
    }

    String scopeKind = toString(invokeNoArg(value, "scopeKind"), "");
    String scopeId = toString(invokeNoArg(value, "scopeId"), "");
    String label = toString(invokeNoArg(value, "label"), scopeId);
    if (scopeKind.isBlank() || scopeId.isBlank()) {
      return null;
    }

    Map<String, String> tags = toStringMap(invokeNoArg(value, "tags"));
    Object rawSamples = invokeNoArg(value, "samples");
    Map<String, IntegrationMetricSample> samples = new LinkedHashMap<>();
    long now = System.currentTimeMillis();
    if (rawSamples instanceof Map<?, ?> sampleMap) {
      for (Map.Entry<?, ?> entry : sampleMap.entrySet()) {
        String key = toString(entry.getKey(), "");
        if (key.isBlank()) {
          continue;
        }
        IntegrationMetricSample sample = toMetricSample(entry.getValue(), key, now);
        if (key.equals(sample.descriptor().key())) {
          samples.put(key, sample);
        }
      }
    }

    try {
      return new IntegrationMetricGroup(scopeKind, scopeId, label, tags, samples);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  public boolean wrapsSameProvider(ReflectiveIntegrationProviderAdapter other) {
    return other != null && provider == other.provider;
  }

  private IntegrationMetricDescriptor toMetricDescriptor(Object value, String keyFallback) {
    if (value == null) {
      if (keyFallback == null || keyFallback.isBlank()) {
        return null;
      }
      return IntegrationMetricSchema.descriptor(keyFallback);
    }

    String key = toString(invokeNoArg(value, "key"), keyFallback);
    if (key == null || key.isBlank()) {
      return null;
    }

    String typeName = toString(invokeNoArg(invokeNoArg(value, "type"), "name"), "DOUBLE");
    IntegrationMetricType metricType;
    try {
      metricType = IntegrationMetricType.valueOf(typeName.toUpperCase(Locale.ROOT));
    } catch (Throwable ignored) {
      metricType = IntegrationMetricType.DOUBLE;
    }

    String unit = toString(invokeNoArg(value, "unit"), "");
    Map<String, String> tags = toStringMap(invokeNoArg(value, "tags"));
    return new IntegrationMetricDescriptor(key, metricType, unit, tags);
  }

  private Set<IntegrationProtocolVersion> toProtocolSet(Object value, Set<IntegrationProtocolVersion> fallback) {
    if (!(value instanceof Collection<?> values)) {
      return fallback;
    }

    Set<IntegrationProtocolVersion> protocols = new LinkedHashSet<>();
    for (Object protocol : values) {
      IntegrationProtocolVersion converted = toProtocol(protocol);
      if (converted != null) {
        protocols.add(converted);
      }
    }

    return protocols.isEmpty() ? fallback : Set.copyOf(protocols);
  }

  private IntegrationProtocolVersion toProtocol(Object value) {
    if (value == null) {
      return null;
    }

    Integer major = toInteger(invokeNoArg(value, "major"));
    Integer minor = toInteger(invokeNoArg(value, "minor"));
    if (major == null || minor == null) {
      return null;
    }

    try {
      return new IntegrationProtocolVersion(major, minor);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private Set<String> toStringSet(Object value, Set<String> fallback) {
    if (!(value instanceof Collection<?> values)) {
      return fallback;
    }

    Set<String> out = new LinkedHashSet<>();
    for (Object entry : values) {
      String normalized = normalize(toString(entry, ""));
      if (!normalized.isBlank()) {
        out.add(normalized);
      }
    }

    return out.isEmpty() ? fallback : Set.copyOf(out);
  }

  private Map<String, String> toStringMap(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return Map.of();
    }

    Map<String, String> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key = toString(entry.getKey(), "");
      if (key.isBlank()) {
        continue;
      }
      out.put(key, toString(entry.getValue(), ""));
    }
    return out.isEmpty() ? Map.of() : Map.copyOf(out);
  }

  private Object invokeObject(Method method, Object... args) {
    try {
      return method.invoke(provider, args);
    } catch (Throwable e) {
      throw new RuntimeException("Failed reflective call " + method.getName() + " on " + provider.getClass().getName(), e);
    }
  }

  private Object invokeNoArg(Object target, String methodName) {
    if (target == null || methodName == null || methodName.isBlank()) {
      return null;
    }

    Class<?> targetClass = target.getClass();
    Map<String, MethodEntry> cache = NO_ARG_METHODS.get(targetClass);
    MethodEntry entry = cache.get(methodName);
    if (entry == null) {
      entry = resolveNoArgMethod(targetClass, methodName);
      cache.put(methodName, entry);
    }

    if (entry.method() == null) {
      return null;
    }

    try {
      return entry.method().invoke(target);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static MethodEntry resolveNoArgMethod(Class<?> targetClass, String methodName) {
    try {
      Method method = targetClass.getMethod(methodName);
      method.setAccessible(true);
      return new MethodEntry(method);
    } catch (Throwable ignored) {
      return MISSING_METHOD;
    }
  }

  private String invokeString(Method method, String fallback) {
    return toString(invokeObject(method), fallback);
  }

  private record MethodEntry(Method method) {
  }
}
