package art.arcane.react.api.web;

import art.arcane.react.util.project.config.TomlCodec;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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

  private static IOException obsoleteConfiguration(File file, String reason) {
    return new IOException(
        "Obsolete React web configuration at [" + file.getPath() + "]: " + reason
            + ". Delete this file; deletion removes its local changes. Restart the server to regenerate "
            + "plugins/React/web.toml with the current schema. This version does not migrate old web configuration."
    );
  }
}
