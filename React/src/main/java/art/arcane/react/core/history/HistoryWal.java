package art.arcane.react.core.history;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32C;

public final class HistoryWal implements AutoCloseable {
  private static final int MAGIC = 0x5248574C;
  private static final int VERSION = 1;
  private static final int HEADER_BYTES = Integer.BYTES + Short.BYTES;
  private static final int DICTIONARY_FRAME = 1;
  private static final int SAMPLE_FRAME = 2;
  private static final int MAX_FRAME_BYTES = 32 * 1024 * 1024;
  private static final int MAX_STRING_BYTES = 16_384;

  private final Path path;
  private final Map<String, Integer> indexById;
  private final Map<Integer, WalMetric> metricByIndex;
  private FileChannel channel;
  private int nextIndex;

  public HistoryWal(Path path) {
    this.path = path;
    this.indexById = new HashMap<>();
    this.metricByIndex = new HashMap<>();
  }

  public synchronized WalRecovery open() throws IOException {
    Files.createDirectories(path.getParent());
    WalRecovery recovery = recover();
    channel = FileChannel.open(
        path,
        StandardOpenOption.CREATE,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE
    );
    if (channel.size() == 0L) {
      writeHeader(channel);
      channel.force(true);
    }
    channel.position(channel.size());
    indexById.clear();
    metricByIndex.clear();
    for (WalMetric metric : recovery.metrics().values()) {
      indexById.put(metric.id(), metric.index());
      metricByIndex.put(metric.index(), metric);
      nextIndex = Math.max(nextIndex, metric.index() + 1);
    }
    return recovery;
  }

  public synchronized void append(MetricSnapshot snapshot) throws IOException {
    requireOpen();
    List<WalValue> values = new ArrayList<>(snapshot.values().size());
    for (MetricSnapshotValue value : snapshot.values()) {
      if (!value.available() || !Double.isFinite(value.value())) {
        continue;
      }
      Integer existing = indexById.get(value.id());
      int index;
      if (existing == null) {
        index = nextIndex++;
        WalMetric metric = new WalMetric(index, value.id(), value.name(), value.suffix());
        writeFrame(dictionaryPayload(metric));
        indexById.put(value.id(), index);
        metricByIndex.put(index, metric);
      } else {
        index = existing;
        WalMetric prior = metricByIndex.get(index);
        if (prior != null && (!prior.name().equals(value.name()) || !prior.suffix().equals(value.suffix()))) {
          WalMetric replacement = new WalMetric(index, value.id(), value.name(), value.suffix());
          writeFrame(dictionaryPayload(replacement));
          metricByIndex.put(index, replacement);
        }
      }
      values.add(new WalValue(index, value.value()));
    }
    writeFrame(samplePayload(snapshot.sequence(), snapshot.capturedAtMs(), values));
  }

  public synchronized void force() throws IOException {
    requireOpen();
    channel.force(false);
  }

  public synchronized void reset() throws IOException {
    requireOpen();
    channel.truncate(0L);
    channel.position(0L);
    writeHeader(channel);
    channel.force(true);
    channel.position(channel.size());
    indexById.clear();
    metricByIndex.clear();
    nextIndex = 0;
  }

  @Override
  public synchronized void close() throws IOException {
    if (channel == null) {
      return;
    }
    try {
      channel.force(false);
    } finally {
      channel.close();
      channel = null;
    }
  }

