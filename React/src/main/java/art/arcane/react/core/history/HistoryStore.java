package art.arcane.react.core.history;

import art.arcane.react.React;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

public final class HistoryStore {
  private final Path root;
  private final EnumMap<HistoryTier, ConcurrentSkipListMap<Long, Path>> filesByTier;
  private final ConcurrentHashMap<String, MetricDescriptor> descriptors;
  private volatile int compressionLevel;

  public HistoryStore(Path root, int compressionLevel) {
    this.root = root;
    this.compressionLevel = compressionLevel;
    this.filesByTier = new EnumMap<>(HistoryTier.class);
    this.descriptors = new ConcurrentHashMap<>();
    for (HistoryTier tier : HistoryTier.values()) {
      filesByTier.put(tier, new ConcurrentSkipListMap<>());
    }
  }

  public void compressionLevel(int compressionLevel) {
    this.compressionLevel = compressionLevel;
  }

  public void initialize() throws IOException {
    Files.createDirectories(root);
    for (HistoryTier tier : HistoryTier.values()) {
      Path directory = directory(tier);
      Files.createDirectories(directory);
      ConcurrentSkipListMap<Long, Path> files = filesByTier.get(tier);
      files.clear();
      try (Stream<Path> listed = Files.list(directory)) {
        List<Path> candidates = listed
            .filter(path -> path.getFileName().toString().endsWith(".rht"))
            .sorted()
            .toList();
        for (Path candidate : candidates) {
          try {
            HistorySegmentCodec.SegmentHeader header = HistorySegmentCodec.readHeader(candidate);
            if (header.tier() != tier) {
              throw new IOException("History segment tier does not match its directory: " + candidate);
            }
            files.put(header.startMs(), candidate);
          } catch (Throwable failure) {
            React.warn("Ignoring unreadable React history segment " + candidate + ": " + failure.getMessage(), failure);
          }
        }
      }
    }
    rebuildDescriptors();
  }

  public Path walPath() {
    return root.resolve("active.wal");
  }

  public boolean contains(HistoryTier tier, long startMs) {
    return filesByTier.get(tier).containsKey(startMs);
  }

  public void write(HistorySegment segment) throws IOException {
    Path target = segmentPath(segment.tier(), segment.startMs());
    HistorySegmentCodec.write(target, segment, compressionLevel);
    filesByTier.get(segment.tier()).put(segment.startMs(), target);
    mergeDescriptors(segment, false);
  }

  public HistorySegment read(HistoryTier tier, long startMs, Set<String> ids) throws IOException {
    Path path = filesByTier.get(tier).get(startMs);
    return path == null ? null : HistorySegmentCodec.read(path, ids);
  }

  Map<String, List<HistoryPoint>> points(
      HistoryTier tier,
      Set<String> ids,
      long fromMs,
      long toMs
  ) throws IOException {
    Map<String, List<HistoryPoint>> points = new HashMap<>();
    if (ids.isEmpty() || toMs <= fromMs) {
      return points;
    }
    List<Map.Entry<Long, Path>> candidates = overlapping(tier, fromMs, toMs);
    for (Map.Entry<Long, Path> candidate : candidates) {
      HistorySegment segment = HistorySegmentCodec.read(candidate.getValue(), ids);
      int fromIndex = (int) Math.max(0L, Math.floorDiv(fromMs - segment.startMs(), tier.intervalMs()));
      int toIndex = (int) Math.min(
          segment.bucketCount(),
          Math.floorDiv(toMs - 1L - segment.startMs(), tier.intervalMs()) + 1L
      );
      for (HistorySeries series : segment.series()) {
        List<HistoryPoint> seriesPoints = points.computeIfAbsent(series.id(), ignored -> new ArrayList<>());
        for (int index = fromIndex; index < toIndex; index++) {
          if (series.count(index) > 0L) {
            seriesPoints.add(series.point(segment.startMs(), tier.intervalMs(), index));
          }
        }
      }
    }
    for (List<HistoryPoint> seriesPoints : points.values()) {
      seriesPoints.sort(Comparator.comparingLong(HistoryPoint::timestampMs));
    }
    return points;
  }

  public void compactAll(long nowMs) throws IOException {
    compact(HistoryTier.RAW, HistoryTier.TEN_SECONDS, nowMs);
    compact(HistoryTier.TEN_SECONDS, HistoryTier.ONE_MINUTE, nowMs);
    compact(HistoryTier.ONE_MINUTE, HistoryTier.FIFTEEN_MINUTES, nowMs);
    compact(HistoryTier.FIFTEEN_MINUTES, HistoryTier.ONE_HOUR, nowMs);
  }

