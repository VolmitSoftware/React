package art.arcane.react.util.project.config;

import art.arcane.react.React;
import art.arcane.volmlib.util.io.IO;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ConfigMigrationManager {
  private static final Object LOCK = new Object();
  private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
  private static volatile boolean backupAttempted = false;

  private ConfigMigrationManager() {
  }

  public static void backupLegacyJsonConfigsOnce() {
    synchronized (LOCK) {
      if (backupAttempted) {
        return;
      }
      backupAttempted = true;

      File dataFolder = React.instance == null ? null : React.instance.getDataFolder();
      if (dataFolder == null || !dataFolder.exists()) {
        return;
      }

      File marker = React.instance.getDataFile("migrations", ".legacy-json-backed-up");
      if (marker.exists()) {
        return;
      }

      List<File> legacyJson = collectLegacyJsonFiles(dataFolder);
      if (legacyJson.isEmpty()) {
        return;
      }

      boolean migrationNeeded = legacyJson.stream()
          .map(ConfigFileSupport::toTomlFile)
          .anyMatch(file -> file != null && !file.exists());
      if (!migrationNeeded) {
        return;
      }

      File backupDir = React.instance.getDataFolder("migrations", "backups");
      String timestamp = LocalDateTime.now().format(TS);
      File zip = new File(backupDir, timestamp + "-pre-toml-migration.zip");

      try {
        zipLegacyFiles(dataFolder, legacyJson, zip);
        IO.writeAll(marker, "backup=" + zip.getName() + "\ncreated=" + timestamp + "\n");
        React.warn("Created legacy config backup before TOML migration: " + zip.getPath());
      } catch (Throwable e) {
        React.warn("Failed to create legacy config backup zip: " + e.getMessage());
      }
    }
  }

  public static int deleteMigratedLegacyJsonFiles() {
    synchronized (LOCK) {
      File dataFolder = React.instance == null ? null : React.instance.getDataFolder();
      if (dataFolder == null || !dataFolder.exists()) {
        return 0;
      }

      int deleted = 0;
      for (File legacyJson : collectLegacyJsonFiles(dataFolder)) {
        if (legacyJson == null || !legacyJson.exists() || !legacyJson.isFile()) {
          continue;
        }

        File canonicalToml = ConfigFileSupport.toTomlFile(legacyJson);
        if (canonicalToml == null || !canonicalToml.exists() || !canonicalToml.isFile()) {
          continue;
        }

        try {
          if (Files.deleteIfExists(legacyJson.toPath())) {
            deleted++;
          }
        } catch (Throwable e) {
          React.warn("Failed to delete migrated legacy config [" + legacyPath(dataFolder, legacyJson) + "]: " + e.getMessage());
        }
      }

      return deleted;
    }
  }

  public static boolean hasLegacyManagedJsonFiles() {
    synchronized (LOCK) {
      File dataFolder = React.instance == null ? null : React.instance.getDataFolder();
      if (dataFolder == null || !dataFolder.exists()) {
        return false;
      }

      return !collectLegacyJsonFiles(dataFolder).isEmpty();
    }
  }

  private static List<File> collectLegacyJsonFiles(File dataFolder) {
    List<File> files = new ArrayList<>();

    addIfJson(new File(dataFolder, "config.json"), files);
    addScopedJsonFiles(new File(dataFolder, "core"), files);
    addScopedJsonFiles(new File(dataFolder, "feature"), files);
    addScopedJsonFiles(new File(dataFolder, "tweak"), files);
    addScopedJsonFiles(new File(dataFolder, "action"), files);
    addScopedJsonFiles(new File(dataFolder, "sampler"), files);

    return files;
  }

  private static void addIfJson(File file, List<File> out) {
    if (file == null || !file.exists() || !file.isFile()) {
      return;
    }

    if (file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
      out.add(file);
    }
  }

  private static void addScopedJsonFiles(File root, List<File> out) {
    if (root == null || !root.exists() || !root.isDirectory()) {
      return;
    }

    File[] children = root.listFiles();
    if (children == null || children.length == 0) {
      return;
    }

    for (File child : children) {
      if (child == null || !child.isFile()) {
        continue;
      }

      if (child.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
        out.add(child);
      }
    }
  }

  private static void zipLegacyFiles(File dataFolder, List<File> files, File zipFile) throws Exception {
    zipFile.getParentFile().mkdirs();
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile), StandardCharsets.UTF_8)) {
      byte[] buffer = new byte[8192];
      for (File file : files) {
        if (file == null || !file.exists() || !file.isFile()) {
          continue;
        }

        String relative = dataFolder.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
        out.putNextEntry(new ZipEntry(relative));
        try (FileInputStream in = new FileInputStream(file)) {
          int read;
          while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
          }
        }
        out.closeEntry();
      }
    }
  }

  private static String legacyPath(File dataFolder, File file) {
    if (file == null) {
      return "<unknown>";
    }
    try {
      if (dataFolder != null && dataFolder.exists()) {
        return dataFolder.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
      }
    } catch (Throwable ignored) {
    }
    return file.getPath();
  }
}
