package art.arcane.react.api.web;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record PairingCode(String host, int port, String tokenId, String tokenSig, String confirmWord,
                          String relayUrl, String serverPubKey, String fingerprint) {

  public static String encode(String host, int port, String tokenId, String tokenSig, String confirmWord,
                              String relayUrl, String serverPubKey, String fingerprint) {
    String json = "{\"host\":\"" + escapeJson(host) + "\","
        + "\"port\":" + port + ","
        + "\"tokenId\":\"" + escapeJson(tokenId) + "\","
        + "\"tokenSig\":\"" + escapeJson(tokenSig) + "\","
        + "\"confirmWord\":\"" + escapeJson(confirmWord) + "\","
        + "\"relayUrl\":\"" + escapeJson(relayUrl) + "\","
        + "\"serverPubKey\":\"" + escapeJson(serverPubKey) + "\","
        + "\"fingerprint\":\"" + escapeJson(fingerprint) + "\"}";
    String encoded = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    return "RCT1." + encoded;
  }

  public static PairingCode decode(String code) {
    if (!code.startsWith("RCT1.")) {
      throw new IllegalArgumentException("Invalid pairing code prefix");
    }
    String payload = code.substring("RCT1.".length());
    byte[] jsonBytes = Base64.getUrlDecoder().decode(payload);
    String json = new String(jsonBytes, StandardCharsets.UTF_8);
    String host = extractJsonString(json, "host");
    int parsedPort = (int) extractJsonLong(json, "port");
    String tokenId = extractJsonString(json, "tokenId");
    String tokenSig = extractJsonString(json, "tokenSig");
    String confirmWord = extractJsonString(json, "confirmWord");
    String relayUrl = extractJsonStringOrDefault(json, "relayUrl", "");
    String serverPubKey = extractJsonStringOrDefault(json, "serverPubKey", "");
    String fingerprint = extractJsonStringOrDefault(json, "fingerprint", "");
    return new PairingCode(host, parsedPort, tokenId, tokenSig, confirmWord, relayUrl, serverPubKey, fingerprint);
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String extractJsonString(String json, String key) {
    String searchKey = "\"" + key + "\":\"";
    int start = json.indexOf(searchKey);
    if (start < 0) {
      throw new IllegalArgumentException("Key not found in JSON: " + key);
    }
    start += searchKey.length();
    int end = start;
    while (end < json.length()) {
      char c = json.charAt(end);
      if (c == '\\') {
        end += 2;
        continue;
      }
      if (c == '"') {
        break;
      }
      end++;
    }
    return json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
  }

  private static String extractJsonStringOrDefault(String json, String key, String defaultValue) {
    String searchKey = "\"" + key + "\":\"";
    int start = json.indexOf(searchKey);
    if (start < 0) {
      return defaultValue;
    }
    start += searchKey.length();
    int end = start;
    while (end < json.length()) {
      char c = json.charAt(end);
      if (c == '\\') {
        end += 2;
        continue;
      }
      if (c == '"') {
        break;
      }
      end++;
    }
    return json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
  }

  private static long extractJsonLong(String json, String key) {
    String searchKey = "\"" + key + "\":";
    int start = json.indexOf(searchKey);
    if (start < 0) {
      throw new IllegalArgumentException("Key not found in JSON: " + key);
    }
    start += searchKey.length();
    int end = start;
    while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
      end++;
    }
    return Long.parseLong(json.substring(start, end));
  }
}
