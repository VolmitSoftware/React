package art.arcane.react.api.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record PairingCode(String directUrl, String relayUrl, String serverPubKey, String fingerprint,
                          String tokenId, String tokenSig) {

  private static final String PREFIX = "RCT2.";

  public static String encode(String directUrl, String relayUrl, String serverPubKey, String fingerprint,
                              String tokenId, String tokenSig) {
    JsonObject payload = new JsonObject();
    payload.addProperty("directUrl", requireNonBlank(directUrl, "directUrl"));
    payload.addProperty("relayUrl", requireNonNull(relayUrl, "relayUrl"));
    payload.addProperty("serverPubKey", requireNonBlank(serverPubKey, "serverPubKey"));
    payload.addProperty("fingerprint", requireNonBlank(fingerprint, "fingerprint"));
    payload.addProperty("tokenId", requireNonBlank(tokenId, "tokenId"));
    payload.addProperty("tokenSig", requireNonBlank(tokenSig, "tokenSig"));
    String encoded = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
    return PREFIX + encoded;
  }

  public static PairingCode decode(String code) {
    if (code == null || !code.startsWith(PREFIX)) {
      throw new IllegalArgumentException("Invalid pairing code prefix");
    }
    try {
      byte[] jsonBytes = Base64.getUrlDecoder().decode(code.substring(PREFIX.length()));
      JsonObject payload = JsonParser.parseString(new String(jsonBytes, StandardCharsets.UTF_8)).getAsJsonObject();
      return new PairingCode(
          requiredString(payload, "directUrl"),
          requiredStringAllowEmpty(payload, "relayUrl"),
          requiredString(payload, "serverPubKey"),
          requiredString(payload, "fingerprint"),
          requiredString(payload, "tokenId"),
          requiredString(payload, "tokenSig")
      );
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid pairing code payload", e);
    }
  }

  private static String requiredString(JsonObject payload, String key) {
    return requireNonBlank(requiredStringAllowEmpty(payload, key), key);
  }

  private static String requiredStringAllowEmpty(JsonObject payload, String key) {
    if (!payload.has(key) || payload.get(key).isJsonNull() || !payload.get(key).isJsonPrimitive()
        || !payload.getAsJsonPrimitive(key).isString()) {
      throw new IllegalArgumentException("Missing pairing field: " + key);
    }
    return payload.get(key).getAsString();
  }

  private static String requireNonBlank(String value, String key) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Pairing field must not be blank: " + key);
    }
    return value;
  }

  private static String requireNonNull(String value, String key) {
    if (value == null) {
      throw new IllegalArgumentException("Pairing field must not be null: " + key);
    }
    return value;
  }
}
