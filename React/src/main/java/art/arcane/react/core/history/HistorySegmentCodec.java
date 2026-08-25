package art.arcane.react.core.history;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32C;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

final class HistorySegmentCodec {
  private static final int MAGIC = 0x52485453;
  private static final int SERIES_MAGIC = 0x53455249;
  private static final int FOOTER_MAGIC = 0x454E4421;
  private static final int VERSION = 1;
  private static final int MAX_SERIES = 4_096;
  private static final int MAX_BUCKETS = 16_384;
  private static final int MAX_STRING_BYTES = 16_384;
  private static final int MAX_PAYLOAD_BYTES = 256 * 1024 * 1024;

  private HistorySegmentCodec() {
  }

  static void write(Path target, HistorySegment segment, int compressionLevel) throws IOException {
    Files.createDirectories(target.getParent());
    Path temporary = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
    try {
      try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(
          temporary,
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE
      )))) {
        output.writeInt(MAGIC);
        output.writeShort(VERSION);
        output.writeByte(segment.tier().id());
        output.writeLong(segment.startMs());
        output.writeInt(segment.bucketCount());
        List<HistorySeries> series = new ArrayList<>(segment.series());
        series.sort(Comparator.comparing(HistorySeries::id));
        output.writeInt(series.size());
        for (HistorySeries value : series) {
          writeSeries(output, value, compressionLevel);
        }
        output.writeInt(FOOTER_MAGIC);
      }
      try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        channel.force(true);
      }
      try {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  static SegmentHeader readHeader(Path source) throws IOException {
    try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(source)))) {
      requireMagic(input.readInt(), MAGIC, "history segment");
      int version = input.readUnsignedShort();
      if (version != VERSION) {
        throw new IOException("Unsupported history segment version " + version);
      }
      HistoryTier tier = HistoryTier.byId(input.readUnsignedByte());
      long startMs = input.readLong();
      int bucketCount = checkedCount(input.readInt(), MAX_BUCKETS, "bucket");
      int seriesCount = checkedCount(input.readInt(), MAX_SERIES, "series");
      return new SegmentHeader(tier, startMs, bucketCount, seriesCount);
    }
  }

  static HistorySegment read(Path source, Set<String> requestedIds) throws IOException {
    try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(source)))) {
      requireMagic(input.readInt(), MAGIC, "history segment");
      int version = input.readUnsignedShort();
      if (version != VERSION) {
        throw new IOException("Unsupported history segment version " + version);
      }
      HistoryTier tier = HistoryTier.byId(input.readUnsignedByte());
      long startMs = input.readLong();
      int bucketCount = checkedCount(input.readInt(), MAX_BUCKETS, "bucket");
      int seriesCount = checkedCount(input.readInt(), MAX_SERIES, "series");
      HistorySegment segment = new HistorySegment(tier, startMs, bucketCount);
      for (int index = 0; index < seriesCount; index++) {
        requireMagic(input.readInt(), SERIES_MAGIC, "history series");
        String id = readString(input);
        String name = readString(input);
        String suffix = readString(input);
        input.readInt();
        input.readInt();
        boolean compressed = input.readBoolean();
        int rawLength = checkedLength(input.readInt(), "raw series payload");
        int storedLength = checkedLength(input.readInt(), "stored series payload");
        int checksum = input.readInt();
        if (requestedIds != null && !requestedIds.contains(id)) {
          input.skipNBytes(storedLength);
          continue;
        }
        byte[] stored = input.readNBytes(storedLength);
        if (stored.length != storedLength) {
          throw new EOFException("History series payload is truncated");
        }
        byte[] raw = compressed ? inflate(stored, rawLength) : stored;
        if (raw.length != rawLength) {
          throw new IOException("History series payload length mismatch");
        }
        CRC32C crc = new CRC32C();
        crc.update(raw, 0, raw.length);
        if ((int) crc.getValue() != checksum) {
          throw new IOException("History series checksum mismatch for " + id);
        }
        segment.add(decodeSeries(id, name, suffix, bucketCount, raw));
      }
      requireMagic(input.readInt(), FOOTER_MAGIC, "history segment footer");
      if (input.read() != -1) {
        throw new IOException("History segment contains trailing bytes");
      }
      return segment;
    }
  }

  private static void writeSeries(DataOutputStream output, HistorySeries series, int compressionLevel) throws IOException {
    byte[] raw = encodeSeries(series);
    byte[] compressed = deflate(raw, compressionLevel);
    boolean useCompressed = compressed.length + 16 < raw.length;
    byte[] stored = useCompressed ? compressed : raw;
    CRC32C crc = new CRC32C();
    crc.update(raw, 0, raw.length);

    output.writeInt(SERIES_MAGIC);
    writeString(output, series.id());
    writeString(output, series.name());
    writeString(output, series.suffix());
    int firstIndex = firstPresentIndex(series.countValues());
    int lastIndex = lastPresentIndex(series.countValues());
    output.writeInt(firstIndex);
    output.writeInt(lastIndex);
    output.writeBoolean(useCompressed);
    output.writeInt(raw.length);
    output.writeInt(stored.length);
    output.writeInt((int) crc.getValue());
    output.write(stored);
  }

  private static byte[] encodeSeries(HistorySeries series) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream(bytes);
    long[] counts = series.countValues();
    output.writeInt(counts.length);
    for (long count : counts) {
      DoubleColumnCodec.writeUnsignedVarLong(output, count);
    }
    writeColumn(output, DoubleColumnCodec.encode(series.firstValues(), counts));
    writeColumn(output, DoubleColumnCodec.encode(series.minimumValues(), counts));
    writeColumn(output, DoubleColumnCodec.encode(series.maximumValues(), counts));
    writeColumn(output, DoubleColumnCodec.encode(series.sums(), counts));
    writeColumn(output, DoubleColumnCodec.encode(series.lastValues(), counts));
    output.flush();
    return bytes.toByteArray();
  }

  private static HistorySeries decodeSeries(
      String id,
      String name,
      String suffix,
      int expectedBuckets,
      byte[] raw
  ) throws IOException {
    DataInputStream input = new DataInputStream(new ByteArrayInputStream(raw));
    int bucketCount = checkedCount(input.readInt(), MAX_BUCKETS, "series bucket");
    if (bucketCount != expectedBuckets) {
      throw new IOException("History series bucket count does not match its segment");
    }
    long[] counts = new long[bucketCount];
    for (int index = 0; index < bucketCount; index++) {
      counts[index] = DoubleColumnCodec.readUnsignedVarLong(input);
    }
    double[] first = DoubleColumnCodec.decode(readColumn(input), counts);
    double[] minimum = DoubleColumnCodec.decode(readColumn(input), counts);
    double[] maximum = DoubleColumnCodec.decode(readColumn(input), counts);
    double[] sums = DoubleColumnCodec.decode(readColumn(input), counts);
    double[] last = DoubleColumnCodec.decode(readColumn(input), counts);
    if (input.available() != 0) {
      throw new IOException("History series contains trailing bytes");
    }
    HistorySeries series = new HistorySeries(id, name, suffix, bucketCount);
    System.arraycopy(first, 0, series.firstValues(), 0, bucketCount);
    System.arraycopy(minimum, 0, series.minimumValues(), 0, bucketCount);
    System.arraycopy(maximum, 0, series.maximumValues(), 0, bucketCount);
    System.arraycopy(sums, 0, series.sums(), 0, bucketCount);
    System.arraycopy(last, 0, series.lastValues(), 0, bucketCount);
    System.arraycopy(counts, 0, series.countValues(), 0, bucketCount);
    return series;
  }

  private static void writeColumn(DataOutputStream output, DoubleColumnCodec.EncodedColumn column) throws IOException {
    output.writeByte(column.codec());
    output.writeInt(column.data().length);
    output.write(column.data());
  }

  private static DoubleColumnCodec.EncodedColumn readColumn(DataInputStream input) throws IOException {
    int codec = input.readUnsignedByte();
    int length = checkedLength(input.readInt(), "history column");
    byte[] data = input.readNBytes(length);
    if (data.length != length) {
      throw new EOFException("History column is truncated");
    }
    return new DoubleColumnCodec.EncodedColumn(codec, data);
  }

  private static byte[] deflate(byte[] raw, int compressionLevel) throws IOException {
    Deflater deflater = new Deflater(Math.max(0, Math.min(9, compressionLevel)));
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(raw.length);
      try (DeflaterOutputStream output = new DeflaterOutputStream(bytes, deflater)) {
        output.write(raw);
      }
      return bytes.toByteArray();
    } finally {
      deflater.end();
    }
  }

  private static byte[] inflate(byte[] compressed, int expectedLength) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(expectedLength);
    try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
      byte[] buffer = new byte[8_192];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        if (bytes.size() + read > MAX_PAYLOAD_BYTES) {
          throw new IOException("Inflated history payload exceeds the safety limit");
        }
        bytes.write(buffer, 0, read);
      }
    }
    if (bytes.size() != expectedLength) {
      throw new IOException("Inflated history payload length does not match its header");
    }
    return bytes.toByteArray();
  }

  private static void writeString(DataOutputStream output, String value) throws IOException {
    byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_STRING_BYTES) {
      throw new IOException("History string exceeds " + MAX_STRING_BYTES + " bytes");
    }
    output.writeInt(bytes.length);
    output.write(bytes);
  }

  private static String readString(DataInputStream input) throws IOException {
    int length = input.readInt();
    if (length < 0 || length > MAX_STRING_BYTES) {
      throw new IOException("Invalid history string length " + length);
    }
    byte[] bytes = input.readNBytes(length);
    if (bytes.length != length) {
      throw new EOFException("History string is truncated");
    }
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static int checkedCount(int value, int maximum, String label) throws IOException {
    if (value < 0 || value > maximum) {
      throw new IOException("Invalid history " + label + " count " + value);
    }
    return value;
  }

  private static int checkedLength(int value, String label) throws IOException {
    if (value < 0 || value > MAX_PAYLOAD_BYTES) {
      throw new IOException("Invalid " + label + " length " + value);
    }
    return value;
  }

  private static void requireMagic(int actual, int expected, String label) throws IOException {
    if (actual != expected) {
      throw new IOException("Invalid " + label + " magic");
    }
  }

  static SegmentCatalog readCatalog(Path source) throws IOException {
    try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(source)))) {
      requireMagic(input.readInt(), MAGIC, "history segment");
      int version = input.readUnsignedShort();
      if (version != VERSION) {
        throw new IOException("Unsupported history segment version " + version);
      }
      HistoryTier tier = HistoryTier.byId(input.readUnsignedByte());
      long startMs = input.readLong();
      int bucketCount = checkedCount(input.readInt(), MAX_BUCKETS, "bucket");
      int seriesCount = checkedCount(input.readInt(), MAX_SERIES, "series");
      List<SegmentSeriesDescriptor> descriptors = new ArrayList<>(seriesCount);
      for (int index = 0; index < seriesCount; index++) {
        requireMagic(input.readInt(), SERIES_MAGIC, "history series");
        String id = readString(input);
        String name = readString(input);
        String suffix = readString(input);
        int firstIndex = input.readInt();
        int lastIndex = input.readInt();
        input.readBoolean();
        checkedLength(input.readInt(), "raw series payload");
        int storedLength = checkedLength(input.readInt(), "stored series payload");
        input.readInt();
        input.skipNBytes(storedLength);
        long firstTimestampMs = firstIndex < 0 ? 0L : startMs + (tier.intervalMs() * firstIndex);
        long lastTimestampMs = lastIndex < 0 ? 0L : startMs + (tier.intervalMs() * lastIndex);
        descriptors.add(new SegmentSeriesDescriptor(id, name, suffix, firstTimestampMs, lastTimestampMs));
      }
      requireMagic(input.readInt(), FOOTER_MAGIC, "history segment footer");
      return new SegmentCatalog(tier, startMs, bucketCount, List.copyOf(descriptors));
    }
  }

  private static int firstPresentIndex(long[] counts) {
    for (int index = 0; index < counts.length; index++) {
      if (counts[index] > 0L) {
        return index;
      }
    }
    return -1;
  }

  private static int lastPresentIndex(long[] counts) {
    for (int index = counts.length - 1; index >= 0; index--) {
      if (counts[index] > 0L) {
        return index;
      }
    }
    return -1;
  }

  record SegmentHeader(HistoryTier tier, long startMs, int bucketCount, int seriesCount) {
    long endMs() {
      return startMs + (tier.intervalMs() * bucketCount);
    }
  }

  record SegmentSeriesDescriptor(
      String id,
      String name,
      String suffix,
      long firstTimestampMs,
      long lastTimestampMs
  ) {
  }

  record SegmentCatalog(
      HistoryTier tier,
      long startMs,
      int bucketCount,
      List<SegmentSeriesDescriptor> series
  ) {
  }
}
