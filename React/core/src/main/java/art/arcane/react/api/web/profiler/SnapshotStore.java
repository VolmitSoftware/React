package art.arcane.react.api.web.profiler;

import art.arcane.react.api.web.heatmap.HeatmapDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Frozen profiles and heatmaps under {@code snapshots/}: a gzip JSON payload and a small metadata file per snapshot,
 * newest kept up to the retention count. Survives restarts.
 */
public final class SnapshotStore {
  public static final int DEFAULT_RETENTION = 50;
  public static final int MAX_RETENTION = 1000;
  public static final String PROFILE = "profile";
  public static final String HEATMAP = "heatmap";
  public static final int MAX_LABEL = 64;
  private static final Pattern ID = Pattern.compile("[a-z0-9]{1,16}-[0-9a-f]{6}");
  private static final String DATA = ".json.gz";
  private static final String META = ".meta.json";
  private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
  private static final DateTimeFormatter LABEL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

  private final File folder;
  private final ObjectMapper json;
  private final IntSupplier retention;
  private final LongSupplier clock;
  private final ZoneId zone;
  private final SecureRandom random = new SecureRandom();

  public SnapshotStore(File folder, ObjectMapper json, IntSupplier retention, LongSupplier clock, ZoneId zone) {
    this.folder = folder;
    this.json = json;
    this.retention = retention;
    this.clock = clock;
    this.zone = zone;
  }

  public record Meta(String id, String kind, String label, long createdAt, String server, long bytes, String summary) {
  }

  public record Listing(List<Meta> snapshots) {
  }

  /** Writes a snapshot and drops the oldest past the retention count. */
  public synchronized Meta save(String kind, String label, String server, String summary, Object data) throws IOException {
    Files.createDirectories(folder.toPath());
    long createdAt = clock.getAsLong();
    String id = Long.toString(createdAt, 36) + "-" + HexFormat.of().toHexDigits(random.nextInt()).substring(2);
    String cleanLabel = label == null || label.isBlank()
        ? (PROFILE.equals(kind) ? "Profile " : "Heatmap ") + LABEL_TIME.format(Instant.ofEpochMilli(createdAt).atZone(zone))
        : label.strip().substring(0, Math.min(MAX_LABEL, label.strip().length()));
    File dataFile = new File(folder, id + DATA);
    File temporary = new File(folder, id + DATA + ".tmp");
    try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(temporary.toPath()))) {
      json.writeValue(out, data);
    }
    Files.move(temporary.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    Meta meta = new Meta(id, kind, cleanLabel, createdAt, server == null ? "" : server, dataFile.length(), summary == null ? "" : summary);
    json.writeValue(new File(folder, id + META), meta);
    prune();
    return meta;
  }

  /** Newest first; {@code kind} filters when not blank. */
  public synchronized List<Meta> list(String kind) {
    List<Meta> metas = new ArrayList<>();
    File[] files = folder.listFiles((File directory, String name) -> name.endsWith(META));
    if (files == null) {
      return metas;
    }
    for (File file : files) {
      try {
        Meta meta = json.readValue(file, Meta.class);
        if (meta.id() != null && new File(folder, meta.id() + DATA).isFile() && (kind == null || kind.isBlank() || kind.equals(meta.kind()))) {
          metas.add(meta);
        }
      } catch (IOException unreadable) {
        continue;
      }
    }
    metas.sort(Comparator.comparingLong(Meta::createdAt).reversed().thenComparing(Meta::id));
    return metas;
  }

  public synchronized Optional<Meta> meta(String id) {
    if (!valid(id)) {
      return Optional.empty();
    }
    File file = new File(folder, id + META);
    if (!file.isFile() || !new File(folder, id + DATA).isFile()) {
      return Optional.empty();
    }
    try {
      return Optional.of(json.readValue(file, Meta.class));
    } catch (IOException unreadable) {
      return Optional.empty();
    }
  }

  /** {@code {"meta":...,"data":...}} as JSON text. */
  public Optional<byte[]> document(String id) throws IOException {
    Optional<Meta> meta = meta(id);
    if (meta.isEmpty()) {
      return Optional.empty();
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write("{\"meta\":".getBytes(StandardCharsets.UTF_8));
    out.write(json.writeValueAsBytes(meta.get()));
    out.write(",\"data\":".getBytes(StandardCharsets.UTF_8));
    try (InputStream in = new GZIPInputStream(Files.newInputStream(new File(folder, id + DATA).toPath()))) {
      in.transferTo(out);
    }
    out.write('}');
    return Optional.of(out.toByteArray());
  }

  /** The document gzipped, for download. */
  public Optional<byte[]> export(String id) throws IOException {
    Optional<byte[]> document = document(id);
    if (document.isEmpty()) {
      return Optional.empty();
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (OutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write(document.get());
    }
    return Optional.of(out.toByteArray());
  }

  public synchronized boolean delete(String id) throws IOException {
    if (!valid(id)) {
      return false;
    }
    boolean data = Files.deleteIfExists(new File(folder, id + DATA).toPath());
    boolean meta = Files.deleteIfExists(new File(folder, id + META).toPath());
    return data || meta;
  }

  /** {@code react-<kind>-<server>-<yyyyMMdd-HHmmss>.json.gz}, with the server name reduced to safe characters. */
  public String exportName(Meta meta) {
    return exportName(meta, zone);
  }

  static String exportName(Meta meta, ZoneId zone) {
    String server = meta.server().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
    return "react-" + meta.kind() + "-" + (server.isEmpty() ? "server" : server) + "-"
        + FILE_TIME.format(Instant.ofEpochMilli(meta.createdAt()).atZone(zone)) + DATA;
  }

  public static String profileSummary(ProfileDtos.Profile profile) {
    String summary = profile.profiler().samples() + " samples, " + Math.round(profile.profiler().durationMs() / 1000D) + " s";
    Optional<ProfileDtos.OwnerRank> top = profile.owners().owners().stream().findFirst();
    return top.map((ProfileDtos.OwnerRank owner) -> summary + ", " + owner.name() + " " + owner.percent() + "%").orElse(summary);
  }

  public static String heatmapSummary(HeatmapDto heatmap) {
    int cells = heatmap.cells == null ? 0 : heatmap.cells.length;
    return heatmap.label + ", " + heatmap.world + ", radius " + heatmap.radius + ", " + cells + " cells";
  }

  public static int clampRetention(int retention) {
    return Math.max(1, Math.min(MAX_RETENTION, retention));
  }

  /** Drops the oldest snapshots past the retention count. */
  public synchronized void prune() throws IOException {
    List<Meta> metas = list(null);
    int keep = clampRetention(retention.getAsInt());
    for (int index = keep; index < metas.size(); index++) {
      delete(metas.get(index).id());
    }
  }

  static boolean valid(String id) {
    return id != null && ID.matcher(id).matches();
  }
}
