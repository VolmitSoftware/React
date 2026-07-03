package art.arcane.react.api.web;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import io.javalin.http.UnauthorizedResponse;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WebAuth implements Handler {

  private static final Set<HandlerType> MUTATING_METHODS = Set.of(
      HandlerType.PUT, HandlerType.POST, HandlerType.PATCH, HandlerType.DELETE);

  private final byte[] secret;
  private final TokenStore store;
  private final ConcurrentHashMap<String, Long> counterMap = new ConcurrentHashMap<>();

  public WebAuth(byte[] secret, TokenStore store) {
    this.secret = secret;
    this.store = store;
  }

  @Override
  public void handle(Context ctx) throws Exception {
    if ("OPTIONS".equals(ctx.method().name())) {
      return;
    }
    String authorization = ctx.header("Authorization");
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new UnauthorizedResponse("Missing or invalid Authorization header");
    }
    String bearer = authorization.substring(7);
    int dot = bearer.indexOf('.');
    if (dot < 0) {
      throw new UnauthorizedResponse("Malformed bearer token");
    }
    String tokenId = bearer.substring(0, dot);

    Optional<PairingToken> maybeToken = PairingToken.verify(secret, bearer, store);
    if (maybeToken.isEmpty()) {
      throw new UnauthorizedResponse("Invalid token");
    }

    HandlerType m = ctx.method();
    if (MUTATING_METHODS.contains(m)) {
      String counterHeader = ctx.header("X-React-Counter");
      if (counterHeader == null) {
        throw new ConflictResponse("Missing X-React-Counter header");
      }
      long counter;
      try {
        counter = Long.parseLong(counterHeader);
      } catch (NumberFormatException e) {
        throw new BadRequestResponse("Invalid X-React-Counter value");
      }
      long finalCounter = counter;
      boolean[] rejected = {false};
      counterMap.compute(tokenId, (id, lastSeen) -> {
        if (lastSeen != null && finalCounter <= lastSeen) {
          rejected[0] = true;
          return lastSeen;
        }
        return finalCounter;
      });
      if (rejected[0]) {
        throw new ConflictResponse("Replayed or non-increasing X-React-Counter");
      }
    }

    ctx.attribute("token", maybeToken.get());
  }

  public static void requireScope(Context ctx, String scope) {
    PairingToken token = ctx.attribute("token");
    if (token == null) {
      throw new UnauthorizedResponse("No authenticated token in context");
    }
    if (!token.hasScope(scope)) {
      throw new ForbiddenResponse("Insufficient scope: " + scope);
    }
  }
}
