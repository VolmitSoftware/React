package art.arcane.react.api.web;

import art.arcane.react.api.web.network.NetworkProtocol;
import art.arcane.react.api.web.pairing.WebToken;
import art.arcane.react.api.web.pairing.WebTokens;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authenticates {@code Authorization: Bearer <token>} against {@link WebTokens}, which slides the token's expiry, and
 * holds each token's {@code X-React-Counter} high-water mark for mutating requests.
 */
public final class WebAuth implements Handler {

  private static final Set<HandlerType> MUTATING_METHODS = Set.of(
      HandlerType.PUT, HandlerType.POST, HandlerType.PATCH, HandlerType.DELETE);

  private final WebTokens tokens;
  private final ConcurrentHashMap<String, Long> counterMap = new ConcurrentHashMap<>();

  public WebAuth(WebTokens tokens) {
    this.tokens = tokens;
  }

  @Override
  public void handle(Context ctx) {
    if (ctx.method() == HandlerType.OPTIONS) {
      return;
    }
    if (ctx.attribute("token") != null) {
      return;
    }
    ctx.attribute("token", authenticate(ctx, ""));
  }

  /**
   * Checks the bearer and, for a mutating request, its {@code X-React-Counter}. {@code counterScope} keeps a separate
   * high-water mark per scope, as the network routes do for each backend a browser reaches through the proxy.
   */
  public WebToken authenticate(Context ctx, String counterScope) {
    String authorization = ctx.header("Authorization");
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw WebError.unauthorized();
    }
    WebToken token = tokens.authenticate(authorization.substring(7));
    String networkScopes = ctx.header(NetworkProtocol.SCOPES_HEADER);
    if (networkScopes != null && NetworkProtocol.TOKEN_ISSUER.equals(token.issuer())) {
      token = token.restrictedTo(List.of(networkScopes.split(",")));
    }

    if (MUTATING_METHODS.contains(ctx.method())) {
      String counterHeader = ctx.header("X-React-Counter");
      if (counterHeader == null) {
        throw new WebError(409, "conflict", "Missing X-React-Counter header");
      }
      long counter;
      try {
        counter = Long.parseLong(counterHeader);
      } catch (NumberFormatException e) {
        throw WebError.badRequest("Invalid X-React-Counter value");
      }
      long[] rejectedBy = {Long.MIN_VALUE};
      counterMap.compute(token.id() + counterScope, (id, lastSeen) -> {
        if (lastSeen != null && counter <= lastSeen) {
          rejectedBy[0] = lastSeen;
          return lastSeen;
        }
        return counter;
      });
      if (rejectedBy[0] != Long.MIN_VALUE) {
        throw WebError.counterConflict(rejectedBy[0]);
      }
    }
    return token;
  }

  public static void requireScope(Context ctx, String scope) {
    WebToken token = ctx.attribute("token");
    if (token == null) {
      throw WebError.unauthorized();
    }
    if (!token.hasScope(scope)) {
      throw WebError.scopeMissing(scope);
    }
  }
}
