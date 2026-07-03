package art.arcane.react.api.web;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class PairingToken {

  private static final String HMAC_ALGO = "HmacSHA256";

  private final Set<String> scopes;
  private final String role;

  private PairingToken(Set<String> scopes, String role) {
    this.scopes = scopes;
    this.role = role;
  }

  public boolean hasScope(String scope) {
    return scopes.contains(scope);
  }

  public String role() {
    return role;
  }

  public List<String> scopes() {
    List<String> sorted = new ArrayList<>(scopes);
    Collections.sort(sorted);
    return sorted;
  }

  public static String mint(byte[] secret, String id, String label, long issuedAt, Set<String> scopes) {
    byte[] sig = computeHmac(secret, signingInput(id, label, issuedAt, scopes));
    return id + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
  }

  public static Optional<PairingToken> verify(byte[] secret, String bearer, TokenStore store) {
    int dot = bearer.indexOf('.');
    if (dot < 0) {
      return Optional.empty();
    }
    String tokenId = bearer.substring(0, dot);
    String sigPart = bearer.substring(dot + 1);
    Optional<TokenRecord> maybeRecord = store.lookup(tokenId);
    if (maybeRecord.isEmpty()) {
      return Optional.empty();
    }
    TokenRecord rec = maybeRecord.get();
    byte[] expected = computeHmac(secret, signingInput(rec.id(), rec.label(), rec.issuedAt(), rec.scopes()));
    byte[] actual;
    try {
      actual = Base64.getUrlDecoder().decode(sigPart);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    if (!MessageDigest.isEqual(expected, actual)) {
      return Optional.empty();
    }
    String roleId = WebRole.resolveRoleId(rec.role());
    Set<String> effective = WebRole.scopesFor(rec.role());
    return Optional.of(new PairingToken(effective, roleId));
  }

  private static String signingInput(String id, String label, long issuedAt, Set<String> scopes) {
    List<String> sorted = new ArrayList<>(scopes);
    Collections.sort(sorted);
    return id + "|" + label + "|" + issuedAt + "|" + String.join(",", sorted);
  }

  private static byte[] computeHmac(byte[] secret, String input) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGO);
      mac.init(new SecretKeySpec(secret, HMAC_ALGO));
      return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", e);
    }
  }
}
