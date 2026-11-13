package art.arcane.react.api.web;

import art.arcane.react.util.project.config.TomlCodec;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WebConfigurationSchema {
  private static final long MAX_CONFIGURATION_BYTES = 2L * 1024L * 1024L;

  private WebConfigurationSchema() {
  }

  public static void requireCurrent(File canonicalFile, File obsoleteJsonFile) throws IOException {
    if (obsoleteJsonFile != null && obsoleteJsonFile.exists()) {
      throw obsoleteConfiguration(obsoleteJsonFile, "web.json is no longer supported");
    }
    if (canonicalFile == null || !canonicalFile.exists()) {
      return;
    }
    long size = Files.size(canonicalFile.toPath());
    if (size > MAX_CONFIGURATION_BYTES) {
      throw new IOException("React web configuration is too large (" + size + " bytes): " + canonicalFile.getPath());
    }

    String raw = Files.readString(canonicalFile.toPath(), StandardCharsets.UTF_8);
    JsonElement parsed = TomlCodec.toJsonElement(raw);
    if (!parsed.isJsonObject()) {
      throw obsoleteConfiguration(canonicalFile, "web.toml must contain a root configuration table");
    }
    JsonObject root = parsed.getAsJsonObject();
    if (root.has("enabled") || root.has("bindAddress")) {
      throw obsoleteConfiguration(canonicalFile, "enabled and bindAddress were replaced by listenerEnabled and listenAddress");
    }
    if (!root.has("listenerEnabled") || !root.has("listenAddress")) {
      throw obsoleteConfiguration(canonicalFile, "listenerEnabled and listenAddress are required");
    }
  }

  private static final Pattern CORS_ORIGINS = Pattern.compile("(?m)^corsOrigins\\s*=\\s*\\[[^\\]]*\\]");

  /** Entries dropped while moving {@code corsOrigins} to {@code allowedOrigins}, with why. */
  public record OriginMove(List<String> moved, List<String> dropped) {
  }

  /**
   * Moves an old non-empty {@code corsOrigins} list to {@code allowedOrigins} once, keeping the entries
   * {@code allowedOrigins} accepts. Returns empty when there was nothing to move.
   */
  public static Optional<OriginMove> moveCorsOrigins(File canonicalFile) throws IOException {
    if (canonicalFile == null || !canonicalFile.exists() || Files.size(canonicalFile.toPath()) > MAX_CONFIGURATION_BYTES) {
      return Optional.empty();
    }
    String raw = Files.readString(canonicalFile.toPath(), StandardCharsets.UTF_8);
    JsonElement parsed = TomlCodec.toJsonElement(raw);
    if (!parsed.isJsonObject()) {
      return Optional.empty();
    }
    JsonObject root = parsed.getAsJsonObject();
    if (!root.has("corsOrigins") || root.has("allowedOrigins") || !root.get("corsOrigins").isJsonArray()
        || root.getAsJsonArray("corsOrigins").isEmpty()) {
      return Optional.empty();
    }
    List<String> moved = new ArrayList<>();
    List<String> dropped = new ArrayList<>();
    for (JsonElement entry : root.getAsJsonArray("corsOrigins")) {
      String origin = entry.isJsonPrimitive() ? entry.getAsString() : entry.toString();
      try {
        moved.addAll(WebOrigins.canonical(List.of(origin)));
      } catch (IllegalArgumentException refused) {
        dropped.add(origin);
      }
    }
    Matcher matcher = CORS_ORIGINS.matcher(raw);
    if (!matcher.find()) {
      return Optional.empty();
    }
    StringBuilder list = new StringBuilder("allowedOrigins = [");
    for (int index = 0; index < moved.size(); index++) {
      list.append(index == 0 ? "" : ", ").append('"').append(moved.get(index)).append('"');
    }
    list.append(']');
    String rewritten = raw.substring(0, matcher.start()) + list + raw.substring(matcher.end());
    Files.writeString(canonicalFile.toPath(), rewritten, StandardCharsets.UTF_8);
    return Optional.of(new OriginMove(List.copyOf(moved), List.copyOf(dropped)));
  }

  private static IOException obsoleteConfiguration(File file, String reason) {
    return new IOException(
        "Obsolete React web configuration at [" + file.getPath() + "]: " + reason
            + ". Delete this file; deletion removes its local changes. Restart the server to regenerate "
            + "plugins/React/web.toml with the current schema. This version does not migrate old web configuration."
    );
  }
}
