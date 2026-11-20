package art.arcane.react.api.web.network;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM under a key derived from the network secret: a sealed text is both private and signed. */
public final class NetworkSeal {
  private static final byte[] CONTEXT = "react-network/1".getBytes(StandardCharsets.UTF_8);
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final SecureRandom RANDOM = new SecureRandom();

  private NetworkSeal() {
  }

  /** A new random secret for {@code networkSecret}. */
  public static String newSecret() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String seal(String secret, String plain) throws GeneralSecurityException {
    byte[] nonce = new byte[NONCE_BYTES];
    RANDOM.nextBytes(nonce);
    Cipher cipher = cipher(Cipher.ENCRYPT_MODE, secret, nonce);
    byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
    byte[] out = new byte[NONCE_BYTES + sealed.length];
    System.arraycopy(nonce, 0, out, 0, NONCE_BYTES);
    System.arraycopy(sealed, 0, out, NONCE_BYTES, sealed.length);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
  }

  /** The plain text, or a {@link GeneralSecurityException} when the text was sealed with another secret or altered. */
  public static String open(String secret, String sealed) throws GeneralSecurityException {
    byte[] bytes;
    try {
      bytes = Base64.getUrlDecoder().decode(sealed);
    } catch (IllegalArgumentException malformed) {
      throw new GeneralSecurityException("Malformed sealed text", malformed);
    }
    if (bytes.length <= NONCE_BYTES) {
      throw new GeneralSecurityException("Sealed text is too short");
    }
    byte[] nonce = new byte[NONCE_BYTES];
    System.arraycopy(bytes, 0, nonce, 0, NONCE_BYTES);
    Cipher cipher = cipher(Cipher.DECRYPT_MODE, secret, nonce);
    return new String(cipher.doFinal(bytes, NONCE_BYTES, bytes.length - NONCE_BYTES), StandardCharsets.UTF_8);
  }

  private static Cipher cipher(int mode, String secret, byte[] nonce) throws GeneralSecurityException {
    if (secret == null || secret.isBlank()) {
      throw new GeneralSecurityException("No network secret");
    }
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update(CONTEXT);
    digest.update((byte) 0);
    byte[] key = digest.digest(secret.strip().getBytes(StandardCharsets.UTF_8));
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
    cipher.updateAAD(CONTEXT);
    return cipher;
  }
}
