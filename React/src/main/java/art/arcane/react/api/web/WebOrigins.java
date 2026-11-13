package art.arcane.react.api.web;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The direct listener's origin gate, as the Animus editor's: a browser page from an origin outside {@code allowedOrigins}
 * is refused before any handler runs, and a request without {@code Origin} is not a browser. Unlike Animus, every
 * preflight and the refusal itself carry {@code Access-Control-Allow-Origin}, so the page can read
 * {@code origin_not_allowed} and say which setting to change. The refusal holds no data and runs nothing.
 */
public final class WebOrigins implements Handler {
  public static final int MAXIMUM_ORIGINS = 32;
  private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "localhost", "[::1]", "::1");
  private static final String ALLOW_METHODS = "GET, POST, PUT, PATCH, DELETE, OPTIONS";
  private static final String ALLOW_HEADERS = "Authorization, Content-Type, X-React-Counter, X-React-Challenge";

  private final Supplier<List<String>> allowed;
  private final Consumer<String> refused;

  public WebOrigins(Supplier<List<String>> allowed, Consumer<String> refused) {
    this.allowed = allowed;
    this.refused = refused;
  }

  @Override
  public void handle(Context ctx) {
    String origin = ctx.header("Origin");
    if (origin == null) {
      return;
    }
    ctx.header("Access-Control-Allow-Origin", origin);
    ctx.header("Vary", "Origin");
    ctx.header("Access-Control-Expose-Headers", "Retry-After");
    if (ctx.method() == HandlerType.OPTIONS) {
      ctx.header("Access-Control-Allow-Methods", ALLOW_METHODS);
      ctx.header("Access-Control-Allow-Headers", ALLOW_HEADERS);
      ctx.header("Access-Control-Max-Age", "600");
      if (ctx.header("Access-Control-Request-Private-Network") != null) {
        ctx.header("Access-Control-Allow-Private-Network", "true");
      }
      ctx.status(204);
      ctx.skipRemainingHandlers();
      return;
    }
    if (!allows(allowed.get(), origin)) {
      refused.accept(origin);
      throw WebError.originNotAllowed(origin.length() > 128 ? origin.substring(0, 128) : origin);
    }
  }

  /** True when {@code origin} is listed; a missing origin is not a browser and always passes. */
  public static boolean allows(Collection<String> origins, String origin) {
    return origin == null || origins.contains(origin.toLowerCase(Locale.ROOT));
  }

  /**
   * The configured origins in canonical form ({@code scheme://host[:port]}, lowercase). Throws naming the first entry that
   * has a path, credentials or a non-loopback {@code http://}.
   */
  public static List<String> canonical(Collection<String> origins) {
    if (origins.size() > MAXIMUM_ORIGINS) {
      throw new IllegalArgumentException("allowedOrigins allows at most " + MAXIMUM_ORIGINS + " origins");
    }
    List<String> canonical = new ArrayList<>();
    for (String raw : origins) {
      String origin = raw == null ? "" : raw.trim();
      URI uri;
      try {
        uri = new URI(origin);
      } catch (URISyntaxException invalid) {
        throw new IllegalArgumentException("allowedOrigins entry is not a URL: " + origin);
      }
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      boolean bare = (uri.getRawPath() == null || uri.getRawPath().isEmpty()) && uri.getRawQuery() == null && uri.getRawFragment() == null;
      if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null || uri.getUserInfo() != null || !bare) {
        throw new IllegalArgumentException("allowedOrigins entries are scheme://host[:port] with no path: " + origin);
      }
      if (scheme.equals("http") && !LOOPBACK.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException("allowedOrigins accepts http:// only on loopback: " + origin);
      }
      String value = origin.toLowerCase(Locale.ROOT);
      if (!canonical.contains(value)) {
        canonical.add(value);
      }
    }
    return List.copyOf(canonical);
  }
}