  public int prune(long nowMs, Map<HistoryTier, Long> retentionByTier) throws IOException {
    int removed = 0;
    HistoryTier[] tiers = HistoryTier.values();
    for (int tierIndex = 0; tierIndex < tiers.length - 1; tierIndex++) {
      HistoryTier source = tiers[tierIndex];
      HistoryTier target = tiers[tierIndex + 1];
      long retentionMs = retentionByTier.getOrDefault(source, 0L);
      if (retentionMs <= 0L) {
        continue;
      }
      long cutoff = nowMs - retentionMs;
      List<Map.Entry<Long, Path>> entries = new ArrayList<>(filesByTier.get(source).entrySet());
      for (Map.Entry<Long, Path> entry : entries) {
        long sourceEnd = entry.getKey() + source.segmentDurationMs();
        if (sourceEnd > cutoff) {
          continue;
        }
        long targetStart = target.segmentStart(entry.getKey());
        if (!filesByTier.get(target).containsKey(targetStart)) {
          continue;
        }
        if (Files.deleteIfExists(entry.getValue())) {
          filesByTier.get(source).remove(entry.getKey(), entry.getValue());
          removed++;
        }
      }
    }
    if (removed > 0) {
      rebuildDescriptors();
    }
    return removed;
  }

  public List<MetricDescriptor> descriptors() {
    List<MetricDescriptor> values = new ArrayList<>(descriptors.values());
    values.sort(Comparator.comparing(MetricDescriptor::id));
    return List.copyOf(values);
  }

  public MetricDescriptor descriptor(String id) {
    return descriptors.get(id);
  }

  public void updateLiveDescriptors(MetricSnapshot snapshot) {
    Set<String> active = new HashSet<>();
    for (MetricSnapshotValue value : snapshot.values()) {
      active.add(value.id());
      long timestamp = value.available() ? snapshot.capturedAtMs() : 0L;
      MetricDescriptor replacement = new MetricDescriptor(
          value.id(),
          value.name(),
          value.suffix(),
          timestamp,
          timestamp,
          true
      );
      descriptors.merge(value.id(), replacement, MetricDescriptor::merge);
    }
    for (Map.Entry<String, MetricDescriptor> entry : descriptors.entrySet()) {
      if (active.contains(entry.getKey()) || !entry.getValue().active()) {
        continue;
      }
      MetricDescriptor prior = entry.getValue();
      descriptors.replace(
          entry.getKey(),
          prior,
          new MetricDescriptor(
              prior.id(),
              prior.name(),
              prior.suffix(),
              prior.firstTimestampMs(),
              prior.lastTimestampMs(),
              false
          )
      );
    }
  }

  public long diskBytes() throws IOException {
    long total = Files.exists(walPath()) ? Files.size(walPath()) : 0L;
    for (HistoryTier tier : HistoryTier.values()) {
      for (Path path : filesByTier.get(tier).values()) {
        total += Files.size(path);
      }
    }
    return total;
  }

  private void compact(HistoryTier source, HistoryTier target, long nowMs) throws IOException {
    Collection<Map.Entry<Long, Path>> sourceFiles = new ArrayList<>(filesByTier.get(source).entrySet());
    Set<Long> targetStarts = new HashSet<>();
    for (Map.Entry<Long, Path> sourceFile : sourceFiles) {
      long sourceStart = sourceFile.getKey();
      long targetStart = target.segmentStart(sourceStart);
      if (targetStart + target.segmentDurationMs() > nowMs) {
        continue;
      }
      Path targetFile = filesByTier.get(target).get(targetStart);
      if (targetFile == null
          || Files.getLastModifiedTime(sourceFile.getValue()).compareTo(Files.getLastModifiedTime(targetFile)) > 0) {
        targetStarts.add(targetStart);
      }
    }
    List<Long> orderedStarts = new ArrayList<>(targetStarts);
    orderedStarts.sort(Long::compareTo);
    for (long targetStart : orderedStarts) {
      HistorySegment compacted = compactWindow(source, target, targetStart);
      if (!compacted.series().isEmpty()) {
        write(compacted);
      }
    }
  }

