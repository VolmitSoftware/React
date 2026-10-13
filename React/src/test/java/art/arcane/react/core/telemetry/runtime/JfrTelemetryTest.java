package art.arcane.react.core.telemetry.runtime;

import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

class JfrTelemetryTest {
  @TempDir
  Path directory;

  @Test
  void ratesUseElapsedTimeAndEmptyDurationWindowsRemainUnavailable() {
    JfrTelemetry.Accumulator accumulator = new JfrTelemetry.Accumulator(10L, 0L);
    accumulator.record(JfrTelemetry.Channel.GENERATION, 2L, 0L, 12D);
    accumulator.record(JfrTelemetry.Channel.READ, 3L, 6_000L, 0D);
    Set<String> supported = Set.of("minecraft.ChunkGeneration", "minecraft.ChunkRegionRead");
    JfrTelemetry.Snapshot snapshot = accumulator.publish(supported, true, 2_000L, 2_000_000_000L);
    Assertions.assertEquals(1D, snapshot.metrics().get("jfr-chunk-generation-rate").value());
    Assertions.assertEquals(6D, snapshot.metrics().get("jfr-chunk-generation-mean-ms").value());
    Assertions.assertEquals(3_000D, snapshot.metrics().get("jfr-region-read-bytes-rate").value());
    Assertions.assertFalse(snapshot.metrics().get("jfr-network-sent-rate").available());
    snapshot = accumulator.publish(supported, true, 3_000L, 3_000_000_000L);
    Assertions.assertTrue(snapshot.metrics().get("jfr-chunk-generation-rate").available());
    Assertions.assertEquals(0D, snapshot.metrics().get("jfr-chunk-generation-rate").value());
    Assertions.assertFalse(snapshot.metrics().get("jfr-chunk-generation-mean-ms").available());
  }

  @Test
  void dimensionOverflowIsBoundedWithoutLosingAggregateCounts() {
    JfrTelemetry.Accumulator accumulator = new JfrTelemetry.Accumulator(10L, 0L);
    for (int index = 0; index < 1_000; index++) {
      accumulator.recordSeries(JfrTelemetry.Channel.GENERATION, "level-" + index, "noise", 0L, 2D);
      accumulator.recordSeries(JfrTelemetry.Channel.READ, "level-" + index, "chunk", 10L, 0D);
      accumulator.recordSeries(JfrTelemetry.Channel.WRITE, "level-" + index, "chunk", 10L, 0D);
    }
    JfrTelemetry.Snapshot snapshot = accumulator.publish(Set.of(), true, 1_000L, 1_000_000_000L);
    Assertions.assertTrue(snapshot.series().size() <= 64);
    Assertions.assertEquals(3_000L, snapshot.series().stream().mapToLong(JfrTelemetry.Series::count).sum());
    Assertions.assertTrue(accumulator.publish(Set.of(), true, 2_000L, 2_000_000_000L).series().isEmpty());
  }

  @Test
  void recordingDataLossInvalidatesTheAffectedWindow() {
    JfrTelemetry.Accumulator accumulator = new JfrTelemetry.Accumulator(10L, 0L);
    accumulator.record(JfrTelemetry.Channel.GENERATION, 1L, 0L, 12D);
    accumulator.record(JfrTelemetry.Channel.LOSS, 1L, 256L, 0D);
    JfrTelemetry.Snapshot snapshot = accumulator.publish(Set.of("minecraft.ChunkGeneration", "jdk.DataLoss"), true, 1_000L, 1_000_000_000L);
    Assertions.assertFalse(snapshot.complete());
    Assertions.assertFalse(snapshot.metrics().get("jfr-chunk-generation-rate").available());
    Assertions.assertEquals("recording-data-loss", snapshot.metrics().get("jfr-chunk-generation-rate").unavailableReason());
    Assertions.assertTrue(snapshot.metrics().get("jfr-data-loss-bytes-rate").available());
    Assertions.assertEquals(256D, snapshot.metrics().get("jfr-data-loss-bytes-rate").value());
  }

  @Test
  void eventSchemaRejectsMissingAndWrongFieldTypes() {
    EventType valid = EventType.getEventType(ProtocolSummary.class);
    EventType invalid = EventType.getEventType(InvalidSummary.class);
    Assertions.assertTrue(JfrTelemetry.hasFields(valid, List.of("sentBytes", "receivedPackets")));
    Assertions.assertFalse(JfrTelemetry.hasFields(valid, List.of("missing")));
    Assertions.assertFalse(JfrTelemetry.hasFields(invalid, List.of("sentBytes")));
    Assertions.assertFalse(JfrTelemetry.hasFields(null, List.of("sentBytes")));
  }