  private WalRecovery recover() throws IOException {
    if (!Files.exists(path) || Files.size(path) == 0L) {
      return new WalRecovery(Map.of(), List.of(), false);
    }
    Map<Integer, WalMetric> metrics = new LinkedHashMap<>();
    List<WalSample> samples = new ArrayList<>();
    long validBytes = HEADER_BYTES;
    boolean truncated = false;
    try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
      if (input.readInt() != MAGIC || input.readUnsignedShort() != VERSION) {
        throw new IOException("Invalid React history WAL header");
      }
      while (true) {
        int frameLength;
        try {
          frameLength = input.readInt();
        } catch (EOFException end) {
          break;
        }
        if (frameLength <= 0 || frameLength > MAX_FRAME_BYTES) {
          truncated = true;
          break;
        }
        byte[] payload = input.readNBytes(frameLength);
        if (payload.length != frameLength) {
          truncated = true;
          break;
        }
        int expectedChecksum;
        try {
          expectedChecksum = input.readInt();
        } catch (EOFException end) {
          truncated = true;
          break;
        }
        CRC32C crc = new CRC32C();
        crc.update(payload, 0, payload.length);
        if ((int) crc.getValue() != expectedChecksum) {
          truncated = true;
          break;
        }
        decodeFrame(payload, metrics, samples);
        validBytes += Integer.BYTES + frameLength + Integer.BYTES;
      }
    }
    if (truncated || validBytes < Files.size(path)) {
      try (FileChannel recoveryChannel = FileChannel.open(path, StandardOpenOption.WRITE)) {
        recoveryChannel.truncate(validBytes);
        recoveryChannel.force(true);
      }
      truncated = true;
    }
    return new WalRecovery(Map.copyOf(metrics), List.copyOf(samples), truncated);
  }

  private void writeFrame(byte[] payload) throws IOException {
    CRC32C crc = new CRC32C();
    crc.update(payload, 0, payload.length);
    ByteBuffer header = ByteBuffer.allocate(Integer.BYTES);
    header.putInt(payload.length).flip();
    writeFully(channel, header);
    writeFully(channel, ByteBuffer.wrap(payload));
    ByteBuffer footer = ByteBuffer.allocate(Integer.BYTES);
    footer.putInt((int) crc.getValue()).flip();
    writeFully(channel, footer);
  }

  private static byte[] dictionaryPayload(WalMetric metric) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream(bytes);
    output.writeByte(DICTIONARY_FRAME);
    output.writeInt(metric.index());
    writeString(output, metric.id());
    writeString(output, metric.name());
    writeString(output, metric.suffix());
    output.flush();
    return bytes.toByteArray();
  }

  private static byte[] samplePayload(long sequence, long timestampMs, List<WalValue> values) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(32, values.size() * 12));
    DataOutputStream output = new DataOutputStream(bytes);
    output.writeByte(SAMPLE_FRAME);
    output.writeLong(sequence);
    output.writeLong(timestampMs);
    output.writeInt(values.size());
    for (WalValue value : values) {
      output.writeInt(value.index());
      output.writeLong(Double.doubleToRawLongBits(value.value()));
    }
    output.flush();
    return bytes.toByteArray();
  }

  private static void decodeFrame(
      byte[] payload,
      Map<Integer, WalMetric> metrics,
      List<WalSample> samples
  ) throws IOException {
    DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
    int type = input.readUnsignedByte();
    if (type == DICTIONARY_FRAME) {
      int index = input.readInt();
      if (index < 0) {
        throw new IOException("Negative metric index in React history WAL");
      }
      WalMetric metric = new WalMetric(index, readString(input), readString(input), readString(input));
      metrics.put(index, metric);
    } else if (type == SAMPLE_FRAME) {
      long sequence = input.readLong();
      long timestampMs = input.readLong();
      int count = input.readInt();
      if (count < 0 || count > 4_096) {
        throw new IOException("Invalid metric count in React history WAL: " + count);
      }
      List<WalValue> values = new ArrayList<>(count);
      for (int index = 0; index < count; index++) {
        int metricIndex = input.readInt();
        if (!metrics.containsKey(metricIndex)) {
          throw new IOException("React history WAL sample references an unknown metric index " + metricIndex);
        }
        values.add(new WalValue(metricIndex, Double.longBitsToDouble(input.readLong())));
      }
      samples.add(new WalSample(sequence, timestampMs, List.copyOf(values)));
    } else {
      throw new IOException("Unknown React history WAL frame type " + type);
    }
    if (input.available() != 0) {
      throw new IOException("React history WAL frame contains trailing bytes");
    }
  }

  private static void writeHeader(FileChannel channel) throws IOException {
    ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
    header.putInt(MAGIC);
    header.putShort((short) VERSION);
    header.flip();
    writeFully(channel, header);
  }

  private static void writeFully(FileChannel channel, ByteBuffer bytes) throws IOException {
    while (bytes.hasRemaining()) {
      channel.write(bytes);
    }
  }

  private static void writeString(DataOutputStream output, String value) throws IOException {
    byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_STRING_BYTES) {
      throw new IOException("React history WAL string exceeds " + MAX_STRING_BYTES + " bytes");
    }
    output.writeInt(bytes.length);
    output.write(bytes);
  }

  private static String readString(DataInputStream input) throws IOException {
    int length = input.readInt();
    if (length < 0 || length > MAX_STRING_BYTES) {
      throw new IOException("Invalid React history WAL string length " + length);
    }
    byte[] bytes = input.readNBytes(length);
    if (bytes.length != length) {
      throw new EOFException("React history WAL string is truncated");
    }
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private void requireOpen() throws IOException {
    if (channel == null || !channel.isOpen()) {
      throw new IOException("React history WAL is not open");
    }
  }

  public record WalMetric(int index, String id, String name, String suffix) {
  }

  public record WalValue(int index, double value) {
  }

  public record WalSample(long sequence, long timestampMs, List<WalValue> values) {
  }

  public record WalRecovery(Map<Integer, WalMetric> metrics, List<WalSample> samples, boolean truncated) {
  }
}