  private HistorySegment compactWindow(HistoryTier source, HistoryTier target, long targetStart) throws IOException {
    int bucketCount = Math.toIntExact(target.segmentDurationMs() / target.intervalMs());
    HistorySegment output = new HistorySegment(target, targetStart, bucketCount);
    long targetEnd = targetStart + target.segmentDurationMs();
    List<Map.Entry<Long, Path>> sources = overlapping(source, targetStart, targetEnd);
    for (Map.Entry<Long, Path> entry : sources) {
      HistorySegment input = HistorySegmentCodec.read(entry.getValue(), null);
      for (HistorySeries inputSeries : input.series()) {
        HistorySeries outputSeries = output.series(
            inputSeries.id(),
            inputSeries.name(),
            inputSeries.suffix()
        );
        for (int sourceIndex = 0; sourceIndex < input.bucketCount(); sourceIndex++) {
          if (inputSeries.count(sourceIndex) <= 0L) {
            continue;
          }
          HistoryPoint point = inputSeries.point(input.startMs(), source.intervalMs(), sourceIndex);
          if (point.timestampMs() < targetStart || point.timestampMs() >= targetEnd) {
            continue;
          }
          int targetIndex = (int) ((point.timestampMs() - targetStart) / target.intervalMs());
          outputSeries.add(targetIndex, point);
        }
      }
    }
    return removeEmptySeries(output);
  }

  private HistorySegment removeEmptySeries(HistorySegment source) {
    HistorySegment filtered = new HistorySegment(source.tier(), source.startMs(), source.bucketCount());
    for (HistorySeries series : source.series()) {
      boolean present = false;
      for (long count : series.countValues()) {
        if (count > 0L) {
          present = true;
          break;
        }
      }
      if (present) {
        filtered.add(series);
      }
    }
    return filtered;
  }

  private List<Map.Entry<Long, Path>> overlapping(HistoryTier tier, long fromMs, long toMs) {
    ConcurrentSkipListMap<Long, Path> files = filesByTier.get(tier);
    List<Map.Entry<Long, Path>> candidates = new ArrayList<>();
    Map.Entry<Long, Path> floor = files.floorEntry(fromMs);
    long firstStart = floor == null ? fromMs : floor.getKey();
    NavigableMap<Long, Path> tail = files.tailMap(firstStart, true);
    for (Map.Entry<Long, Path> entry : tail.entrySet()) {
      if (entry.getKey() >= toMs) {
        break;
      }
      long end = entry.getKey() + tier.segmentDurationMs();
      if (end > fromMs) {
        candidates.add(Map.entry(entry.getKey(), entry.getValue()));
      }
    }
    return candidates;
  }

  private synchronized void rebuildDescriptors() throws IOException {
    Map<String, MetricDescriptor> rebuilt = new LinkedHashMap<>();
    for (HistoryTier tier : HistoryTier.values()) {
      for (Path path : filesByTier.get(tier).values()) {
        try {
          HistorySegmentCodec.SegmentCatalog catalog = HistorySegmentCodec.readCatalog(path);
          for (HistorySegmentCodec.SegmentSeriesDescriptor series : catalog.series()) {
            MetricDescriptor descriptor = new MetricDescriptor(
                series.id(),
                series.name(),
                series.suffix(),
                series.firstTimestampMs(),
                series.lastTimestampMs(),
                false
            );
            rebuilt.merge(series.id(), descriptor, MetricDescriptor::merge);
          }
        } catch (Throwable failure) {
          React.warn("Failed to index React history segment " + path + ": " + failure.getMessage(), failure);
        }
      }
    }
    descriptors.clear();
    descriptors.putAll(rebuilt);
  }

  private void mergeDescriptors(HistorySegment segment, boolean active) {
    for (HistorySeries series : segment.series()) {
      int firstIndex = -1;
      int lastIndex = -1;
      for (int index = 0; index < series.bucketCount(); index++) {
        if (series.count(index) > 0L) {
          if (firstIndex < 0) {
            firstIndex = index;
          }
          lastIndex = index;
        }
      }
      if (firstIndex < 0) {
        continue;
      }
      MetricDescriptor descriptor = new MetricDescriptor(
          series.id(),
          series.name(),
          series.suffix(),
          segment.startMs() + (segment.tier().intervalMs() * firstIndex),
          segment.startMs() + (segment.tier().intervalMs() * lastIndex),
          active
      );
      descriptors.merge(series.id(), descriptor, MetricDescriptor::merge);
    }
  }

  private Path directory(HistoryTier tier) {
    return root.resolve(tier.directory());
  }

  private Path segmentPath(HistoryTier tier, long startMs) {
    return directory(tier).resolve(startMs + ".rht");
  }
}
