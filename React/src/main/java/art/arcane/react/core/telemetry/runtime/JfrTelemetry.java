package art.arcane.react.core.telemetry.runtime;

import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JfrTelemetry implements AutoCloseable {
  private static final int MAX_SERIES = 64;
  private static final long MAX_RECORDING_BYTES = 64L * 1024L * 1024L;
  private final Accumulator accumulator;
  private final Set<String> supported;
  private final RecordingStream stream;
  private volatile boolean failed;

  private JfrTelemetry(Options options, Consumer<Throwable> failures) {
    accumulator = new Accumulator(options.slowThresholdMs(), System.nanoTime());
    stream = new RecordingStream();
    stream.setMaxAge(Duration.ofSeconds(30));
    stream.setMaxSize(MAX_RECORDING_BYTES);
    stream.setReuse(true);
    stream.setOrdered(false);
    Map<String, EventType> types = new HashMap<>();
    for (EventType type : FlightRecorder.getFlightRecorder().getEventTypes()) {
      types.put(type.getName(), type);
      stream.disable(type.getName());
    }
    List<String> enabled = new ArrayList<>();
    for (Channel channel : Channel.values()) {
      EventType type = types.get(channel.eventName);
      if (!hasFields(type, channel.fields)) {
        continue;
      }
      stream.enable(channel.eventName).withoutStackTrace()
          .withThreshold(Duration.ofMillis(channel.slow ? options.slowThresholdMs() : 0L))
          .withPeriod(Duration.ofSeconds(1));
      stream.onEvent(channel.eventName, event -> accumulator.record(channel, event));
      enabled.add(channel.eventName);
    }
    supported = Set.copyOf(enabled);
    AtomicBoolean reported = new AtomicBoolean();
    stream.onError(failure -> {
      failed = true;
      if (reported.compareAndSet(false, true)) {
        failures.accept(failure);
      }
    });
    stream.onClose(() -> failed = true);
    try {
      stream.startAsync();
    } catch (RuntimeException | LinkageError failure) {
      stream.close();
      throw failure;
    }
  }

  public static JfrTelemetry start(Options options, Consumer<Throwable> failures) {
    return new JfrTelemetry(options, failures);
  }

  public Snapshot snapshot(long nowMs) {
    return accumulator.publish(supported, !failed, nowMs, System.nanoTime());
  }

  @Override
  public void close() {
    failed = true;
    stream.close();
  }

  public static List<RuntimeMetricSampler.Definition> definitions() {
    List<RuntimeMetricSampler.Definition> definitions = new ArrayList<>();
    for (Channel channel : Channel.values()) {
      definitions.add(new RuntimeMetricSampler.Definition(channel.id + "-rate", channel.title + " Rate", "/s", 1_000L));
      if (channel.duration) {
        definitions.add(new RuntimeMetricSampler.Definition(channel.id + "-mean-ms", channel.title + " Mean", "ms", 1_000L));
        definitions.add(new RuntimeMetricSampler.Definition(channel.id + "-max-ms", channel.title + " Maximum", "ms", 1_000L));
      }
      if (channel.bytes) {
        definitions.add(new RuntimeMetricSampler.Definition(channel.id + "-bytes-rate", channel.title + " Bytes", "bytes/s", 1_000L));
      }
    }
    definitions.add(new RuntimeMetricSampler.Definition("jfr-network-received-packets-rate", "Protocol Packets Received", "/s", 1_000L));
    definitions.add(new RuntimeMetricSampler.Definition("jfr-network-received-bytes-rate", "Protocol Bytes Received", "bytes/s", 1_000L));
    definitions.add(new RuntimeMetricSampler.Definition("jfr-delivery-delay-ms", "JFR Maximum Delivery Delay", "ms", 1_000L));
    return List.copyOf(definitions);
  }

  static boolean hasFields(EventType type, List<String> names) {
    if (type == null) {
      return false;
    }
    for (String name : names) {
      ValueDescriptor field = type.getField(name);
      if (field == null) {
        return false;
      }
      boolean text = name.equals("level") || name.equals("targetStatus") || name.equals("levelId") || name.equals("type");
      if (text ? !field.getTypeName().equals("java.lang.String")
          : !field.getTypeName().equals("long") && !field.getTypeName().equals("int")) {
        return false;
      }
    }
    return true;
  }

  public record Options(long slowThresholdMs) {
    public Options {
      slowThresholdMs = Math.clamp(slowThresholdMs, 1L, 1_000L);
    }
  }

  public record Snapshot(long sampledAtMs, long windowMs, long slowThresholdMs, boolean available,
                         boolean complete, Set<String> supportedEvents, Map<String, RuntimeMetric> metrics, List<Series> series) {
    public static final Snapshot EMPTY = new Snapshot(0L, 0L, 10L, false, false, Set.of(), Map.of(), List.of());
  }

  public record Series(String channel, String level, String category, long count, long bytes,
                       double meanMs, double maxMs) {
  }

  enum Channel {
    GENERATION("minecraft.ChunkGeneration", "jfr-chunk-generation", "Chunk Generation Stage", true, false, false, List.of("level", "targetStatus", "duration")),
    READ("minecraft.ChunkRegionRead", "jfr-region-read", "Chunk Storage Reads", false, true, false, List.of("levelId", "type", "bytes")),
    WRITE("minecraft.ChunkRegionWrite", "jfr-region-write", "Chunk Storage Writes", false, true, false, List.of("levelId", "type", "bytes")),
    NETWORK("minecraft.NetworkSummary", "jfr-network-sent", "Protocol Packets Sent", false, true, false, List.of("sentPackets", "sentBytes", "receivedPackets", "receivedBytes")),
    FILE_READ("jdk.FileRead", "jfr-slow-file-read", "Slow File Reads", true, true, true, List.of("duration", "bytesRead")),
    FILE_WRITE("jdk.FileWrite", "jfr-slow-file-write", "Slow File Writes", true, true, true, List.of("duration", "bytesWritten")),
    MONITOR("jdk.JavaMonitorEnter", "jfr-slow-monitor-enter", "Slow Monitor Entries", true, false, true, List.of("duration")),
    WAIT("jdk.JavaMonitorWait", "jfr-slow-monitor-wait", "Slow Monitor Waits", true, false, true, List.of("duration")),
    PARK("jdk.ThreadPark", "jfr-slow-thread-park", "Slow Thread Parks", true, false, true, List.of("duration")),
    LOSS("jdk.DataLoss", "jfr-data-loss", "JFR Data Loss Events", false, true, false, List.of("amount"));

    private final String eventName;
    private final String id;
    private final String title;
    private final boolean duration;
    private final boolean bytes;
    private final boolean slow;
    private final List<String> fields;

    Channel(String eventName, String id, String title, boolean duration, boolean bytes, boolean slow, List<String> fields) {
      this.eventName = eventName;
      this.id = id;
      this.title = title;
      this.duration = duration;
      this.bytes = bytes;
      this.slow = slow;
      this.fields = fields;
    }
  }

  static final class Accumulator {
    private final Map<Channel, Totals> totals = new HashMap<>();
    private final Map<SeriesKey, Totals> series = new LinkedHashMap<>();
    private final long thresholdMs;
    private long previousNanos;
    private long receivedPackets;
    private long receivedBytes;
    private long delayMs;

    Accumulator(long thresholdMs, long nowNanos) {
      this.thresholdMs = thresholdMs;
      previousNanos = nowNanos;
    }

    synchronized void record(Channel channel, RecordedEvent event) {
      double durationMs = channel.duration ? event.getDuration().toNanos() / 1_000_000D : 0D;
      long bytes = switch (channel) {
        case READ, WRITE -> event.getLong("bytes");
        case NETWORK -> event.getLong("sentBytes");
        case FILE_READ -> event.getLong("bytesRead");
        case FILE_WRITE -> event.getLong("bytesWritten");
        case LOSS -> event.getLong("amount");
        default -> 0L;
      };
      long count = channel == Channel.NETWORK ? event.getLong("sentPackets") : 1L;
      record(channel, count, bytes, durationMs);
      delayMs = Math.max(delayMs, Math.max(0L, System.currentTimeMillis() - event.getEndTime().toEpochMilli()));
      if (channel == Channel.NETWORK) {
        receivedPackets += Math.max(0L, event.getLong("receivedPackets"));
        receivedBytes += Math.max(0L, event.getLong("receivedBytes"));
      }
      if (channel == Channel.GENERATION) {
        recordSeries(channel, event.getString("level"), event.getString("targetStatus"), bytes, durationMs);
      } else if (channel == Channel.READ || channel == Channel.WRITE) {
        recordSeries(channel, event.getString("levelId"), event.getString("type"), bytes, durationMs);
      }
    }

    synchronized void record(Channel channel, long count, long bytes, double durationMs) {
      totals.computeIfAbsent(channel, ignored -> new Totals()).add(count, bytes, durationMs);
    }

    synchronized void recordSeries(Channel channel, String world, String category, long bytes, double durationMs) {
      SeriesKey key = new SeriesKey(channel, bounded(world), bounded(category));
      if (!series.containsKey(key) && series.size() >= MAX_SERIES - 3) {
        key = new SeriesKey(channel, "other", "other");
      }
      series.computeIfAbsent(key, ignored -> new Totals()).add(1L, bytes, durationMs);
    }

    synchronized Snapshot publish(Set<String> supported, boolean available, long nowMs, long nowNanos) {
      long elapsedNanos = Math.max(1L, nowNanos - previousNanos);
      double seconds = elapsedNanos / 1_000_000_000D;
      Map<String, RuntimeMetric> metrics = new LinkedHashMap<>();
      boolean dataLoss = totals.getOrDefault(Channel.LOSS, Totals.EMPTY).count > 0L;
      for (Channel channel : Channel.values()) {
        boolean channelAvailable = available && supported.contains(channel.eventName)
            && (!dataLoss || channel == Channel.LOSS);
        Totals value = totals.getOrDefault(channel, Totals.EMPTY);
        put(metrics, channel.id + "-rate", value.count / seconds, nowMs, channelAvailable);
        if (channel.duration) {
          put(metrics, channel.id + "-mean-ms", value.mean(), nowMs, channelAvailable && value.count > 0L);
          put(metrics, channel.id + "-max-ms", value.maxMs, nowMs, channelAvailable && value.count > 0L);
          if (channelAvailable && value.count == 0L) {
            metrics.put(channel.id + "-mean-ms", new RuntimeMetric(0D, nowMs, false, "no-observation"));
            metrics.put(channel.id + "-max-ms", new RuntimeMetric(0D, nowMs, false, "no-observation"));
          }
        }
        if (channel.bytes) {
          put(metrics, channel.id + "-bytes-rate", value.bytes / seconds, nowMs, channelAvailable);
        }
      }
      boolean network = available && !dataLoss && supported.contains(Channel.NETWORK.eventName);
      put(metrics, "jfr-network-received-packets-rate", receivedPackets / seconds, nowMs, network);
      put(metrics, "jfr-network-received-bytes-rate", receivedBytes / seconds, nowMs, network);
      put(metrics, "jfr-delivery-delay-ms", delayMs, nowMs, available);
      if (dataLoss) {
        metrics.replaceAll((id, value) -> id.startsWith("jfr-data-loss") || id.equals("jfr-delivery-delay-ms") ? value
            : new RuntimeMetric(value.value(), nowMs, false, "recording-data-loss"));
      }
      List<Series> details = new ArrayList<>(series.size());
      for (Map.Entry<SeriesKey, Totals> entry : series.entrySet()) {
        SeriesKey key = entry.getKey();
        Totals value = entry.getValue();
        details.add(new Series(key.channel.id, key.world, key.category, value.count, value.bytes, value.mean(), value.maxMs));
      }
      totals.clear();
      series.clear();
      receivedPackets = 0L;
      receivedBytes = 0L;
      delayMs = 0L;
      previousNanos = nowNanos;
      return new Snapshot(nowMs, Math.max(1L, elapsedNanos / 1_000_000L), thresholdMs, available,
          available && !dataLoss, supported, Map.copyOf(metrics), List.copyOf(details));
    }

    private static void put(Map<String, RuntimeMetric> metrics, String id, double value, long nowMs, boolean available) {
      metrics.put(id, new RuntimeMetric(value, nowMs, available, available ? "" : "source-unavailable"));
    }

    private static String bounded(String value) {
      if (value == null || value.isBlank()) {
        return "unknown";
      }
      return value.substring(0, Math.min(value.length(), 128));
    }
  }

  private record SeriesKey(Channel channel, String world, String category) {
  }

  private static final class Totals {
    private static final Totals EMPTY = new Totals();
    private long count;
    private long bytes;
    private double durationMs;
    private double maxMs;

    private void add(long count, long bytes, double durationMs) {
      this.count += Math.max(0L, count);
      this.bytes += Math.max(0L, bytes);
      if (Double.isFinite(durationMs) && durationMs >= 0D) {
        this.durationMs += durationMs;
        maxMs = Math.max(maxMs, durationMs);
      }
    }

    private double mean() {
      return count == 0L ? 0D : durationMs / count;
    }
  }
}
