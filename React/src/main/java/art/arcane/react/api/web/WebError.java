package art.arcane.react.api.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A refused request with a machine-readable code. Every error body has the shape
 * {@code {"error": {"code": ..., "message": ..., extras...}}}.
 */
public final class WebError extends RuntimeException {
  public static final String CONFIG_FILE = "plugins/React/web.toml";

  private final int status;
  private final String code;
  private final Map<String, Object> extras;

  public WebError(int status, String code, String message) {
    this(status, code, message, Map.of());
  }

  public WebError(int status, String code, String message, Map<String, Object> extras) {
    super(message, null, false, false);
    this.status = status;
    this.code = code;
    this.extras = Map.copyOf(extras);
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public Map<String, Object> body() {
    return body(code, getMessage(), extras);
  }

  public static Map<String, Object> body(String code, String message, Map<String, Object> extras) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("code", code);
    detail.put("message", message);
    detail.putAll(extras);
    return Map.of("error", detail);
  }

  /** The code of a plain HTTP refusal that carries no code of its own. */
  public static String codeFor(int status) {
    return switch (status) {
      case 400 -> "bad_request";
      case 401 -> "unauthorized";
      case 403 -> "forbidden";
      case 404 -> "not_found";
      case 409 -> "conflict";
      case 413 -> "too_large";
      case 429 -> "rate_limited";
      case 503 -> "unavailable";
      default -> status >= 500 ? "internal" : "error";
    };
  }

  public static WebError unauthorized() {
    return new WebError(401, "unauthorized", "A valid React Web token is required.");
  }

  public static WebError tokenRevoked() {
    return new WebError(401, "token_revoked", "This pairing was revoked on the server.");
  }

  public static WebError tokenExpired() {
    return new WebError(401, "token_expired", "This pairing ended after going unused.");
  }

  public static WebError scopeMissing(String scope) {
    return new WebError(403, "scope_missing", "This pairing lacks " + scope + ".", Map.of("scope", scope));
  }

  public static WebError counterConflict(long lastCounter) {
    return new WebError(409, "counter_conflict", "Replayed or non-increasing X-React-Counter", Map.of("lastCounter", lastCounter));
  }

  public static WebError pairingUnknown() {
    return new WebError(401, "pairing_unknown", "The server does not know this pairing link.");
  }

  public static WebError pairingUsed() {
    return new WebError(410, "pairing_used", "This pairing link was already used.");
  }

  public static WebError pairingExpired() {
    return new WebError(410, "pairing_expired", "This pairing link expired.");
  }

  public static WebError pairingRevoked() {
    return new WebError(410, "pairing_revoked", "This pairing link was revoked before it was opened.");
  }

  public static WebError originNotAllowed(String origin) {
    return new WebError(403, "origin_not_allowed", "Origin " + origin + " is not in allowedOrigins in web.toml.",
        Map.of("origin", origin, "configKey", "allowedOrigins", "configFile", CONFIG_FILE));
  }

  public static WebError badRequest(String message) {
    return new WebError(400, "bad_request", message);
  }

  public static WebError unavailable(String message) {
    return new WebError(503, "unavailable", message);
  }
}
