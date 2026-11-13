package art.arcane.react.api.web.pairing;

import art.arcane.react.api.web.WebRole;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The code a pairing link carries after {@code /pair#}: {@code RCT3.} and the payload JSON in base64url without padding.
 * It holds everything the pairing card shows before anything is sent, and the one-time secret that redeems it.
 */
public record PairingLink(Optional<String> directUrl, Optional<String> relayUrl, String fingerprint, String serverKey, String tokenId,
                          String secret, Instant expiresAt, String serverName, List<String> scopes, long lifetimeSeconds,
                          Optional<String> label) {
  public static final String PREFIX = "RCT3.";
  public static final String PATH = "/pair#";
  public static final int VERSION = 1, MAXIMUM_CODE_LENGTH = 4096;
  private static final List<String> FIELDS = List.of("v", "directUrl", "relayUrl", "fingerprint", "serverKey", "tokenId", "secret",
      "expiresAt", "serverName", "scopes", "role", "lifetimeSeconds", "label");
  private static final Set<String> DIRECT_SCHEMES = Set.of("http", "https"), RELAY_SCHEMES = Set.of("ws", "wss");

  public PairingLink {
    if (directUrl.isEmpty() && relayUrl.isEmpty()) {
      throw new IllegalArgumentException("$: a pairing link needs directUrl, relayUrl or both");
    }
    directUrl.ifPresent(url -> requireUrl(url, DIRECT_SCHEMES, "$.directUrl"));
    relayUrl.ifPresent(url -> requireUrl(url, RELAY_SCHEMES, "$.relayUrl"));
    byte[] key;
    try {
      key = Base64.getUrlDecoder().decode(serverKey);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("$.serverKey: expected base64url");
    }
    if (!fingerprint.equals(fingerprint(key))) {
      throw new IllegalArgumentException("$.fingerprint: does not match serverKey");
    }
    if (!tokenId.matches("tok_[A-Za-z0-9_-]{16}")) {
      throw new IllegalArgumentException("$.tokenId: expected tok_ and 16 base64url characters");
    }
    if (!secret.matches("[A-Za-z0-9_-]{43}")) {
      throw new IllegalArgumentException("$.secret: expected 32 bytes in base64url without padding");
    }
    if (serverName.isBlank() || serverName.length() > 64) {
      throw new IllegalArgumentException("$.serverName: 1 to 64 characters");
    }
    if (scopes.isEmpty() || !WebRole.SCOPES.containsAll(scopes)) {
      throw new IllegalArgumentException("$.scopes: known scopes only, at least one");
    }
    if (lifetimeSeconds < 60) {
      throw new IllegalArgumentException("$.lifetimeSeconds: at least 60");
    }
    label.ifPresent(text -> {
      if (text.isEmpty() || text.length() > 64) {
        throw new IllegalArgumentException("$.label: 1 to 64 characters");
      }
    });
    scopes = List.copyOf(scopes);
  }

  /** Lowercase hex SHA-256 of the key bytes: the relay's server id. */
  public static String fingerprint(byte[] serverKey) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serverKey));
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException(missing);
    }
  }

  /** {@code viewer}, {@code operator} or {@code admin} when the scopes match one, else empty. */
  public Optional<String> role() {
    String role = WebRole.roleOf(scopes);
    return WebRole.CUSTOM.equals(role) ? Optional.empty() : Optional.of(role);
  }

  /** The address to open: {@code <appUrl>/pair#RCT3.<payload>}. */
  public String url(String appUrl) {
    String base = appUrl;
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base + PATH + encode();
  }

  /** The code with fields in a fixed order, so a decoded code encodes to the same text. */
  public String encode() {
    JsonObject object = new JsonObject();
    object.addProperty("v", VERSION);
    directUrl.ifPresent(url -> object.addProperty("directUrl", url));
    relayUrl.ifPresent(url -> object.addProperty("relayUrl", url));
    object.addProperty("fingerprint", fingerprint);
    object.addProperty("serverKey", serverKey);
    object.addProperty("tokenId", tokenId);
    object.addProperty("secret", secret);
    object.addProperty("expiresAt", expiresAt.toString());
    object.addProperty("serverName", serverName);
    JsonArray list = new JsonArray();
    scopes.forEach(list::add);
    object.add("scopes", list);
    role().ifPresent(role -> object.addProperty("role", role));
    object.addProperty("lifetimeSeconds", lifetimeSeconds);
    label.ifPresent(text -> object.addProperty("label", text));
    return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(object.toString().getBytes(StandardCharsets.UTF_8));
  }

  /** @throws IllegalArgumentException naming the field that is wrong */
  public static PairingLink decode(String code) {
    if (code == null || code.length() > MAXIMUM_CODE_LENGTH || !code.startsWith(PREFIX) || !code.substring(PREFIX.length()).matches("[A-Za-z0-9_-]+")) {
      throw new IllegalArgumentException("$: expected RCT3. followed by base64url without padding");
    }
    JsonObject object;
    try {
      object = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(code.substring(PREFIX.length())), StandardCharsets.UTF_8))
          .getAsJsonObject();
    } catch (RuntimeException invalid) {
      throw new IllegalArgumentException("$: the payload is not one JSON object");
    }
    for (String key : object.keySet()) {
      if (!FIELDS.contains(key)) {
        throw new IllegalArgumentException("$." + key + ": unknown field");
      }
    }
    JsonElement version = object.get("v");
    if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber() || version.getAsInt() != VERSION) {
      throw new IllegalArgumentException("$.v: expected " + VERSION);
    }
    Instant expires;
    try {
      expires = Instant.parse(required(object, "expiresAt"));
    } catch (DateTimeParseException invalid) {
      throw new IllegalArgumentException("$.expiresAt: expected an ISO-8601 instant");
    }
    List<String> scopes = new ArrayList<>();
    JsonElement list = object.get("scopes");
    if (list == null || !list.isJsonArray()) {
      throw new IllegalArgumentException("$.scopes: expected an array");
    }
    list.getAsJsonArray().forEach(scope -> scopes.add(scope.getAsString()));
    JsonElement lifetime = object.get("lifetimeSeconds");
    if (lifetime == null || !lifetime.isJsonPrimitive() || !lifetime.getAsJsonPrimitive().isNumber()) {
      throw new IllegalArgumentException("$.lifetimeSeconds: expected a number");
    }
    return new PairingLink(optional(object, "directUrl"), optional(object, "relayUrl"), required(object, "fingerprint"),
        required(object, "serverKey"), required(object, "tokenId"), required(object, "secret"), expires,
        required(object, "serverName"), scopes, lifetime.getAsLong(), optional(object, "label"));
  }

  private static void requireUrl(String url, Set<String> schemes, String path) {
    try {
      URI uri = new URI(url);
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      if (!schemes.contains(scheme) || uri.getHost() == null || uri.getUserInfo() != null) {
        throw new URISyntaxException(url, "unexpected scheme or host");
      }
    } catch (URISyntaxException invalid) {
      throw new IllegalArgumentException(path + ": expected an absolute " + String.join(" or ", schemes) + " URL");
    }
  }

  private static String required(JsonObject object, String key) {
    return optional(object, key).orElseThrow(() -> new IllegalArgumentException("$." + key + ": expected string"));
  }

  private static Optional<String> optional(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null) {
      return Optional.empty();
    }
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("$." + key + ": expected string");
    }
    return Optional.of(value.getAsString());
  }
}
