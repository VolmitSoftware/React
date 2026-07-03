package art.arcane.react.api.web;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.Set;

public final class WebSecret {

  private WebSecret() {}

  public static byte[] load(File dataFolder) {
    File keyFile = new File(dataFolder, "web/secret.key");
    if (keyFile.exists()) {
      try {
        return Files.readAllBytes(keyFile.toPath());
      } catch (IOException e) {
        throw new IllegalStateException("Failed to read web secret key", e);
      }
    }
    File parent = keyFile.getParentFile();
    if (!parent.exists() && !parent.mkdirs()) {
      throw new IllegalStateException("Failed to create web secret directory: " + parent.getAbsolutePath());
    }
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    try {
      Files.write(keyFile.toPath(), key);
      try {
        Set<PosixFilePermission> perms = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
        );
        Files.setPosixFilePermissions(keyFile.toPath(), perms);
      } catch (UnsupportedOperationException ignored) {
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write web secret key", e);
    }
    return key;
  }
}
