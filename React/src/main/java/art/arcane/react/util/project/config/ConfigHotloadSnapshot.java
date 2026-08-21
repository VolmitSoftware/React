package art.arcane.react.util.project.config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

public record ConfigHotloadSnapshot(
    Path path,
    String rawContent,
    String normalizedContent,
    String digest
) {
  public static ConfigHotloadSnapshot capture(Path source, long maxBytes) throws IOException {
    if (maxBytes < 0L) {
      throw new IllegalArgumentException("Maximum snapshot size cannot be negative");
    }
    Path normalized = source.toAbsolutePath().normalize();
    BasicFileAttributes before = Files.readAttributes(normalized, BasicFileAttributes.class);
    if (!before.isRegularFile()) {
      throw new NoSuchFileException(normalized.toString());
    }
    if (before.size() > maxBytes) {
      throw new IOException("Config file exceeds the hotload limit of " + maxBytes + " bytes: " + normalized);
    }

    byte[] bytes = readBounded(normalized, maxBytes, before.size());
    BasicFileAttributes between = Files.readAttributes(normalized, BasicFileAttributes.class);
    byte[] verificationBytes = readBounded(normalized, maxBytes, between.size());
    BasicFileAttributes after = Files.readAttributes(normalized, BasicFileAttributes.class);
    if (!stableAttributes(before, between)
        || !stableAttributes(between, after)
        || bytes.length != before.size()
        || verificationBytes.length != after.size()
        || !Arrays.equals(bytes, verificationBytes)) {
      throw new IOException("Config file changed while its hotload snapshot was being captured: " + normalized);
    }

    String raw = decodeUtf8(bytes, normalized);
    return new ConfigHotloadSnapshot(
        normalized,
        raw,
        ConfigFileSupport.normalize(raw),
        sha256(bytes)
    );
  }

  private static boolean stableAttributes(BasicFileAttributes before, BasicFileAttributes after) {
    return after.isRegularFile()
        && before.size() == after.size()
        && before.lastModifiedTime().equals(after.lastModifiedTime())
        && Objects.equals(before.fileKey(), after.fileKey());
  }

  private static byte[] readBounded(Path source, long maxBytes, long expectedBytes) throws IOException {
    int initialSize = (int) Math.min(Math.max(32L, expectedBytes), Math.min(maxBytes, 64L * 1024L));
    ByteArrayOutputStream output = new ByteArrayOutputStream(initialSize);
    byte[] buffer = new byte[8 * 1024];
    long total = 0L;
    try (InputStream input = Files.newInputStream(source)) {
      int read;
      while ((read = input.read(buffer)) >= 0) {
        if (read == 0) {
          continue;
        }
        total += read;
        if (total > maxBytes) {
          throw new IOException("Config file exceeds the hotload limit of " + maxBytes + " bytes: " + source);
        }
        output.write(buffer, 0, read);
      }
    }
    return output.toByteArray();
  }

  private static String decodeUtf8(byte[] bytes, Path source) throws IOException {
    try {
      return StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException e) {
      throw new IOException("Config file is not valid UTF-8: " + source, e);
    }
  }

  private static String sha256(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
