package art.arcane.react.web;

import art.arcane.react.api.web.WebAuth;
import art.arcane.react.api.web.WebError;
import art.arcane.react.api.web.pairing.WebToken;
import art.arcane.react.api.web.pairing.WebTokens;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.Clock;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WebAuthTest {

  @TempDir
  File dataFolder;

  private WebTokens tokens;
  private String readBearer;
  private String writeBearer;

  @BeforeEach
  void setUp() throws Exception {
    readBearer = WebTestTokens.issue(dataFolder, "read-device", "viewer");
    writeBearer = WebTestTokens.issue(dataFolder, "write-device", "operator");
    tokens = WebTokens.load(dataFolder, Clock.systemUTC());
  }

  @Test
  void missingAuthorizationHeaderIsUnauthorized() {
    WebAuth auth = new WebAuth(tokens);
    Context ctx = request(HandlerType.GET, null, null);
    assertEquals("unauthorized", assertThrows(WebError.class, () -> auth.handle(ctx)).code());
  }

  @Test
  void optionsPreflightSkipsAuth() {
    WebAuth auth = new WebAuth(tokens);
    Context ctx = mock(Context.class);
    when(ctx.method()).thenReturn(HandlerType.OPTIONS);
    auth.handle(ctx);
    verify(ctx, never()).attribute(eq("token"), any(WebToken.class));
  }

  @Test
  void getWithoutCounterPasses() {
    WebAuth auth = new WebAuth(tokens);
    Context ctx = request(HandlerType.GET, readBearer, null);
    auth.handle(ctx);
    verify(ctx).attribute(eq("token"), any(WebToken.class));
  }

  @Test
  void getIgnoresCounterEvenIfRepeated() {
    WebAuth auth = new WebAuth(tokens);
    Context first = request(HandlerType.GET, readBearer, "5");
    auth.handle(first);
    verify(first).attribute(eq("token"), any(WebToken.class));
    Context second = request(HandlerType.GET, readBearer, "5");
    auth.handle(second);
    verify(second).attribute(eq("token"), any(WebToken.class));
  }

  @Test
  void putWithoutCounterIsConflict() {
    WebAuth auth = new WebAuth(tokens);
    Context ctx = request(HandlerType.PUT, writeBearer, null);
    WebError conflict = assertThrows(WebError.class, () -> auth.handle(ctx));
    assertEquals(409, conflict.status());
    assertEquals("conflict", conflict.code());
  }

  @Test
  void putWithIncreasingCounterPassesReplayIsConflict() {
    WebAuth auth = new WebAuth(tokens);
    Context first = request(HandlerType.PUT, writeBearer, "5");
    auth.handle(first);
    verify(first).attribute(eq("token"), any(WebToken.class));

    WebError conflict = assertThrows(WebError.class, () -> auth.handle(request(HandlerType.PUT, writeBearer, "5")));
    assertEquals(409, conflict.status());
    assertEquals("counter_conflict", conflict.code());
    @SuppressWarnings("unchecked")
    Map<String, Object> error = (Map<String, Object>) conflict.body().get("error");
    assertEquals("counter_conflict", error.get("code"));
    assertEquals(5L, error.get("lastCounter"));

    Context third = request(HandlerType.PUT, writeBearer, "6");
    auth.handle(third);
    verify(third).attribute(eq("token"), any(WebToken.class));
  }

  @Test
  void putWithNonNumericCounterIsBadRequest() {
    WebAuth auth = new WebAuth(tokens);
    WebError invalid = assertThrows(WebError.class, () -> auth.handle(request(HandlerType.PUT, writeBearer, "abc")));
    assertEquals("bad_request", invalid.code());
  }

  @Test
  void readOnlyTokenRejectsExecuteScope() {
    Context ctx = mock(Context.class);
    when(ctx.<WebToken>attribute("token")).thenReturn(tokens.authenticate(readBearer));
    WebError missing = assertThrows(WebError.class, () -> WebAuth.requireScope(ctx, "op:execute"));
    assertEquals(403, missing.status());
    assertEquals("scope_missing", missing.code());
  }

  @Test
  void revokedTokenIsRejectedWithTokenRevoked() throws Exception {
    tokens.revoke(WebTestTokens.idOf(readBearer));
    WebAuth auth = new WebAuth(tokens);
    WebError revoked = assertThrows(WebError.class, () -> auth.handle(request(HandlerType.GET, readBearer, null)));
    assertEquals(401, revoked.status());
    assertEquals("token_revoked", revoked.code());

    WebError unknown = assertThrows(WebError.class, () -> auth.handle(request(HandlerType.GET, "tok_AAAAAAAAAAAAAAAA." + "A".repeat(43), null)));
    assertEquals("unauthorized", unknown.code());
  }

  private static Context request(HandlerType method, String bearer, String counter) {
    Context ctx = mock(Context.class);
    when(ctx.method()).thenReturn(method);
    when(ctx.header("Authorization")).thenReturn(bearer == null ? null : "Bearer " + bearer);
    when(ctx.header("X-React-Counter")).thenReturn(counter);
    return ctx;
  }
}