  @Test
  void recordedNetworkEventsPublishTotalsWithoutAddressData() throws Exception {
    Path path = directory.resolve("network.jfr");
    try (Recording recording = new Recording()) {
      recording.enable(ProtocolSummary.class).withoutStackTrace();
      recording.start();
      ProtocolSummary event = new ProtocolSummary();
      event.remoteAddress = "private.invalid:25565";
      event.sentBytes = 600L;
      event.sentPackets = 3;
      event.receivedBytes = 400L;
      event.receivedPackets = 2;
      event.commit();
      recording.stop();
      recording.dump(path);
    }
    JfrTelemetry.Accumulator accumulator = new JfrTelemetry.Accumulator(10L, 0L);
    for (RecordedEvent event : RecordingFile.readAllEvents(path)) {
      accumulator.record(JfrTelemetry.Channel.NETWORK, event);
    }
    JfrTelemetry.Snapshot snapshot = accumulator.publish(Set.of("minecraft.NetworkSummary"), true, 1_000L, 1_000_000_000L);
    Assertions.assertEquals(3D, snapshot.metrics().get("jfr-network-sent-rate").value());
    Assertions.assertEquals(600D, snapshot.metrics().get("jfr-network-sent-bytes-rate").value());
    Assertions.assertEquals(2D, snapshot.metrics().get("jfr-network-received-packets-rate").value());
    Assertions.assertEquals(400D, snapshot.metrics().get("jfr-network-received-bytes-rate").value());
    Assertions.assertFalse(snapshot.toString().contains("private.invalid"));
    Assertions.assertTrue(snapshot.series().isEmpty());
  }

  @Test
  void recordedChunkEventsUseAnnotatedSchemaNames() throws Exception {
    Path path = directory.resolve("chunks.jfr");
    try (Recording recording = new Recording()) {
      recording.enable(ChunkStage.class).withoutStackTrace();
      recording.enable(ChunkRead.class).withoutStackTrace();
      recording.start();
      ChunkStage generation = new ChunkStage();
      generation.targetStatus = "noise";
      generation.level = "minecraft:overworld";
      generation.begin();
      generation.end();
      generation.commit();
      ChunkRead read = new ChunkRead();
      read.levelId = "world";
      read.type = "chunk";
      read.bytes = 256;
      read.commit();
      recording.stop();
      recording.dump(path);
    }
    JfrTelemetry.Accumulator accumulator = new JfrTelemetry.Accumulator(10L, 0L);
    for (RecordedEvent event : RecordingFile.readAllEvents(path)) {
      boolean generation = event.getEventType().getName().equals("minecraft.ChunkGeneration");
      Assertions.assertTrue(JfrTelemetry.hasFields(event.getEventType(), generation
          ? List.of("level", "status", "duration") : List.of("level", "type", "bytes")));
      accumulator.record(generation ? JfrTelemetry.Channel.GENERATION : JfrTelemetry.Channel.READ, event);
    }
    JfrTelemetry.Snapshot snapshot = accumulator.publish(Set.of("minecraft.ChunkGeneration", "minecraft.ChunkRegionRead"), true, 1_000L, 1_000_000_000L);
    Assertions.assertEquals(1D, snapshot.metrics().get("jfr-chunk-generation-rate").value());
    Assertions.assertEquals(256D, snapshot.metrics().get("jfr-region-read-bytes-rate").value());
    Assertions.assertTrue(snapshot.series().stream().anyMatch(series -> series.level().equals("minecraft:overworld") && series.category().equals("noise")));
    Assertions.assertTrue(snapshot.series().stream().anyMatch(series -> series.level().equals("world") && series.category().equals("chunk")));
  }

  @Test
  void streamStartsWithSupportedSchemasAndClosesWithoutStaleAvailability() {
    EventType.getEventType(ProtocolSummary.class);
    EventType.getEventType(ChunkStage.class);
    EventType.getEventType(ChunkRead.class);
    JfrTelemetry telemetry = JfrTelemetry.start(new JfrTelemetry.Options(10L), failure -> Assertions.fail(failure));
    try {
      JfrTelemetry.Snapshot snapshot = telemetry.snapshot(System.currentTimeMillis());
      Assertions.assertTrue(snapshot.available());
      Assertions.assertTrue(snapshot.supportedEvents().contains("minecraft.NetworkSummary"));
      Assertions.assertTrue(snapshot.supportedEvents().contains("minecraft.ChunkGeneration"));
      Assertions.assertTrue(snapshot.supportedEvents().contains("minecraft.ChunkRegionRead"));
      Assertions.assertTrue(snapshot.supportedEvents().contains("jdk.ThreadPark"));
    } finally {
      telemetry.close();
    }
    Assertions.assertFalse(telemetry.snapshot(System.currentTimeMillis()).available());
  }

  @Name("minecraft.NetworkSummary")
  static class ProtocolSummary extends Event {
    String remoteAddress;
    long sentBytes;
    int sentPackets;
    long receivedBytes;
    int receivedPackets;
  }

  static class InvalidSummary extends Event {
    String sentBytes;
  }

  @Name("minecraft.ChunkGeneration")
  static class ChunkStage extends Event {
    @Name("status")
    String targetStatus;
    String level;
  }

  @Name("minecraft.ChunkRegionRead")
  static class ChunkRead extends Event {
    @Name("level")
    String levelId;
    String type;
    int bytes;
  }
}
